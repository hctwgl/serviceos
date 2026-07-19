package com.serviceos.integration.application;

import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.spi.OutboundSubmissionConnector;
import com.serviceos.integration.spi.OutboundSubmissionRequest;
import com.serviceos.integration.spi.OutboundTechnicalAcknowledgement;
import com.serviceos.integration.spi.OutboundTransportResult;
import com.serviceos.integration.spi.SignedOutboundRequest;
import com.serviceos.shared.Sha256;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionException;
import com.serviceos.task.spi.TaskExecutionResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 通用出站提审执行管道。
 *
 * <p>事务边界：短事务登记 attempt → 事务外单次网络 → 短事务落不可变结果；
 * 已 DELIVERED 后的本地 Case/Route 落账可安全重试，不得再次 HTTP。</p>
 *
 * <p>UNKNOWN 绝不映射为 RETRYABLE；NOT_SENT 才允许 FAILED_FINAL。</p>
 */
@Service
public class OutboundSubmissionPipeline {
    private static final int MAXIMUM_PAYLOAD_BYTES = 8 * 1024;
    private static final Duration LOCAL_FINALIZATION_RETRY_DELAY = Duration.ofSeconds(30);

    private final OutboundDeliveryRepository deliveries;
    private final OutboundDeliveryCompletionService completion;
    private final ObjectStorageGateway storage;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public OutboundSubmissionPipeline(
            OutboundDeliveryRepository deliveries,
            OutboundDeliveryCompletionService completion,
            ObjectStorageGateway storage,
            TransactionTemplate transactions,
            Clock clock
    ) {
        this.deliveries = deliveries;
        this.completion = completion;
        this.storage = storage;
        this.transactions = transactions;
        this.clock = clock;
    }

    public TaskExecutionResult execute(
            TaskExecutionContext context,
            OutboundSubmissionConnector connector
    ) throws Exception {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(connector, "connector must not be null");

        UUID deliveryId = parseDeliveryId(context);
        OutboundDeliveryRepository.DeliveryRecord record = deliveries.find(context.tenantId(), deliveryId)
                .orElseThrow(() -> TaskExecutionException.finalFailure(
                        "OUTBOUND_DELIVERY_NOT_FOUND", null));
        OutboundDeliveryView delivery = record.view();
        requireTaskIdentity(context, delivery);

        if ("ACKNOWLEDGED".equals(delivery.status())) {
            return TaskExecutionResult.succeeded("outbound-delivery:" + deliveryId);
        }
        if ("DELIVERED".equals(delivery.status())) {
            return finalizeLocally(context, deliveryId);
        }
        if ("REJECTED".equals(delivery.status()) || "FAILED_FINAL".equals(delivery.status())) {
            throw TaskExecutionException.finalFailure("OUTBOUND_SUBMISSION_FINAL_FAILURE", null);
        }
        if (!"PENDING".equals(delivery.status()) && !"UNKNOWN".equals(delivery.status())) {
            throw TaskExecutionException.unknown("OUTBOUND_SUBMISSION_CONCURRENT_ATTEMPT", null);
        }

        Optional<String> preflight = connector.preflightErrorCode();
        if (preflight.isPresent()) {
            String code = preflight.get();
            transactions.executeWithoutResult(status -> deliveries.failBeforeAttempt(
                    context.tenantId(), deliveryId, context.taskId(), code, clock.instant()));
            throw TaskExecutionException.finalFailure(code, null);
        }

        byte[] payload;
        try {
            payload = loadPayload(record);
        } catch (IOException | RuntimeException exception) {
            transactions.executeWithoutResult(status -> deliveries.failBeforeAttempt(
                    context.tenantId(), deliveryId, context.taskId(),
                    "OUTBOUND_DELIVERY_PAYLOAD_UNREADABLE", clock.instant()));
            throw TaskExecutionException.finalFailure("OUTBOUND_DELIVERY_PAYLOAD_UNREADABLE", exception);
        }

        OutboundSubmissionRequest request = new OutboundSubmissionRequest(
                context.tenantId(), deliveryId, context.attemptId(), payload, delivery.payloadDigest());
        SignedOutboundRequest signed;
        try {
            signed = connector.prepare(request);
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> deliveries.failBeforeAttempt(
                    context.tenantId(), deliveryId, context.taskId(),
                    "OUTBOUND_DELIVERY_SIGNING_FAILED", clock.instant()));
            throw TaskExecutionException.finalFailure("OUTBOUND_DELIVERY_SIGNING_FAILED", exception);
        }

        OutboundDeliveryRepository.AttemptStart attempt = transactions.execute(status ->
                deliveries.startAttempt(
                        context.tenantId(), deliveryId, context.taskId(), context.attemptId(),
                        signed.nonce(), signed.requestDate(), delivery.payloadDigest(),
                        signed.credentialVersionId(), clock.instant()));
        if (attempt == null || !attempt.created() || !"SENDING".equals(attempt.status())) {
            throw TaskExecutionException.unknown("OUTBOUND_ATTEMPT_ALREADY_EXISTS", null);
        }

        OutboundTransportResult transport = connector.send(signed);
        if (transport.kind() == OutboundTransportResult.Kind.NOT_SENT) {
            Instant finishedAt = clock.instant();
            transactions.executeWithoutResult(status -> deliveries.recordFailedFinal(
                    context.tenantId(), deliveryId, context.attemptId(), null,
                    null, null, transport.errorCode(), finishedAt));
            throw TaskExecutionException.finalFailure(transport.errorCode(), transport.cause());
        }
        if (transport.kind() == OutboundTransportResult.Kind.UNKNOWN) {
            Instant finishedAt = clock.instant();
            transactions.executeWithoutResult(status -> deliveries.recordUnknown(
                    context.tenantId(), deliveryId, context.attemptId(), null,
                    null, null, transport.errorCode(), finishedAt));
            throw TaskExecutionException.unknown(transport.errorCode(), transport.cause());
        }

        StoredResponse stored;
        try {
            stored = storeResponse(
                    context.tenantId(), deliveryId, context.attemptId(),
                    connector.responseStorageSegment(), transport.body());
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> deliveries.recordUnknown(
                    context.tenantId(), deliveryId, context.attemptId(), transport.httpStatus(),
                    null, Sha256.digest(transport.body()), "OUTBOUND_RESPONSE_STORAGE_FAILED",
                    clock.instant()));
            throw TaskExecutionException.unknown("OUTBOUND_RESPONSE_STORAGE_FAILED", exception);
        }

        OutboundTechnicalAcknowledgement ack = connector.interpret(transport.httpStatus(), transport.body());
        Instant finishedAt = clock.instant();
        return switch (ack.outcome()) {
            case UNKNOWN -> {
                transactions.executeWithoutResult(status -> deliveries.recordUnknown(
                        context.tenantId(), deliveryId, context.attemptId(), transport.httpStatus(),
                        stored.objectRef(), stored.digest(), ack.reasonCode(), finishedAt));
                throw TaskExecutionException.unknown(ack.reasonCode(), null);
            }
            case REJECTED -> {
                transactions.executeWithoutResult(status -> deliveries.recordRejected(
                        context.tenantId(), deliveryId, context.attemptId(), transport.httpStatus(),
                        stored.objectRef(), stored.digest(), ack.reasonCode(),
                        ack.acknowledgementReasonCode(), finishedAt));
                throw TaskExecutionException.finalFailure(ack.reasonCode(), null);
            }
            case ACCEPTED -> {
                transactions.executeWithoutResult(status -> deliveries.recordDelivered(
                        context.tenantId(), deliveryId, context.attemptId(), transport.httpStatus(),
                        stored.objectRef(), stored.digest(), ack.reasonCode(),
                        ack.acknowledgementReasonCode(), finishedAt));
                yield finalizeLocally(context, deliveryId);
            }
        };
    }

    private TaskExecutionResult finalizeLocally(TaskExecutionContext context, UUID deliveryId)
            throws TaskExecutionException {
        try {
            completion.finalizeDelivered(
                    context.tenantId(), deliveryId, context.taskId(), context.correlationId());
            return TaskExecutionResult.succeeded("outbound-delivery:" + deliveryId);
        } catch (RuntimeException exception) {
            // 外部已明确接受，后续重试只执行本地幂等 Case/Route 落账，不会再次发 HTTP。
            throw TaskExecutionException.retryable(
                    "OUTBOUND_LOCAL_FINALIZATION_PENDING",
                    clock.instant().plus(LOCAL_FINALIZATION_RETRY_DELAY), exception);
        }
    }

    private byte[] loadPayload(OutboundDeliveryRepository.DeliveryRecord delivery) throws IOException {
        ObjectMetadata metadata = storage.inspect(delivery.payloadObjectRef());
        if (metadata.size() <= 0 || metadata.size() > MAXIMUM_PAYLOAD_BYTES
                || !delivery.view().payloadDigest().equals(metadata.checksumSha256())) {
            throw new IOException("outbound payload metadata mismatch");
        }
        try (InputStream input = storage.openForScan(delivery.payloadObjectRef())) {
            byte[] content = input.readNBytes(MAXIMUM_PAYLOAD_BYTES + 1);
            if (content.length != metadata.size()
                    || !delivery.view().payloadDigest().equals(Sha256.digest(content))) {
                throw new IOException("outbound payload content mismatch");
            }
            return content;
        }
    }

    private StoredResponse storeResponse(
            String tenantId,
            UUID deliveryId,
            UUID attemptId,
            String storageSegment,
            byte[] body
    ) {
        String digest = Sha256.digest(body);
        String tenantPrefix = Sha256.digest(tenantId).substring(0, 16);
        String objectRef = "integration/outbound/" + tenantPrefix + "/" + storageSegment + "/"
                + deliveryId + "/responses/" + attemptId + "-" + digest + ".json";
        try {
            storage.storeInternal(
                    objectRef, new ByteArrayInputStream(body), body.length, digest, "application/json");
            return new StoredResponse(objectRef, digest);
        } catch (IOException exception) {
            throw new IllegalStateException("Private outbound response storage failed", exception);
        }
    }

    private static UUID parseDeliveryId(TaskExecutionContext context) throws TaskExecutionException {
        String expectedPayloadRef = "outbound-delivery:" + context.businessKey();
        if (!expectedPayloadRef.equals(context.payloadRef())) {
            throw TaskExecutionException.finalFailure("OUTBOUND_DELIVERY_TASK_IDENTITY_MISMATCH", null);
        }
        try {
            int replaySeparator = context.businessKey().indexOf(":replay:");
            String deliveryId = replaySeparator < 0
                    ? context.businessKey()
                    : context.businessKey().substring(0, replaySeparator);
            return UUID.fromString(deliveryId);
        } catch (IllegalArgumentException exception) {
            throw TaskExecutionException.finalFailure("OUTBOUND_DELIVERY_ID_INVALID", exception);
        }
    }

    private void requireTaskIdentity(TaskExecutionContext context, OutboundDeliveryView delivery)
            throws TaskExecutionException {
        if (!deliveries.isAuthorizedExecutionTask(
                    context.tenantId(), delivery.deliveryId(), context.taskId())
                || !context.payloadDigest().equals(delivery.payloadDigest())) {
            throw TaskExecutionException.finalFailure("OUTBOUND_DELIVERY_TASK_IDENTITY_MISMATCH", null);
        }
    }

    private record StoredResponse(String objectRef, String digest) {
    }
}
