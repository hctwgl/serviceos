package com.serviceos.integration.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.CreateReviewSubmissionCommand;
import com.serviceos.integration.api.DeliveryReplayRequestView;
import com.serviceos.integration.api.OutboundDeliveryService;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.api.QueryRemoteStatusCommand;
import com.serviceos.integration.api.RemoteStatusQueryView;
import com.serviceos.integration.api.RetryOutboundDeliveryCommand;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 内部提审命令与受 scope 保护的 Delivery 摘要查询。 */
@RestController
@RequestMapping("/api/v1")
final class OutboundDeliveryController {
    private final OutboundDeliveryService deliveries;
    private final CurrentPrincipalProvider principals;

    OutboundDeliveryController(OutboundDeliveryService deliveries, CurrentPrincipalProvider principals) {
        this.deliveries = deliveries;
        this.principals = principals;
    }

    @PostMapping("/internal/integration/byd/review-submissions")
    ResponseEntity<OutboundDeliveryView> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody CreateRequest request
    ) {
        OutboundDeliveryView created = deliveries.createReviewSubmission(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new CreateReviewSubmissionCommand(request.sourceReviewCaseId()));
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/outbound-deliveries/{deliveryId}")
    OutboundDeliveryView get(
            @PathVariable UUID deliveryId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return deliveries.get(principals.current(), correlationId, deliveryId);
    }

    @PostMapping("/outbound-deliveries/{deliveryId}:retry")
    ResponseEntity<DeliveryReplayRequestView> retry(
            @PathVariable UUID deliveryId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody RetryRequest request
    ) {
        DeliveryReplayRequestView replay = deliveries.retryUnknown(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new RetryOutboundDeliveryCommand(
                        deliveryId, request.expectedAggregateVersion(),
                        request.reason(), request.approvalRef()));
        return ResponseEntity.accepted().body(replay);
    }

    @PostMapping("/outbound-deliveries/{deliveryId}:query-remote-status")
    ResponseEntity<RemoteStatusQueryView> queryRemoteStatus(
            @PathVariable UUID deliveryId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody QueryRemoteStatusRequest request
    ) {
        RemoteStatusQueryView view = deliveries.queryRemoteStatus(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new QueryRemoteStatusCommand(deliveryId, request.reason()));
        return ResponseEntity.accepted().body(view);
    }

    record CreateRequest(@NotNull UUID sourceReviewCaseId) {
    }

    record RetryRequest(
            @Positive long expectedAggregateVersion,
            @NotBlank @Size(max = 1000) String reason,
            @NotBlank @Size(max = 160) String approvalRef
    ) {
    }

    record QueryRemoteStatusRequest(
            @NotBlank @Size(max = 1000) String reason
    ) {
    }
}
