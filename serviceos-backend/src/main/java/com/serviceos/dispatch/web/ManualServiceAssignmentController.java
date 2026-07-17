package com.serviceos.dispatch.web;

import com.serviceos.dispatch.api.ManualAssignServiceAssignmentCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
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
}
