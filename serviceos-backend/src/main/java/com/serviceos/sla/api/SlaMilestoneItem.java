package com.serviceos.sla.api;

import java.time.Instant;
import java.util.UUID;

/** 只追加 SLA milestone 投影。 */
public record SlaMilestoneItem(
        UUID milestoneId,
        String milestoneType,
        Instant scheduledAt,
        String status,
        Instant triggeredAt,
        Instant detectedAt,
        UUID triggerEventId
) {
}
