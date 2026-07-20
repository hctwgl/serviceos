package com.serviceos.evidence.web;

import com.serviceos.evidence.api.TechnicianCompleteTaskCommand;
import com.serviceos.evidence.api.TechnicianEvidenceService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.ClientMetadata;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/** Technician 任务提交边界；客户端只提交权威对象 ID，不自行拼装引用 URI 或摘要。 */
@RestController
@RequestMapping("/api/v1/technician/me")
final class TechnicianTaskCompletionController {
    private final TechnicianEvidenceService evidence;
    private final CurrentPrincipalProvider principals;

    TechnicianTaskCompletionController(TechnicianEvidenceService evidence, CurrentPrincipalProvider principals) {
        this.evidence = evidence;
        this.principals = principals;
    }

    @PostMapping("/tasks/{taskId}:complete")
    ResponseEntity<TechnicianTaskCompletionResponse> complete(
            @PathVariable UUID taskId,
            @RequestHeader("X-Technician-Context") String context,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestAttribute(value = ClientMetadata.KIND_ATTRIBUTE, required = false) String clientKind,
            @Valid @RequestBody TechnicianCompleteTaskRequest request
    ) {
        HumanTaskCommandReceipt receipt = evidence.completeTask(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey),
                context, clientKind,
                new TechnicianCompleteTaskCommand(taskId, version(ifMatch),
                        request.evidenceSetSnapshotId(), request.formSubmissionId()));
        return ResponseEntity.ok().eTag(Long.toString(receipt.version()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(new TechnicianTaskCompletionResponse(
                        receipt.taskId(), receipt.status(), receipt.version(), receipt.occurredAt()));
    }

    private static long version(String ifMatch) {
        if (ifMatch == null || !ifMatch.matches("\"[1-9][0-9]*\"")) {
            throw new IllegalArgumentException("If-Match must contain one quoted positive task version");
        }
        try {
            return Long.parseLong(ifMatch.substring(1, ifMatch.length() - 1));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match task version is outside the supported range", exception);
        }
    }

    record TechnicianCompleteTaskRequest(@NotNull UUID evidenceSetSnapshotId, UUID formSubmissionId) {}
    record TechnicianTaskCompletionResponse(UUID taskId, String status, long resourceVersion, Instant occurredAt) {}
}
