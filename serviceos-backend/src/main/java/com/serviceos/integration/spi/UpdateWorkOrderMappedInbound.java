package com.serviceos.integration.spi;

import java.util.Objects;

/**
 * 适配器完成反腐映射后的外部工单详情更新意图。
 */
public record UpdateWorkOrderMappedInbound(
        String businessKey,
        String externalOrderCode,
        String clientCode,
        String customerName,
        String customerMobile,
        String serviceAddress,
        String provinceCode,
        String cityCode,
        String districtCode,
        String updateDigest,
        String mappingVersionId,
        byte[] canonicalPayload
) {
    public static final String MESSAGE_TYPE_UPDATE_WORK_ORDER = "UPDATE_WORK_ORDER";

    public UpdateWorkOrderMappedInbound {
        businessKey = required(businessKey, "businessKey");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        clientCode = required(clientCode, "clientCode");
        customerName = required(customerName, "customerName");
        customerMobile = required(customerMobile, "customerMobile");
        serviceAddress = required(serviceAddress, "serviceAddress");
        provinceCode = required(provinceCode, "provinceCode");
        cityCode = required(cityCode, "cityCode");
        districtCode = required(districtCode, "districtCode");
        updateDigest = required(updateDigest, "updateDigest").toLowerCase(java.util.Locale.ROOT);
        if (!updateDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("updateDigest must be SHA-256 hex");
        }
        mappingVersionId = required(mappingVersionId, "mappingVersionId");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload must not be null");
        if (canonicalPayload.length == 0) {
            throw new IllegalArgumentException("canonicalPayload must not be empty");
        }
        canonicalPayload = canonicalPayload.clone();
    }

    @Override
    public byte[] canonicalPayload() {
        return canonicalPayload.clone();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
