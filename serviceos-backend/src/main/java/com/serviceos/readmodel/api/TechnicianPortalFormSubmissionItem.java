package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** Technician 任务详情的表单提交安全摘要；不含 values、校验消息、摘要正文或提交人。 */
public record TechnicianPortalFormSubmissionItem(
        UUID submissionId,
        UUID formVersionId,
        String formKey,
        int submissionVersion,
        String validationStatus,
        int errorCount,
        int warningCount,
        Instant submittedAt
) {
}
