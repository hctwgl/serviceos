package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.UUID;

/** 不含客户 PII 的 WorkOrder 运营概要。 */
public record WorkOrderView(
        UUID id, String tenantId, UUID projectId, String clientCode, String brandCode,
        String serviceProductCode, String externalOrderCode, String status,
        UUID configurationBundleId, String configurationBundleCode,
        String configurationBundleVersion, String configurationBundleDigest,
        String provinceCode, String cityCode, String districtCode,
        Instant externalDispatchedAt, Instant receivedAt, Instant activatedAt,
        Instant fulfilledAt, long version
) {
}
