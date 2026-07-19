package com.serviceos.dispatch.web;

import com.serviceos.dispatch.api.ManualAssignServiceAssignmentCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentReceipt;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin 人工初派 HTTP 适配器。
 * <p>
 * 只信任 JWT 映射主体与 Idempotency-Key；不接受客户端 tenant/actor 头。
 */
@RestController
@RequestMapping("/api/v1/tasks")
final class ManualServiceAssignmentController {
    private final ManualServiceAssignmentService manualAssignments;
    private final CurrentPrincipalProvider principals;

    ManualServiceAssignmentController(
            ManualServiceAssignmentService manualAssignments,
            CurrentPrincipalProvider principals
    ) {
        this.manualAssignments = manualAssignments;
        this.principals = principals;
    }

    @PostMapping("/{taskId}/service-assignments:manual-assign")
    ResponseEntity<ManualServiceAssignmentReceipt> manualAssign(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ManualAssignServiceAssignmentRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        ManualServiceAssignmentReceipt receipt = manualAssignments.manualAssign(
                principal,
                new CommandMetadata(correlationId, idempotencyKey),
                new ManualAssignServiceAssignmentCommand(
                        taskId, request.networkAssigneeId(), request.technicianAssigneeId(),
                        request.businessType()));
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }

    /**
     * 平台初审派网点：仅激活 ACTIVE NETWORK，不强制同时指派师傅。
     * <p>
     * 复用 {@link ManualServiceAssignmentService#manualAssignNetwork}；能力与双责任初派相同。
     * 网点列表可见后，网点端可幂等确认接单或继续指派师傅。
     */
    @PostMapping("/{taskId}/service-assignments:manual-assign-network")
    ResponseEntity<NetworkPortalAcceptAssignmentReceipt> manualAssignNetwork(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody ManualAssignNetworkRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        NetworkPortalAcceptAssignmentReceipt receipt = manualAssignments.manualAssignNetwork(
                principal,
                new CommandMetadata(correlationId, idempotencyKey),
                taskId,
                request.networkAssigneeId(),
                request.businessType());
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }
}
