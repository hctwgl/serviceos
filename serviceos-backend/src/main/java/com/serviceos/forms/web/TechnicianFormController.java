package com.serviceos.forms.web;

import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.forms.api.FormValidationIssue;
import com.serviceos.forms.api.SubmitFormCommand;
import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TechnicianFormService;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Technician Portal 表单协议适配器；不接受 tenant、actor、network 或 submittedBy。 */
@RestController
@RequestMapping("/api/v1/technician/me/tasks/{taskId}")
final class TechnicianFormController {
    private final TechnicianFormService forms;
    private final CurrentPrincipalProvider principals;
    private final ObjectMapper objectMapper;

    TechnicianFormController(
            TechnicianFormService forms, CurrentPrincipalProvider principals, ObjectMapper objectMapper
    ) {
        this.forms = forms;
        this.principals = principals;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/forms")
    List<TaskFormResponse> list(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return forms.listForTask(principals.current(), correlationId, technicianContext, taskId)
                .stream().map(this::formResponse).toList();
    }

    @PostMapping("/form-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    FormSubmissionResponse submit(
            @PathVariable UUID taskId,
            @RequestHeader(value = "X-Technician-Context", required = false) String technicianContext,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @Valid @RequestBody SubmitFormRequest request
    ) {
        FormSubmissionView result = forms.submit(
                principals.current(), new CommandMetadata(correlationId, idempotencyKey), technicianContext,
                new SubmitFormCommand(taskId, request.formVersionId(), json(request.values()), null));
        return submissionResponse(result);
    }

    private TaskFormResponse formResponse(TaskFormDefinition form) {
        return new TaskFormResponse(form.taskId(), form.formVersionId(), form.formKey(),
                form.semanticVersion(), form.schemaVersion(), tree(form.definitionJson()), form.contentDigest());
    }

    private FormSubmissionResponse submissionResponse(FormSubmissionView submission) {
        return new FormSubmissionResponse(
                submission.submissionId(), submission.taskId(), submission.projectId(),
                submission.formVersionId(), submission.formKey(), submission.submissionVersion(),
                tree(submission.valuesJson()), submission.contentDigest(), submission.validationStatus(),
                submission.errors(), submission.warnings(), submission.submittedAt());
    }

    private JsonNode tree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored form JSON is invalid", exception);
        }
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("values are invalid", exception);
        }
    }

    record SubmitFormRequest(@NotNull UUID formVersionId, @NotNull JsonNode values) { }

    record TaskFormResponse(
            UUID taskId, UUID formVersionId, String formKey, String semanticVersion,
            String schemaVersion, JsonNode definition, String contentDigest) { }

    record FormSubmissionResponse(
            UUID submissionId, UUID taskId, UUID projectId, UUID formVersionId, String formKey,
            int submissionVersion, JsonNode values, String contentDigest, String validationStatus,
            List<FormValidationIssue> errors, List<FormValidationIssue> warnings, Instant submittedAt) { }
}
