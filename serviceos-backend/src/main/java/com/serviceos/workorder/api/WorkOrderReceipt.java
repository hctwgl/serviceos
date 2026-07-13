package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

public record WorkOrderReceipt(
        UUID workOrderId,
        String externalOrderCode,
        String status,
        String configurationBundleCode,
        String configurationBundleVersion,
        boolean replay,
        Instant receivedAt
) {
}
