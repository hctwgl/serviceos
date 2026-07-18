package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderCancellationReceipt(
        UUID workOrderId,
        String status,
        long version,
        boolean replay,
        Instant cancelledAt
) {
}
