package com.serviceos.integration.referenceoem.api;

/**
 * REFERENCE / SAMPLE 入站响应。
 *
 * <p>非生产协议；真实第二家车企接入前字段与错误码均可能替换，见 {@code TBD_EXTERNAL_CONTRACT}。</p>
 */
public record ReferenceOemInboundOrderResponse(
        boolean success,
        String code,
        String message,
        String orderCode,
        String adapterVersion,
        String mappingVersion,
        boolean replay
) {
    public static ReferenceOemInboundOrderResponse accepted(
            String orderCode, String adapterVersion, String mappingVersion, boolean replay) {
        return new ReferenceOemInboundOrderResponse(
                true, "ACCEPTED", "accepted", orderCode, adapterVersion, mappingVersion, replay);
    }

    public static ReferenceOemInboundOrderResponse rejected(String code, String message) {
        return new ReferenceOemInboundOrderResponse(
                false, code, message, null, null, null, false);
    }
}
