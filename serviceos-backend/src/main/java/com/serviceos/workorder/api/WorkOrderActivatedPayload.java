package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderActivatedPayload(
        UUID workOrderId,
        Instant activatedAt
) {
}
