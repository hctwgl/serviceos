package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/** 对 OPEN ReviewCase 追加 APPROVED/REJECTED 决定。 */
public record DecideReviewCaseCommand(
        UUID reviewCaseId,
        String decision,
        List<String> reasonCodes,
        String note
) {
}
