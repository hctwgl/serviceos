package com.serviceos.integration.byd.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 反腐层输出。字段采用 ServiceOS 统一语义，不暴露 CPIM 原字段命名。
 */
public record BydCpimMappedOrder(
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
        String vehicleSeries,
        String vehicleModel,
        String equipmentName,
        String equipmentPower,
        boolean equipmentProvidedOnSite,
        LocalDateTime dispatchedAt,
        String dealerName,
        String entitlementCode,
        BigDecimal declaredOrderAmount,
        String sourceCode,
        String channelCode,
        String adapterVersion,
        String mappingVersion
) {
}
