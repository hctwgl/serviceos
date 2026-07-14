package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderFulfillmentReceipt(
        UUID workOrderId,
        String status,
        long version,
        boolean replay,
        Instant fulfilledAt
) {
}
