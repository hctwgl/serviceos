package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/** Technician Portal 可见的最小整改投影；不暴露审核人、上传人或内部摘要。 */
public record TechnicianCorrectionView(
        UUID correctionCaseId,
        UUID sourceTaskId,
        UUID correctionTaskId,
        String caseStatus,
        List<String> reasonCodes,
        String taskStatus,
        long taskVersion,
        UUID latestResubmissionSnapshotId,
        int resubmissionCount
) {
}
