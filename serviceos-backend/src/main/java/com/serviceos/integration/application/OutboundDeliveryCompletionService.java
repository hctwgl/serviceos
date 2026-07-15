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
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 已收到 BYD errno=0 后，只执行本地幂等落账，不再次调用外部接口。 */
@Service
public class OutboundDeliveryCompletionService {
    private static final String AUTH_POLICY = "BYD_CPIM_SUBMIT_REVIEW_V7_3_1";

    private final OutboundDeliveryRepository deliveries;
    private final ReviewCaseService reviews;
    private final ExternalReviewRouteService routes;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String adapterPrincipalId;

    public OutboundDeliveryCompletionService(
            OutboundDeliveryRepository deliveries,
            ReviewCaseService reviews,
            ExternalReviewRouteService routes,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${serviceos.integration.byd.cpim.adapter-principal-id}") String adapterPrincipalId
    ) {
        this.deliveries = deliveries;
        this.reviews = reviews;
        this.routes = routes;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.adapterPrincipalId = requireText(adapterPrincipalId, "adapterPrincipalId");
    }

    @Transactional
    public OutboundDeliveryView finalizeDelivered(String tenantId, UUID deliveryId, String correlationId) {
        OutboundDeliveryRepository.DeliveryRecord record = deliveries.find(tenantId, deliveryId)
                .orElseThrow(() -> new IllegalStateException("Delivered OutboundDelivery disappeared"));
        OutboundDeliveryView delivery = record.view();
        if ("ACKNOWLEDGED".equals(delivery.status())) {
            return delivery;
        }
        if (!"DELIVERED".equals(delivery.status())) {
            throw new IllegalStateException("Only DELIVERED OutboundDelivery can be finalized");
        }

        CurrentPrincipal adapter = new CurrentPrincipal(
                adapterPrincipalId, tenantId, CurrentPrincipal.PrincipalType.SERVICE,
                "byd-cpim-adapter", Set.of());
        String submissionRef = "BYD:SUBMIT_REVIEW:" + delivery.deliveryId();
        String callbackBatchRef = "BYD:REVIEW_CALLBACK:" + delivery.deliveryId();
        ReviewCaseView clientCase = reviews.createClient(
                adapter, new CommandMetadata(correlationId, "delivery-client:" + delivery.deliveryId()),
                new CreateClientReviewCaseCommand(
                        delivery.sourceReviewCaseId(), submissionRef, callbackBatchRef,
                        DefaultOutboundDeliveryService.CALLBACK_MAPPING_VERSION,
                        DefaultOutboundDeliveryService.CLIENT_POLICY));
        ExternalReviewRouteView route = routes.register(
                adapter, new CommandMetadata(correlationId, "delivery-route:" + delivery.deliveryId()),
                new RegisterExternalReviewRouteCommand(
                        delivery.externalOrderCode(), clientCase.reviewCaseId(), submissionRef,
                        callbackBatchRef, DefaultOutboundDeliveryService.CALLBACK_MAPPING_VERSION));
        Instant now = clock.instant();
        deliveries.acknowledge(tenantId, delivery.deliveryId(), clientCase.reviewCaseId(), route.reviewRouteId(), now);
        OutboundDeliveryView acknowledged = deliveries.find(tenantId, delivery.deliveryId()).orElseThrow().view();

        String payload = json(new DeliveryAcknowledgedPayload(
                acknowledged.deliveryId(), acknowledged.projectId(), acknowledged.sourceReviewCaseId(),
                clientCase.reviewCaseId(), route.reviewRouteId(), acknowledged.externalOrderCode(),
                acknowledged.payloadDigest(), acknowledged.mappingVersionId(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "integration",
                "integration.outbound-delivery-acknowledged", 1,
                "OutboundDelivery", acknowledged.deliveryId().toString(), acknowledged.aggregateVersion(),
                tenantId, correlationId, acknowledged.executionTaskId().toString(),
                acknowledged.sourceReviewCaseId().toString(), payload, Sha256.digest(payload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, adapterPrincipalId,
                "OUTBOUND_REVIEW_SUBMISSION_ACKNOWLEDGED", AUTH_POLICY,
                "OutboundDelivery", acknowledged.deliveryId().toString(), "ALLOW",
                List.of(), AUTH_POLICY, "ACKNOWLEDGED", null,
                Sha256.digest(acknowledged.payloadDigest() + "|" + clientCase.reviewCaseId()),
                correlationId, now));
        return acknowledged;
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
}
