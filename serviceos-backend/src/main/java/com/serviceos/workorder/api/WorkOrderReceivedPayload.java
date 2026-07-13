package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

/** WorkOrderReceived v1 的稳定业务载荷，不包含车企原始报文或客户敏感字段。 */
public record WorkOrderReceivedPayload(
        UUID workOrderId,
        UUID projectId,
        String externalOrderCode,
        String clientCode,
        String brandCode,
        String serviceProductCode,
        ConfigurationBundleRef bundleRef,
        Instant receivedAt
) {
    public record ConfigurationBundleRef(
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String manifestDigest
    ) {
    }
}
