package com.serviceos.integration.spi;

/**
 * CREATE_WORK_ORDER 适配器路由提示。
 *
 * <p>M336：仅承载 Bundle 解析与 businessKey 重写所需最小字段。领域建单字段由冻结
 * INBOUND Mapping 物化（见 {@link CreateWorkOrderMappedInbound}），适配器不得再填充
 * 客户/地址/VIN 等权威领域值。</p>
 */
public record CreateWorkOrderRouteHint(
        String businessKey,
        String externalOrderCode,
        String clientCode,
        String brandCode,
        String serviceProductCode,
        String provinceCode
) {
    public CreateWorkOrderRouteHint {
        businessKey = required(businessKey, "businessKey");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        clientCode = required(clientCode, "clientCode");
        brandCode = required(brandCode, "brandCode");
        serviceProductCode = required(serviceProductCode, "serviceProductCode");
        provinceCode = required(provinceCode, "provinceCode");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
