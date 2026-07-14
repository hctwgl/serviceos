package com.serviceos.task.web;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.TaskAssignmentBatchReceipt;
import com.serviceos.task.api.TaskAssignmentService;
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

/** 人工 Task 候选责任快照的 HTTP 适配器。 */
@RestController
@RequestMapping("/api/v1/tasks")
final class TaskAssignmentController {
    private final TaskAssignmentService assignments;
    private final CurrentPrincipalProvider principals;

    TaskAssignmentController(TaskAssignmentService assignments, CurrentPrincipalProvider principals) {
        this.assignments = assignments;
        this.principals = principals;
    }

    @PostMapping("/{taskId}:assign-candidates")
    ResponseEntity<TaskAssignmentBatchReceipt> assignCandidates(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("If-Match") String ifMatch,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody AssignTaskCandidatesRequest request
    ) {
        CurrentPrincipal principal = principals.current();
        TaskAssignmentBatchReceipt receipt = assignments.assignCandidates(
                principal,
                new CommandMetadata(correlationId, idempotencyKey),
                new AssignTaskCandidatesCommand(
                        taskId, TaskHttpPreconditions.version(ifMatch),
                        request.candidatePrincipalIds(), request.sourceType(), request.sourceId()));
        return ResponseEntity.ok()
                .eTag(Long.toString(receipt.taskVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(receipt);
    }
}
