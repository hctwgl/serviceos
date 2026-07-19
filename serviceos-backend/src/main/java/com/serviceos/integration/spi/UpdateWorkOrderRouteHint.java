package com.serviceos.integration.spi;

/**
 * UPDATE_WORK_ORDER 适配器路由提示。
 *
 * <p>M339：仅承载外部订单定位与 businessKey 基底。领域更新字段由冻结 INBOUND Mapping 物化。</p>
 *
 * <p>{@code businessKeyBase} 不含 updateDigest 后缀，物化后追加 {@code :}{@code updateDigest}。
 * 例如 {@code BYD:INSTALL-UPDATE:ORD-1} → {@code BYD:INSTALL-UPDATE:ORD-1:<sha256>}。</p>
 */
public record UpdateWorkOrderRouteHint(
        String clientCode,
        String externalOrderCode,
        String businessKeyBase
) {
    public UpdateWorkOrderRouteHint {
        clientCode = required(clientCode, "clientCode");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        businessKeyBase = required(businessKeyBase, "businessKeyBase");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
