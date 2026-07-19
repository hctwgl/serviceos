package com.serviceos.integration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.evidence.api.CreateClientReviewCaseCommand;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.ExternalReviewRouteService;
import com.serviceos.integration.api.ExternalReviewRouteView;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.api.RegisterExternalReviewRouteCommand;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 技术接受（DELIVERED）后的本地幂等落账：创建 CLIENT ReviewCase 与回调路由。
 *
 * <p>不再次调用外部接口；mapping/clientPolicy 由出站 Profile 解析，禁止本类硬编码车企常量。</p>
 */
@Service
public class OutboundDeliveryCompletionService {
    private final OutboundDeliveryRepository deliveries;
    private final ReviewCaseService reviews;
    private final ExternalReviewRouteService routes;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String adapterPrincipalId;
    private final OutboundReviewSubmissionProfiles profiles;

    public OutboundDeliveryCompletionService(
            OutboundDeliveryRepository deliveries,
            ReviewCaseService reviews,
            ExternalReviewRouteService routes,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${serviceos.integration.byd.cpim.adapter-principal-id}") String adapterPrincipalId,
            OutboundReviewSubmissionProfiles profiles
    ) {
        this.deliveries = deliveries;
        this.reviews = reviews;
        this.routes = routes;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.adapterPrincipalId = requireText(adapterPrincipalId, "adapterPrincipalId");
        this.profiles = profiles;
    }

    @Transactional
    public OutboundDeliveryView finalizeDelivered(
            String tenantId,
            UUID deliveryId,
            UUID successfulExecutionTaskId,
            String correlationId
    ) {
        Objects.requireNonNull(successfulExecutionTaskId, "successfulExecutionTaskId must not be null");
        OutboundDeliveryRepository.DeliveryRecord record = deliveries.find(tenantId, deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivered OutboundDelivery disappeared"));
        OutboundDeliveryView delivery = record.view();
        if ("ACKNOWLEDGED".equals(delivery.status())) {
            return delivery;
        }
        if (!"DELIVERED".equals(delivery.status())) {
            throw new IllegalStateException("Only DELIVERED OutboundDelivery can be finalized");
        }

        var profile = profiles.requireByConnectorVersion(delivery.connectorVersionId());
        CurrentPrincipal adapter = new CurrentPrincipal(
                adapterPrincipalId, tenantId, CurrentPrincipal.PrincipalType.SERVICE,
                "outbound-submission-adapter", Set.of());
        String submissionRef = profile.clientSubmissionRef(delivery.deliveryId());
        String callbackBatchRef = profile.callbackBatchRef(delivery.deliveryId());
        ReviewCaseView clientCase = reviews.createClient(
                adapter, new CommandMetadata(correlationId, "delivery-client:" + delivery.deliveryId()),
                new CreateClientReviewCaseCommand(
                        delivery.sourceReviewCaseId(), submissionRef, callbackBatchRef,
                        profile.callbackMappingVersion(),
                        profile.clientPolicy()));
        ExternalReviewRouteView route = routes.register(
                adapter, new CommandMetadata(correlationId, "delivery-route:" + delivery.deliveryId()),
                new RegisterExternalReviewRouteCommand(
                        delivery.externalOrderCode(), clientCase.reviewCaseId(), submissionRef,
                        callbackBatchRef, profile.callbackMappingVersion()));
        Instant now = clock.instant();
        deliveries.acknowledge(tenantId, delivery.deliveryId(), clientCase.reviewCaseId(), route.reviewRouteId(), now);
        OutboundDeliveryView acknowledged = deliveries.find(tenantId, delivery.deliveryId()).orElseThrow().view();

        String payload = json(new DeliveryAcknowledgedPayload(
                acknowledged.deliveryId(), acknowledged.projectId(), acknowledged.sourceReviewCaseId(),
                clientCase.reviewCaseId(), route.reviewRouteId(), acknowledged.externalOrderCode(),
                acknowledged.payloadDigest(), acknowledged.mappingVersionId(), now));
        UUID acknowledgedEventId = UUID.randomUUID();
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), acknowledgedEventId, "integration",
                "integration.outbound-delivery-acknowledged", 1,
                "OutboundDelivery", acknowledged.deliveryId().toString(), acknowledged.aggregateVersion(),
                tenantId, correlationId, successfulExecutionTaskId.toString(),
                acknowledged.sourceReviewCaseId().toString(), payload, Sha256.digest(payload), now));
        appendRecoveryEventIfRequired(
                tenantId, acknowledged, successfulExecutionTaskId,
                acknowledgedEventId, correlationId, now);
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, adapterPrincipalId,
                "OUTBOUND_REVIEW_SUBMISSION_ACKNOWLEDGED", "OUTBOUND_SUBMIT_REVIEW_TECHNICAL_ACK",
                "OutboundDelivery", acknowledged.deliveryId().toString(), "ALLOW",
                List.of(), "OUTBOUND_SUBMIT_REVIEW_TECHNICAL_ACK", "ACKNOWLEDGED", null,
                Sha256.digest(acknowledged.payloadDigest() + "|" + clientCase.reviewCaseId()),
                correlationId, now));
        return acknowledged;
    }

    private void appendRecoveryEventIfRequired(
            String tenantId,
            OutboundDeliveryView acknowledged,
            UUID successfulExecutionTaskId,
            UUID acknowledgedEventId,
            String correlationId,
            Instant acknowledgedAt
    ) {
        if (acknowledged.replayRequests().isEmpty()) {
            return;
        }
        // 同一 Delivery 的 UNKNOWN 历史可能跨越多个重发 Task；明确 ACK 后它们都已失去人工处理必要性。
        LinkedHashSet<UUID> recoveredTaskIds = new LinkedHashSet<>();
        recoveredTaskIds.add(acknowledged.executionTaskId());
        acknowledged.replayRequests().forEach(replay -> recoveredTaskIds.add(replay.executionTaskId()));
        if (!recoveredTaskIds.contains(successfulExecutionTaskId)) {
            throw new IllegalStateException("Successful replay Task is not bound to OutboundDelivery");
        }
        String recoveryPayload = json(new DeliveryRecoveredPayload(
                acknowledged.deliveryId(), successfulExecutionTaskId,
                List.copyOf(recoveredTaskIds), acknowledgedAt));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "integration",
                "integration.outbound-delivery-recovered", 1,
                "OutboundDelivery", acknowledged.deliveryId().toString(), acknowledged.aggregateVersion(),
                tenantId, correlationId, acknowledgedEventId.toString(),
                acknowledged.deliveryId().toString(), recoveryPayload,
                Sha256.digest(recoveryPayload), acknowledgedAt));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("OutboundDelivery event serialization failed", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record DeliveryAcknowledgedPayload(
            UUID deliveryId,
            UUID projectId,
            UUID sourceReviewCaseId,
            UUID clientReviewCaseId,
            UUID reviewRouteId,
            String externalOrderCode,
            String payloadDigest,
            String mappingVersionId,
            Instant acknowledgedAt
    ) {
    }

    private record DeliveryRecoveredPayload(
            UUID deliveryId,
            UUID successfulExecutionTaskId,
            List<UUID> recoveredTaskIds,
            Instant acknowledgedAt
    ) {
    }
}
