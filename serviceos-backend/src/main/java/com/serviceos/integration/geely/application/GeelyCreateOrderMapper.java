package com.serviceos.integration.geely.application;

import com.serviceos.integration.geely.api.GeelyCreateOrderPayload;
import com.serviceos.integration.spi.CreateWorkOrderMappedInbound;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 吉利 7.1 报文 → CreateWorkOrderMappedInbound。
 *
 * <p>brandCode 优先使用 carBrand；缺省 GEELY。serviceProductCode 固定家充勘安产品码，
 * 待 Bundle/ServiceProduct 配置覆盖（TBD_EXTERNAL_CONTRACT）。</p>
 */
final class GeelyCreateOrderMapper {
    static final String CLIENT_CODE = "GEELY";
    static final String DEFAULT_BRAND = "GEELY";
    static final String SERVICE_PRODUCT = "HOME_CHARGING_SURVEY_INSTALL";
    static final String MAPPING_VERSION = "geely-haohan-v1.3-create-order-v1";
    static final String BUSINESS_PREFIX = "GEELY:INSTALL:";

    private static final DateTimeFormatter ASSIGN_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private GeelyCreateOrderMapper() {
    }

    static CreateWorkOrderMappedInbound map(GeelyCreateOrderPayload payload, ObjectMapper objectMapper) {
        if (payload.installProcessNo() == null || payload.installProcessNo().isBlank()) {
            throw new IllegalArgumentException("installProcessNo is required");
        }
        if (payload.province() == null || payload.city() == null || payload.district() == null) {
            throw new IllegalArgumentException("province/city/district are required");
        }
        if (payload.contactName() == null || payload.contactPhone() == null || payload.address() == null) {
            throw new IllegalArgumentException("contactName/contactPhone/address are required");
        }
        String brand = payload.carBrand() == null || payload.carBrand().isBlank()
                ? DEFAULT_BRAND : payload.carBrand().trim();
        LocalDateTime dispatchedAt = null;
        if (payload.assignProviderTime() != null && !payload.assignProviderTime().isBlank()) {
            dispatchedAt = LocalDateTime.parse(payload.assignProviderTime().trim(), ASSIGN_FMT);
        }
        byte[] canonical = objectMapper.writeValueAsBytes(payload);
        return new CreateWorkOrderMappedInbound(
                BUSINESS_PREFIX + payload.installProcessNo().trim(),
                payload.installProcessNo().trim(),
                CLIENT_CODE,
                brand,
                SERVICE_PRODUCT,
                payload.province().trim(),
                payload.city().trim(),
                payload.district().trim(),
                payload.contactName().trim(),
                payload.contactPhone().trim(),
                payload.address().trim(),
                payload.vin() == null ? null : payload.vin().trim(),
                dispatchedAt,
                MAPPING_VERSION,
                canonical);
    }
}
