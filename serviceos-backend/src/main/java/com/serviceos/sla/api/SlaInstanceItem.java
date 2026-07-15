package com.serviceos.sla.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** SLA 工作台和工单时间线共享的只读投影；动态秒数均以响应 asOf 为基准。 */
public record SlaInstanceItem(
        UUID slaInstanceId,
        UUID projectId,
        UUID workOrderId,
        UUID taskId,
        String slaRef,
        UUID policyVersionId,
        String policySemanticVersion,
        String policyContentDigest,
        String clockMode,
        long targetDurationSeconds,
        Instant startedAt,
        Instant deadlineAt,
        String status,
        Instant breachedAt,
        Instant breachDetectedAt,
        Instant completedAt,
        Long elapsedSeconds,
        Long remainingSeconds,
        Long overdueSeconds,
        long aggregateVersion,
        List<String> allowedActions
) {
    public SlaInstanceItem {
        allowedActions = List.copyOf(allowedActions);
    }
}
