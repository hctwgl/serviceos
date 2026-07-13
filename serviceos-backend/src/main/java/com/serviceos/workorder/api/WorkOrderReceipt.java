package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderReceipt(
        UUID workOrderId,
        String tenantId,
        UUID projectId,
        String externalOrderCode,
        String status,
        UUID configurationBundleId,
        String configurationBundleCode,
        String configurationBundleVersion,
        boolean replay,
        Instant receivedAt
) {
}
