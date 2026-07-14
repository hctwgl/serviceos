package com.serviceos.evidence.api;

import java.util.UUID;

/** 为 TASK_SUBMISSION Snapshot 创建审核案例。 */
public record CreateReviewCaseCommand(UUID evidenceSetSnapshotId, String policyVersion) {
}
