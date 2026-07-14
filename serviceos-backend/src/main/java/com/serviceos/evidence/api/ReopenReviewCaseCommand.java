package com.serviceos.evidence.api;

import java.util.UUID;

/** 重开已通过的 ReviewCase，产生同 Snapshot 的新 OPEN 案例。 */
public record ReopenReviewCaseCommand(
        UUID reviewCaseId,
        String reason,
        String triggerRef,
        String approvalRef
) {
}
