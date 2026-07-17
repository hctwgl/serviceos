package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/** 网点容量计数器只读行（dsp_capacity_counter）。 */
public record NetworkCapacityCounterView(
        UUID capacityCounterId,
        String businessType,
        int maxUnits,
        int occupiedUnits,
        long version,
        Instant updatedAt
) {
    public int availableUnits() {
        return Math.max(0, maxUnits - occupiedUnits);
    }
}
