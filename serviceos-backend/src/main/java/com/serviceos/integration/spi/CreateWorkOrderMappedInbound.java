package com.serviceos.integration.spi;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 适配器完成反腐映射后的建单 Canonical 意图。
 *
 * <p>字段使用 ServiceOS 统一语义；不得把 OEM 协议原文字段名当作权威模型。</p>
 */
public record CreateWorkOrderMappedInbound(
        String businessKey,
        String externalOrderCode,
        String clientCode,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        String cityCode,
        String districtCode,
        String customerName,
        String customerMobile,
        String serviceAddress,
        String vehicleVin,
        LocalDateTime dispatchedAt,
        String mappingVersionId,
        byte[] canonicalPayload
) {
    public static final String MESSAGE_TYPE_CREATE_WORK_ORDER = "CREATE_WORK_ORDER";

    public CreateWorkOrderMappedInbound {
        businessKey = required(businessKey, "businessKey");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        clientCode = required(clientCode, "clientCode");
        brandCode = required(brandCode, "brandCode");
        serviceProductCode = required(serviceProductCode, "serviceProductCode");
        provinceCode = required(provinceCode, "provinceCode");
        cityCode = required(cityCode, "cityCode");
        districtCode = required(districtCode, "districtCode");
        mappingVersionId = required(mappingVersionId, "mappingVersionId");
        Objects.requireNonNull(canonicalPayload, "canonicalPayload must not be null");
        if (canonicalPayload.length == 0) {
            throw new IllegalArgumentException("canonicalPayload must not be empty");
        }
        canonicalPayload = canonicalPayload.clone();
    }

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
