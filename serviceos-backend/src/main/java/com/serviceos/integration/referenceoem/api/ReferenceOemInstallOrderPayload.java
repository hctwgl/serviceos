package com.serviceos.integration.referenceoem.api;

import java.time.LocalDateTime;

/**
 * REFERENCE / SAMPLE 安装订单正文。
 *
 * <p>使用平台语义字段演示 SPI 接入，不代表吉利/广汽等真实协议（{@code TBD_EXTERNAL_CONTRACT}）。</p>
 */
public record ReferenceOemInstallOrderPayload(
        String externalOrderCode,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        String cityCode,
        String districtCode,
        String customerName,
        String customerMobile,
        String serviceAddress,
        String vehicleVin,
        LocalDateTime dispatchedAt
) {
}
