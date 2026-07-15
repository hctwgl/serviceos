package com.serviceos.evidence.web;

import com.serviceos.evidence.api.EvidenceConditionDispositionService;
import com.serviceos.evidence.api.EvidenceConditionDispositionView;
import com.serviceos.evidence.api.ResolveEvidenceConditionChangeCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** 条件变化人工处置 HTTP 边界；expectedResolutionId 精确引用产生 REVIEW_REQUIRED 的代次。 */
@RestController
@RequestMapping("/api/v1")
final class EvidenceConditionDispositionController {
    private final EvidenceConditionDispositionService dispositions;
    private final CurrentPrincipalProvider principals;

    EvidenceConditionDispositionController(
            EvidenceConditionDispositionService dispositions,
            CurrentPrincipalProvider principals
    ) {
        this.dispositions = dispositions;
        this.principals = principals;
    }

    @PostMapping("/tasks/{taskId}/evidence-slots/{slotId}:resolve-condition-change")
    EvidenceConditionDispositionResponse resolve(
            @PathVariable UUID taskId,
            @PathVariable UUID slotId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody EvidenceConditionDispositionRequest request
    ) {
        EvidenceConditionDispositionView result = dispositions.resolve(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                new ResolveEvidenceConditionChangeCommand(
                        taskId, slotId, request.expectedResolutionId(), request.decision(),
                        request.reasonCode(), request.reviewRef()));
        return new EvidenceConditionDispositionResponse(
                result.dispositionId(), result.taskId(), result.slotId(), result.resolutionId(),
                result.decision(), result.reasonCode(), result.reviewRef(),
                result.decidedBy(), result.decidedAt());
    }

    record EvidenceConditionDispositionRequest(
            UUID expectedResolutionId,
            String decision,
            String reasonCode,
            String reviewRef
    ) {
    }

    record EvidenceConditionDispositionResponse(
            UUID dispositionId,
            UUID taskId,
            UUID slotId,
            UUID resolutionId,
            String decision,
            String reasonCode,
            String reviewRef,
            String decidedBy,
            Instant decidedAt
    ) {
    }
}
