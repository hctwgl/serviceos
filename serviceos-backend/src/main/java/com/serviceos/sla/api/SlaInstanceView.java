package com.serviceos.sla.api;

import java.time.Instant;
import java.util.UUID;

/** Task SLA 的稳定只读事实。 */
public record SlaInstanceView(
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
