package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** 容量权威上限变更事实。 */
public record CapacityConfiguredPayload(
        UUID capacityCounterId,
        String responsibilityLevel,
        String assigneeId,
        String businessType,
        int maxUnits,
        int occupiedUnits,
        long version,
        Instant configuredAt
) {
}
