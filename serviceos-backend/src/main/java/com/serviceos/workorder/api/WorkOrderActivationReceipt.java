package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderActivationReceipt(
        UUID workOrderId,
        String status,
        long aggregateVersion,
        boolean replay,
        Instant activatedAt
) {
}
