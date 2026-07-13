package com.serviceos.integration.byd.application;

import com.serviceos.integration.byd.api.BydCpimInstallOrderPayload;

/**
 * BYD CPIM 安装订单到 ServiceOS 统一订单语义的反腐层映射。
 */
public final class BydCpimOrderMapper {
    public static final String ADAPTER_VERSION = "byd-cpim-v7.3.1";
    public static final String MAPPING_VERSION = "byd-ocean-shandong-install-order-v1";

    public BydCpimMappedOrder map(BydCpimInstallOrderPayload source) {
        return new BydCpimMappedOrder(
                source.orderCode(),
                "BYD",
                "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL",
                source.provinceCode(),
                source.cityCode(),
                source.areaCode(),
                source.contactName(),
                source.contactMobile(),
                source.contactAddress(),
                source.vin(),
                source.carSeries(),
                source.carModel(),
                source.wallboxName(),
                source.wallboxPower(),
                "1".equals(source.bringWallbox()),
                source.dispatchTime(),
                source.dealerName(),
                source.rightCode(),
                source.orderAmount(),
                source.source(),
                source.channel(),
                ADAPTER_VERSION,
                MAPPING_VERSION
        );
    }
}
