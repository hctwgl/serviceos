package com.serviceos.integration.referenceoem.api;

/**
 * REFERENCE / SAMPLE 更新订单正文。
 *
 * <p>平台语义演示字段；非真实车企协议（{@code TBD_EXTERNAL_CONTRACT}）。</p>
 */
public record ReferenceOemUpdateOrderPayload(
        String externalOrderCode,
        String customerName,
        String customerMobile,
        String serviceAddress,
        String provinceCode,
        String cityCode,
        String districtCode
) {
}
