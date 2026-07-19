package com.serviceos.integration.spi;

/**
 * CANCEL_WORK_ORDER 适配器路由提示。
 *
 * <p>M339：仅承载定位键与 businessKey 基底；{@code reasonCode}/{@code approvalRef} 由 Mapping 物化。</p>
 *
 * <p>{@code createBusinessKey} / {@code businessKeyBase} 应以 hint {@code externalOrderCode}
 * 为后缀（与 CREATE rebuild 同构）。{@code businessKeySuffix} 承载 cancelDate 等协议后缀，
 * 物化后拼为 {@code businessKeyBase}:{suffix}。</p>
 */
public record CancelWorkOrderRouteHint(
        String clientCode,
        String externalOrderCode,
        String createBusinessKey,
        String businessKeyBase,
        String businessKeySuffix
) {
    public CancelWorkOrderRouteHint {
        clientCode = required(clientCode, "clientCode");
        externalOrderCode = required(externalOrderCode, "externalOrderCode");
        createBusinessKey = required(createBusinessKey, "createBusinessKey");
        businessKeyBase = required(businessKeyBase, "businessKeyBase");
        businessKeySuffix = required(businessKeySuffix, "businessKeySuffix");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
