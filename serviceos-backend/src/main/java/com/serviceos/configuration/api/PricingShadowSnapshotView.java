package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.UUID;

/** 工单 SHADOW 定价试算快照只读投影（非结算、不落账）。 */
public record PricingShadowSnapshotView(
        UUID snapshotId,
        UUID workOrderId,
        UUID projectId,
        UUID sourceEventId,
        String sourceEventType,
        String pricingKey,
        String currency,
        long totalAmountMinor,
        String mode,
        String correlationId,
        Instant createdAt
) {
}
