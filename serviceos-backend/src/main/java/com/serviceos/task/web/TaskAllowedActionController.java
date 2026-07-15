package com.serviceos.task.web;

import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import com.serviceos.task.api.TaskAllowedActionQueryService;
import com.serviceos.task.api.TaskAllowedActions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Task 资源的服务端动作投影 HTTP 适配器。 */
@RestController
@RequestMapping("/api/v1/tasks")
final class TaskAllowedActionController {
    private final TaskAllowedActionQueryService actions;
    private final CurrentPrincipalProvider principals;

    TaskAllowedActionController(
            TaskAllowedActionQueryService actions,
            CurrentPrincipalProvider principals
    ) {
        this.actions = actions;
        this.principals = principals;
    }

    @GetMapping("/{taskId}/allowed-actions")
    ResponseEntity<TaskAllowedActions> get(
            @PathVariable UUID taskId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        TaskAllowedActions result = actions.get(principals.current(), correlationId, taskId);
        return ResponseEntity.ok()
                .eTag(Long.toString(result.resourceVersion()))
                .header(CorrelationIds.HEADER_NAME, correlationId)
                .body(result);
    }
}
