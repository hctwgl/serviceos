package com.serviceos.evidence.application;

import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;

/** EvidenceSlot 当前数量投影：只统计计入数量的 Revision 所对应的 Item。 */
final class EvidenceSlotStatusProjector {
    private EvidenceSlotStatusProjector() {
    }

    static String project(int minCount, Integer maxCount, int countingItemCount) {
        if (countingItemCount < 0) {
            throw new IllegalArgumentException("countingItemCount must be >= 0");
        }
        if (maxCount != null && countingItemCount > maxCount) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "EvidenceSlot counting items exceed maxCount");
        }
        if (countingItemCount == 0) {
            return minCount > 0 ? "MISSING" : "SATISFIED";
        }
        if (countingItemCount < minCount) {
            return "PARTIAL";
        }
        return "SATISFIED";
    }

    /** M38：STORED/VALIDATING/VALIDATED 计入；隔离、校验失败与作废不计入。 */
    static boolean countsTowardSlot(String revisionStatus) {
        return "STORED".equals(revisionStatus)
                || "VALIDATING".equals(revisionStatus)
                || "VALIDATED".equals(revisionStatus);
    }
}
