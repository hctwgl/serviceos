package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderCancelledPayload(
        UUID workOrderId,
        String reasonCode,
        String approvalRef,
        Instant cancelledAt
) {
}
