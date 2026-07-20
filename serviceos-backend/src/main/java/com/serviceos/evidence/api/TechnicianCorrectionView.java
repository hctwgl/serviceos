package com.serviceos.evidence.api;

import java.util.List;
import java.util.UUID;

/**
 * Technician Portal 可见的最小整改投影；不暴露审核人、上传人或内部摘要。
 *
 * <p>M362：可选 {@code clientCapabilityUnsupportedDetail} 注解当前客户端相对源 Task
 * 冻结 Bundle FORM/EVIDENCE 的不兼容说明；null 表示兼容或未强制预检（如 UNKNOWN）。</p>
 */
public record TechnicianCorrectionView(
        UUID correctionCaseId,
        UUID sourceTaskId,
        UUID correctionTaskId,
        String caseStatus,
        List<String> reasonCodes,
        String taskStatus,
        long taskVersion,
        UUID latestResubmissionSnapshotId,
        int resubmissionCount,
        String clientCapabilityUnsupportedDetail
) {
}
