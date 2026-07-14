package com.serviceos.forms.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 不可变表单提交及其权威服务端验证快照。 */
public record FormSubmissionView(
        UUID submissionId, UUID taskId, UUID projectId, UUID formVersionId, String formKey,
        int submissionVersion, String valuesJson, String contentDigest, String validationStatus,
        List<FormValidationIssue> errors, List<FormValidationIssue> warnings,
        String prefillVersion, String submittedBy, Instant submittedAt
) {
    public FormSubmissionView {
        errors = errors == null ? List.of() : List.copyOf(errors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
