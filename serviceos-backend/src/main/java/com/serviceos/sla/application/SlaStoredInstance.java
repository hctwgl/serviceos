package com.serviceos.sla.application;

import java.time.Instant;
import java.util.UUID;

/** SLA 模块内部的持久化只读行；HTTP 动态字段由应用层统一计算。 */
public record SlaStoredInstance(
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
        long aggregateVersion
) {
}
