package com.serviceos.dispatch.web;

import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentReceipt;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
 * Network Portal 网点接单 HTTP 适配器。
 * <p>
 * networkId 仅来自可信头 X-Network-Context；不接受请求体中的 networkAssigneeId。
 */
@RestController
@RequestMapping("/api/v1/network-portal")
final class NetworkPortalAcceptAssignmentController {
    private final NetworkPortalAcceptAssignmentService acceptAssignments;
    private final CurrentPrincipalProvider principals;

    NetworkPortalAcceptAssignmentController(
            NetworkPortalAcceptAssignmentService acceptAssignments,
            CurrentPrincipalProvider principals
    ) {
        this.acceptAssignments = acceptAssignments;
        this.principals = principals;
    }

    @PostMapping("/tasks/{taskId}:accept-assignment")
    ResponseEntity<NetworkPortalAcceptAssignmentReceipt> acceptAssignment(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody NetworkPortalAcceptAssignmentRequest request
    ) {
        NetworkPortalAcceptAssignmentReceipt receipt = acceptAssignments.acceptAssignment(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                taskId,
                request.businessType());
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }

    record NetworkPortalAcceptAssignmentRequest(
            @NotBlank @Size(max = 100) String businessType
    ) {
    }
}
