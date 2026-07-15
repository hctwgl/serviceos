package com.serviceos.task.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.TaskExecutionAttemptPage;
import com.serviceos.task.api.TaskExecutionAttemptQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 自动 Task 执行 Attempt 历史的安全 HTTP 适配器。 */
@RestController
@RequestMapping("/api/v1/tasks")
final class TaskExecutionAttemptController {
    private final TaskExecutionAttemptQueryService attempts;
    private final CurrentPrincipalProvider principals;

    TaskExecutionAttemptController(
            TaskExecutionAttemptQueryService attempts,
            CurrentPrincipalProvider principals
    ) {
        this.attempts = attempts;
        this.principals = principals;
    }

    @GetMapping("/{taskId}/execution-attempts")
    ResponseEntity<TaskExecutionAttemptPage> list(
            @PathVariable UUID taskId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        TaskExecutionAttemptPage result = attempts.list(
                principals.current(), correlationId, taskId, cursor, limit);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.resourceVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
