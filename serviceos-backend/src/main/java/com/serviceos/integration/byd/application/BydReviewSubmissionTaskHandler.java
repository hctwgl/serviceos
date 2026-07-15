package com.serviceos.integration.byd.application;

import com.serviceos.files.spi.ObjectMetadata;
import com.serviceos.files.spi.ObjectStorageGateway;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.application.OutboundDeliveryCompletionService;
import com.serviceos.integration.application.OutboundDeliveryRepository;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.spi.BydCpimSubmitReviewGateway;
import com.serviceos.shared.Sha256;
import com.serviceos.task.spi.AutomatedTaskHandler;
import com.serviceos.task.spi.TaskExecutionContext;
import com.serviceos.task.spi.TaskExecutionException;
import com.serviceos.task.spi.TaskExecutionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BYD 提审执行器：短事务登记 attempt，事务外只调用一次网络，再以短事务保存不可变结果。
 *
 * <p>UNKNOWN 绝不抛 RETRYABLE；只有已经持久化 errno=0 后的本地 Case/Route 落账可以安全重试。</p>
 */
@Service
final class BydReviewSubmissionTaskHandler implements AutomatedTaskHandler {
    private static final String TASK_TYPE = "integration.byd.submit-review";
    private static final int MAXIMUM_PAYLOAD_BYTES = 8 * 1024;
    private static final Duration LOCAL_FINALIZATION_RETRY_DELAY = Duration.ofSeconds(30);
    private static final Set<String> RESPONSE_FIELDS = Set.of("errno", "errmsg", "data");

    private final OutboundDeliveryRepository deliveries;
    private final OutboundDeliveryCompletionService completion;
    private final BydCpimSubmitReviewGateway gateway;
    private final ObjectStorageGateway storage;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final ZoneId protocolZone;
    private final String appKey;
    private final BydCpimSignatureVerifier signer;
    private final String credentialVersionId;

    BydReviewSubmissionTaskHandler(
            OutboundDeliveryRepository deliveries,
            OutboundDeliveryCompletionService completion,
            BydCpimSubmitReviewGateway gateway,
            ObjectStorageGateway storage,
            ObjectMapper objectMapper,
            TransactionTemplate transactions,
            Clock clock,
            @Value("${serviceos.integration.byd.cpim.zone-id}") ZoneId protocolZone,
            @Value("${serviceos.integration.byd.cpim.app-key}") String appKey,
            @Value("${serviceos.integration.byd.cpim.app-secret}") String appSecret,
            @Value("${serviceos.integration.byd.cpim.credential-version-id:}") String credentialVersionId
    ) {
        this.deliveries = deliveries;
        this.completion = completion;
        this.gateway = gateway;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.transactions = transactions;
        this.clock = clock;
        this.protocolZone = protocolZone;
        this.appKey = requireText(appKey, "appKey");
        this.signer = new BydCpimSignatureVerifier(appKey, appSecret, clock, protocolZone);
        this.credentialVersionId = credentialVersionId == null ? "" : credentialVersionId.trim();
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public TaskExecutionResult execute(TaskExecutionContext context) throws Exception {
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
            throw TaskExecutionException.finalFailure("BYD_SUBMISSION_FINAL_FAILURE", null);
        }
        if (!"PENDING".equals(delivery.status()) && !"UNKNOWN".equals(delivery.status())) {
            throw TaskExecutionException.unknown("BYD_SUBMISSION_CONCURRENT_ATTEMPT", null);
        }
        if (credentialVersionId.isBlank()) {
            transactions.executeWithoutResult(status -> deliveries.failBeforeAttempt(
                    context.tenantId(), deliveryId, context.taskId(),
                    "BYD_CREDENTIAL_VERSION_NOT_CONFIGURED", clock.instant()));
            throw TaskExecutionException.finalFailure("BYD_CREDENTIAL_VERSION_NOT_CONFIGURED", null);
        }

        byte[] payload;
        Map<String, Object> parameters;
        try {
            payload = loadPayload(record);
            parameters = objectMapper.readValue(payload, new TypeReference<>() { });
        } catch (IOException | RuntimeException exception) {
            transactions.executeWithoutResult(status -> deliveries.failBeforeAttempt(
                    context.tenantId(), deliveryId, context.taskId(),
                    "BYD_DELIVERY_PAYLOAD_UNREADABLE", clock.instant()));
            throw TaskExecutionException.finalFailure("BYD_DELIVERY_PAYLOAD_UNREADABLE", exception);
        }

        LocalDate requestDate = LocalDate.now(clock.withZone(protocolZone));
        String nonce = context.attemptId().toString();
        String signature;
        try {
            signature = signer.sign(nonce, requestDate, parameters);
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> deliveries.failBeforeAttempt(
                    context.tenantId(), deliveryId, context.taskId(),
                    "BYD_DELIVERY_SIGNING_FAILED", clock.instant()));
            throw TaskExecutionException.finalFailure("BYD_DELIVERY_SIGNING_FAILED", exception);
        }
        OutboundDeliveryRepository.AttemptStart attempt = transactions.execute(status ->
                deliveries.startAttempt(
                        context.tenantId(), deliveryId, context.taskId(), context.attemptId(),
                        nonce, requestDate, delivery.payloadDigest(), credentialVersionId, clock.instant()));
        if (attempt == null || !attempt.created() || !"SENDING".equals(attempt.status())) {
            throw TaskExecutionException.unknown("BYD_ATTEMPT_ALREADY_EXISTS", null);
        }

        BydCpimSubmitReviewGateway.Response response;
        try {
            response = gateway.send(new BydCpimSubmitReviewGateway.Request(
                    appKey, nonce, requestDate.toString(), signature, payload));
        } catch (BydCpimSubmitReviewGateway.TransportException exception) {
            Instant finishedAt = clock.instant();
            if (exception.kind() == BydCpimSubmitReviewGateway.TransportException.Kind.NOT_SENT) {
                transactions.executeWithoutResult(status -> deliveries.recordFailedFinal(
                        context.tenantId(), deliveryId, context.attemptId(), null,
                        null, null, exception.errorCode(), finishedAt));
                throw TaskExecutionException.finalFailure(exception.errorCode(), exception);
            }
            transactions.executeWithoutResult(status -> deliveries.recordUnknown(
                    context.tenantId(), deliveryId, context.attemptId(), null,
                    null, null, exception.errorCode(), finishedAt));
            throw TaskExecutionException.unknown(exception.errorCode(), exception);
        }

        StoredResponse stored;
        try {
            stored = storeResponse(context.tenantId(), deliveryId, context.attemptId(), response.body());
        } catch (RuntimeException exception) {
            transactions.executeWithoutResult(status -> deliveries.recordUnknown(
                    context.tenantId(), deliveryId, context.attemptId(), response.httpStatus(),
                    null, Sha256.digest(response.body()), "BYD_RESPONSE_STORAGE_FAILED", clock.instant()));
            throw TaskExecutionException.unknown("BYD_RESPONSE_STORAGE_FAILED", exception);
        }
        if (response.httpStatus() < 200 || response.httpStatus() >= 300) {
            transactions.executeWithoutResult(status -> deliveries.recordUnknown(
                    context.tenantId(), deliveryId, context.attemptId(), response.httpStatus(),
                    stored.objectRef(), stored.digest(), "BYD_HTTP_RESULT_UNKNOWN", clock.instant()));
            throw TaskExecutionException.unknown("BYD_HTTP_RESULT_UNKNOWN", null);
        }

        NormalizedResponse normalized;
        try {
            normalized = normalize(response.body());
        } catch (IllegalArgumentException exception) {
            transactions.executeWithoutResult(status -> deliveries.recordUnknown(
                    context.tenantId(), deliveryId, context.attemptId(), response.httpStatus(),
                    stored.objectRef(), stored.digest(), "BYD_PROTOCOL_RESULT_UNKNOWN", clock.instant()));
            throw TaskExecutionException.unknown("BYD_PROTOCOL_RESULT_UNKNOWN", exception);
        }
        Instant finishedAt = clock.instant();
        if (normalized.errno() != 0) {
            String reason = "BYD_ERRNO_" + normalized.errno();
            transactions.executeWithoutResult(status -> deliveries.recordRejected(
                    context.tenantId(), deliveryId, context.attemptId(), response.httpStatus(),
                    stored.objectRef(), stored.digest(), reason, reason, finishedAt));
            throw TaskExecutionException.finalFailure(reason, null);
        }
        transactions.executeWithoutResult(status -> deliveries.recordDelivered(
                context.tenantId(), deliveryId, context.attemptId(), response.httpStatus(),
                stored.objectRef(), stored.digest(), "BYD_ERRNO_0", "BYD_ACCEPTED", finishedAt));
        return finalizeLocally(context, deliveryId);
    }

    private TaskExecutionResult finalizeLocally(TaskExecutionContext context, UUID deliveryId)
            throws TaskExecutionException {
        try {
            completion.finalizeDelivered(context.tenantId(), deliveryId, context.correlationId());
            return TaskExecutionResult.succeeded("outbound-delivery:" + deliveryId);
        } catch (RuntimeException exception) {
            // 外部已明确接受，后续重试只执行本地幂等 Case/Route 落账，不会再次发 HTTP。
            throw TaskExecutionException.retryable(
                    "BYD_LOCAL_FINALIZATION_PENDING",
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

    private StoredResponse storeResponse(String tenantId, UUID deliveryId, UUID attemptId, byte[] body) {
        String digest = Sha256.digest(body);
        String tenantPrefix = Sha256.digest(tenantId).substring(0, 16);
        String objectRef = "integration/outbound/" + tenantPrefix + "/byd-cpim/submit-review/"
                + deliveryId + "/responses/" + attemptId + "-" + digest + ".json";
        try {
            storage.storeInternal(
                    objectRef, new ByteArrayInputStream(body), body.length, digest, "application/json");
            return new StoredResponse(objectRef, digest);
        } catch (IOException exception) {
            throw new IllegalStateException("Private BYD response storage failed", exception);
        }
    }

    private NormalizedResponse normalize(byte[] body) {
        final Map<String, Object> response;
        try {
            response = objectMapper.readValue(body, new TypeReference<>() { });
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("BYD response is not valid JSON", exception);
        }
        if (!RESPONSE_FIELDS.equals(response.keySet())) {
            throw new IllegalArgumentException("BYD response fields are invalid");
        }
        Object errnoValue = response.get("errno");
        Object errmsgValue = response.get("errmsg");
        if (!(errnoValue instanceof Number number)
                || number.doubleValue() != number.intValue()
                || !(errmsgValue instanceof String errmsg)
                || errmsg.isBlank() || !errmsg.equals(errmsg.trim()) || errmsg.length() > 500
                || response.get("data") != null) {
            throw new IllegalArgumentException("BYD response value types are invalid");
        }
        return new NormalizedResponse(number.intValue());
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

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record StoredResponse(String objectRef, String digest) {
    }

    private record NormalizedResponse(int errno) {
    }
}
