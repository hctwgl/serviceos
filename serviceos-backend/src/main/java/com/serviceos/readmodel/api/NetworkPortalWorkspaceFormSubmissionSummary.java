package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/**
 * M222：Network Portal 工作区表单提交摘要；字段对齐 Admin
 * {@code WorkOrderWorkspaceFormSubmissionSummary}，故意不含 values/submittedBy。
 */
public record NetworkPortalWorkspaceFormSubmissionSummary(
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
