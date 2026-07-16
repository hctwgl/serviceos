package com.serviceos.forms.api;

import java.time.Instant;
import java.util.UUID;

/** 不含 values、校验消息与提交人的 FormSubmission 安全摘要。 */
public record FormSubmissionSummaryView(
        UUID submissionId,
        UUID taskId,
        UUID projectId,
        UUID formVersionId,
        String formKey,
        int submissionVersion,
        String contentDigest,
        String validationStatus,
        int errorCount,
        int warningCount,
        Instant submittedAt
) {
}
