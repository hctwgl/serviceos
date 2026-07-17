package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** Network Portal 容量计数行。 */
public record NetworkPortalCapacityItem(
        UUID capacityCounterId,
        String businessType,
        int maxUnits,
        int occupiedUnits,
        int availableUnits,
        long version,
        Instant updatedAt
) {
}
