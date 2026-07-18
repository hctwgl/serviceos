package com.serviceos.configuration.web;

import com.serviceos.configuration.api.ActivateBundleChannelCommand;
import com.serviceos.configuration.api.BundleChannel;
import com.serviceos.configuration.api.BundleChannelActivationService;
import com.serviceos.configuration.api.BundleChannelActivationView;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bundle 灰度通道激活 / 晋级 / 回滚协议适配。 */
@RestController
@RequestMapping("/api/v1/configuration/bundle-activations")
final class BundleChannelActivationController {
    private final BundleChannelActivationService activations;
    private final CurrentPrincipalProvider principals;

    BundleChannelActivationController(
            BundleChannelActivationService activations,
            CurrentPrincipalProvider principals
    ) {
        this.activations = activations;
        this.principals = principals;
    }

    @GetMapping
    ResponseEntity<List<ActivationResponse>> list(
            @RequestParam UUID projectId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        List<ActivationResponse> body = activations.list(principals.current(), correlationId, projectId)
                .stream().map(BundleChannelActivationController::toResponse).toList();
        return ResponseEntity.ok().header(CorrelationIds.HEADER_NAME, correlationId).body(body);
    }

    @PostMapping
    ResponseEntity<ActivationResponse> activate(
            @RequestBody ActivateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        BundleChannelActivationView view = activations.activate(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                new ActivateBundleChannelCommand(
                        request.projectId(),
                        BundleChannel.valueOf(request.channel()),
                        request.bundleId(),
                        request.approvalRef()));
        return ok(view, correlationId);
    }

    @PostMapping("/{activationId}:promote")
    ResponseEntity<ActivationResponse> promote(
            @PathVariable UUID activationId,
            @RequestBody ApprovalRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        BundleChannelActivationView view = activations.promoteCanary(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                activationId,
                request.approvalRef());
        return ok(view, correlationId);
    }

    @PostMapping("/{activationId}:rollback")
    ResponseEntity<ActivationResponse> rollback(
            @PathVariable UUID activationId,
            @RequestBody ApprovalRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        BundleChannelActivationView view = activations.rollbackStable(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey == null ? correlationId : idempotencyKey),
                activationId,
                request.approvalRef());
        return ok(view, correlationId);
    }

    private static ResponseEntity<ActivationResponse> ok(
            BundleChannelActivationView view,
            String correlationId
    ) {
        return ResponseEntity.ok()
                .eTag(Long.toString(view.aggregateVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(toResponse(view));
    }

    private static ActivationResponse toResponse(BundleChannelActivationView view) {
        return new ActivationResponse(
                view.activationId(), view.projectId(), view.channel().name(), view.bundleId(),
                view.bundleCode(), view.bundleVersion(), view.previousActivationId(), view.status(),
                view.approvalRef(), view.activatedBy(), view.activatedAt(), view.supersededAt(),
                view.aggregateVersion());
    }

    record ActivateRequest(UUID projectId, String channel, UUID bundleId, String approvalRef) {
    }

    record ApprovalRequest(String approvalRef) {
    }

    record ActivationResponse(
            UUID activationId,
            UUID projectId,
            String channel,
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            UUID previousActivationId,
            String status,
            String approvalRef,
            String activatedBy,
            Instant activatedAt,
            Instant supersededAt,
            long aggregateVersion
    ) {
    }
}
