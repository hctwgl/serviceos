package com.serviceos.dispatch.web;

import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.NetworkPortalReassignTechnicianService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
 * Network Portal 改派师傅 HTTP 适配器。
 * <p>
 * networkId 仅来自可信头 X-Network-Context；不接受请求体中的 networkAssigneeId。
 */
@RestController
@RequestMapping("/api/v1/network-portal")
final class NetworkPortalReassignTechnicianController {
    private final NetworkPortalReassignTechnicianService reassignTechnicians;
    private final CurrentPrincipalProvider principals;

    NetworkPortalReassignTechnicianController(
            NetworkPortalReassignTechnicianService reassignTechnicians,
            CurrentPrincipalProvider principals
    ) {
        this.reassignTechnicians = reassignTechnicians;
        this.principals = principals;
    }

    @PostMapping("/tasks/{taskId}:reassign-technician")
    ResponseEntity<ManualServiceAssignmentReceipt> reassignTechnician(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-Network-Context", required = false) String networkContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody NetworkPortalReassignTechnicianRequest request
    ) {
        ManualServiceAssignmentReceipt receipt = reassignTechnicians.reassignTechnician(
                principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                networkContext,
                taskId,
                request.technicianAssigneeId(),
                request.businessType(),
                request.reasonCode());
        return ResponseEntity.ok()
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }

    record NetworkPortalReassignTechnicianRequest(
            @NotBlank @Size(max = 128) String technicianAssigneeId,
            @NotBlank @Size(max = 100) String businessType,
            @NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,99}$") String reasonCode
    ) {
    }
}
