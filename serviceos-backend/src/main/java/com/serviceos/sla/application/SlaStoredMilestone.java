package com.serviceos.sla.application;

import java.time.Instant;
import java.util.UUID;

/** SLA 模块内部的 milestone 持久化只读行。 */
public record SlaStoredMilestone(
        UUID milestoneId, String milestoneType, Instant scheduledAt, String status,
        Instant triggeredAt, Instant detectedAt, UUID triggerEventId
) {
}
