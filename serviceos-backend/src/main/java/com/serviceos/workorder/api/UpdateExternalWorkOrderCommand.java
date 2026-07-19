package com.serviceos.workorder.api;

import java.util.Objects;
import java.util.UUID;

/**
 * 外部车企更新工单联系/地址事实。
 *
 * <p>仅允许 RECEIVED/ACTIVE；使用乐观锁版本。不修改 Bundle 锁定与建单 payload_digest。</p>
 */
public record UpdateExternalWorkOrderCommand(
        String tenantId,
        UUID workOrderId,
        long expectedVersion,
        String customerName,
        String customerMobile,
        String serviceAddress,
        String provinceCode,
        String cityCode,
        String districtCode,
        String updateDigest,
        String correlationId,
        String causationId
) {
    public UpdateExternalWorkOrderCommand {
        tenantId = text(tenantId, "tenantId", 64);
        Objects.requireNonNull(workOrderId, "workOrderId");
        if (expectedVersion < 1) {
            throw new IllegalArgumentException("expectedVersion must be >= 1");
        }
        customerName = text(customerName, "customerName", 128);
        customerMobile = text(customerMobile, "customerMobile", 32);
        serviceAddress = text(serviceAddress, "serviceAddress", 512);
        provinceCode = text(provinceCode, "provinceCode", 16);
        cityCode = text(cityCode, "cityCode", 16);
        districtCode = text(districtCode, "districtCode", 16);
        updateDigest = text(updateDigest, "updateDigest", 64).toLowerCase(java.util.Locale.ROOT);
        if (!updateDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("updateDigest must be SHA-256 hex");
        }
        correlationId = text(correlationId, "correlationId", 128);
        causationId = text(causationId, "causationId", 160);
    }

    private static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds max length " + maxLength);
        }
        return normalized;
    }
}
