package com.serviceos.forms.web;

import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TaskFormQueryService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CorrelationIds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

/** 当前任务表单只读 HTTP 边界；租户和主体只取自受信 JWT。 */
@RestController
@RequestMapping("/api/v1")
final class TaskFormController {
    private final TaskFormQueryService forms;
    private final CurrentPrincipalProvider principals;
    private final ObjectMapper objectMapper;

    TaskFormController(
            TaskFormQueryService forms,
            CurrentPrincipalProvider principals,
            ObjectMapper objectMapper
    ) {
        this.forms = forms;
        this.principals = principals;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/tasks/{taskId}/forms")
    List<TaskFormResponse> list(
            @PathVariable UUID taskId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return forms.listForTask(principals.current(), correlationId, taskId)
                .stream().map(this::response).toList();
    }

    private TaskFormResponse response(TaskFormDefinition form) {
        try {
            return new TaskFormResponse(
                    form.taskId(), form.formVersionId(), form.formKey(), form.semanticVersion(),
                    form.schemaVersion(), objectMapper.readTree(form.definitionJson()), form.contentDigest());
        } catch (JacksonException exception) {
            // FORM 发布门禁已保证 JSON 合法；若存量事实损坏必须失败关闭，不能返回伪造空定义。
            throw new IllegalStateException("Published FormVersion definition is not valid JSON", exception);
        }
    }

    record TaskFormResponse(
            UUID taskId,
            UUID formVersionId,
            String formKey,
            String semanticVersion,
            String schemaVersion,
            JsonNode definition,
            String contentDigest
    ) {
    }
}
