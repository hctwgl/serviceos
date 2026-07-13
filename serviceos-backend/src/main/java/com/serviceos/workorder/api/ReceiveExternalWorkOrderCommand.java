package com.serviceos.workorder.api;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 外部车企订单进入 ServiceOS 的统一命令。payloadDigest 用于业务幂等冲突检测，
 * configurationBundleVersion 必须是接单时解析出的精确版本，历史工单默认不漂移。
 */
public record ReceiveExternalWorkOrderCommand(
        String clientCode,
        String brandCode,
        String serviceProductCode,
        String externalOrderCode,
        String payloadDigest,
        String configurationBundleCode,
        String configurationBundleVersion,
        String provinceCode,
        String cityCode,
        String districtCode,
        String customerName,
        String customerMobile,
        String serviceAddress,
        String vehicleVin,
        LocalDateTime externalDispatchedAt
) {
    public ReceiveExternalWorkOrderCommand {
        clientCode = text(clientCode, "clientCode", 64);
        brandCode = text(brandCode, "brandCode", 64);
        serviceProductCode = text(serviceProductCode, "serviceProductCode", 96);
        externalOrderCode = text(externalOrderCode, "externalOrderCode", 128);
        payloadDigest = text(payloadDigest, "payloadDigest", 64).toLowerCase(java.util.Locale.ROOT);
        if (!payloadDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("payloadDigest must be SHA-256 hex");
        }
        configurationBundleCode = text(configurationBundleCode, "configurationBundleCode", 128);
        configurationBundleVersion = text(configurationBundleVersion, "configurationBundleVersion", 64);
        provinceCode = text(provinceCode, "provinceCode", 16);
        cityCode = text(cityCode, "cityCode", 16);
        districtCode = text(districtCode, "districtCode", 16);
        customerName = text(customerName, "customerName", 128);
        customerMobile = text(customerMobile, "customerMobile", 32);
        serviceAddress = text(serviceAddress, "serviceAddress", 512);
        vehicleVin = text(vehicleVin, "vehicleVin", 32).toUpperCase(java.util.Locale.ROOT);
        Objects.requireNonNull(externalDispatchedAt, "externalDispatchedAt");
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
