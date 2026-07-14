package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/** 强制通过 OPEN ReviewCase。 */
public record ForceApproveReviewCaseCommand(
        UUID reviewCaseId,
        List<String> reasonCodes,
        String approvalRef,
        String note
) {
}
