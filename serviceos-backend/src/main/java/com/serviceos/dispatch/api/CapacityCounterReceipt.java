package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** 容量配置的冻结结果。 */
public record CapacityCounterReceipt(
        UUID capacityCounterId,
        ResponsibilityLevel responsibilityLevel,
        String assigneeId,
        String businessType,
        int maxUnits,
        int occupiedUnits,
        long version,
        Instant occurredAt
) {
}
