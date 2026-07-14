package com.serviceos.forms.web;

import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.forms.api.FormValidationIssue;
import com.serviceos.forms.api.SubmitFormCommand;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.CorrelationIds;
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

/** 不可变 FormSubmission HTTP 边界；tenant、actor 和 submittedBy 均来自受信 JWT。 */
@RestController
@RequestMapping("/api/v1")
final class FormSubmissionController {
    private final FormSubmissionService submissions;
    private final CurrentPrincipalProvider principals;
    private final ObjectMapper objectMapper;

    FormSubmissionController(
            FormSubmissionService submissions, CurrentPrincipalProvider principals,
            ObjectMapper objectMapper
    ) {
        this.submissions = submissions;
        this.principals = principals;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/tasks/{taskId}/form-submissions")
    @ResponseStatus(HttpStatus.CREATED)
    FormSubmissionResponse submit(
            @PathVariable UUID taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId,
            @RequestBody SubmitFormRequest request
    ) {
        return response(submissions.submit(principals.current(),
                new CommandMetadata(correlationId, idempotencyKey),
                new SubmitFormCommand(taskId, request.formVersionId(), json(request.values()),
                        request.prefillVersion())));
    }

    @GetMapping("/form-submissions/{submissionId}")
    FormSubmissionResponse get(
            @PathVariable UUID submissionId,
            @RequestAttribute(CorrelationIds.REQUEST_ATTRIBUTE) String correlationId
    ) {
        return response(submissions.get(principals.current(), correlationId, submissionId));
    }

    private FormSubmissionResponse response(FormSubmissionView submission) {
        try {
            return new FormSubmissionResponse(
                    submission.submissionId(), submission.taskId(), submission.projectId(),
                    submission.formVersionId(), submission.formKey(), submission.submissionVersion(),
                    objectMapper.readTree(submission.valuesJson()), submission.contentDigest(),
                    submission.validationStatus(), submission.errors(), submission.warnings(),
                    submission.prefillVersion(), submission.submittedBy(), submission.submittedAt());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored FormSubmission values are invalid", exception);
        }
    }

    private String json(JsonNode node) {
        try { return objectMapper.writeValueAsString(node); }
        catch (JacksonException exception) { throw new IllegalArgumentException("values are invalid", exception); }
    }

    record SubmitFormRequest(UUID formVersionId, JsonNode values, String prefillVersion) { }

    record FormSubmissionResponse(
            UUID submissionId, UUID taskId, UUID projectId, UUID formVersionId, String formKey,
            int submissionVersion, JsonNode values, String contentDigest, String validationStatus,
            List<FormValidationIssue> errors, List<FormValidationIssue> warnings,
            String prefillVersion, String submittedBy, Instant submittedAt) { }
}
