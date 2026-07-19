package com.serviceos.integration.byd.application;

import com.serviceos.integration.byd.api.BydCpimInstallOrderPayload;
import com.serviceos.integration.spi.CreateWorkOrderRouteHint;

/**
 * BYD CPIM 安装订单 → CREATE_WORK_ORDER 路由提示。
 *
 * <p>M336：不再反腐映射客户/地址/VIN 等领域字段；这些由冻结 INBOUND Mapping 物化。</p>
 */
public final class BydCpimOrderMapper {
    public static final String ADAPTER_VERSION = "byd-cpim-v7.3.1";
    public static final String CLIENT_CODE = "BYD";
    public static final String BRAND_CODE = "BYD_OCEAN";
    public static final String SERVICE_PRODUCT = "HOME_CHARGING_SURVEY_INSTALL";
    public static final String BUSINESS_PREFIX = "BYD:INSTALL:";

    public CreateWorkOrderRouteHint toRouteHint(BydCpimInstallOrderPayload source) {
        if (source == null) {
            throw new IllegalArgumentException("BYD payload must not be null");
        }
        if (source.orderCode() == null || source.orderCode().isBlank()) {
            throw new IllegalArgumentException("orderCode must not be blank");
        }
        if (source.provinceCode() == null || source.provinceCode().isBlank()) {
            throw new IllegalArgumentException("provinceCode must not be blank");
        }
        String orderCode = source.orderCode().trim();
        return new CreateWorkOrderRouteHint(
                BUSINESS_PREFIX + orderCode,
                orderCode,
                CLIENT_CODE,
                BRAND_CODE,
                SERVICE_PRODUCT,
                source.provinceCode().trim());
    }
}
