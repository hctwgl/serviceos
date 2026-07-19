package com.serviceos.integration.geely.application;

import com.serviceos.integration.geely.api.GeelyCreateOrderPayload;
import com.serviceos.integration.spi.CreateWorkOrderRouteHint;

/**
 * 吉利 7.1 报文 → CREATE_WORK_ORDER 路由提示。
 *
 * <p>M336：不再映射客户/地址/VIN 等领域字段；brand 缺省 GEELY，product 固定家充勘安。
 * 领域字段由冻结 INBOUND Mapping 物化。</p>
 */
final class GeelyCreateOrderMapper {
    static final String CLIENT_CODE = "GEELY";
    static final String DEFAULT_BRAND = "GEELY";
    static final String SERVICE_PRODUCT = "HOME_CHARGING_SURVEY_INSTALL";
    static final String BUSINESS_PREFIX = "GEELY:INSTALL:";

    private GeelyCreateOrderMapper() {
    }

    static CreateWorkOrderRouteHint toRouteHint(GeelyCreateOrderPayload payload) {
        if (payload.installProcessNo() == null || payload.installProcessNo().isBlank()) {
            throw new IllegalArgumentException("installProcessNo is required");
        }
        if (payload.province() == null || payload.province().isBlank()) {
            throw new IllegalArgumentException("province is required");
        }
        String orderCode = payload.installProcessNo().trim();
        String brand = payload.carBrand() == null || payload.carBrand().isBlank()
                ? DEFAULT_BRAND : payload.carBrand().trim();
        return new CreateWorkOrderRouteHint(
                BUSINESS_PREFIX + orderCode,
                orderCode,
                CLIENT_CODE,
                brand,
                SERVICE_PRODUCT,
                payload.province().trim());
    }
}
