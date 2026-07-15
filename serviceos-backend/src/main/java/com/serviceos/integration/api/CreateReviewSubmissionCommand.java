package com.serviceos.integration.api;

import java.util.UUID;

/** 从已通过的 INTERNAL ReviewCase 创建 BYD 提审交付。 */
public record CreateReviewSubmissionCommand(UUID sourceReviewCaseId) {
}
