package com.serviceos.integration.byd.api;

/**
 * BYD CPIM 入站安装订单的标准响应。
 *
 * <p>车企协议层始终返回可解析的业务结果；HTTP 状态只表达网关/传输是否成功。</p>
 */
public record BydCpimInboundOrderResponse(
        boolean success,
        String code,
        String message,
        String orderCode,
        String adapterVersion,
        String mappingVersion,
        boolean replay
) {
    public static BydCpimInboundOrderResponse accepted(
            String orderCode,
            String adapterVersion,
            String mappingVersion,
            boolean replay) {
        return new BydCpimInboundOrderResponse(
                true,
                replay ? "REPLAYED" : "ACCEPTED",
                replay ? "request already accepted" : "request accepted",
                orderCode,
                adapterVersion,
                mappingVersion,
                replay);
    }

    public static BydCpimInboundOrderResponse rejected(String code, String message) {
        return new BydCpimInboundOrderResponse(false, code, message, null, null, null, false);
    }
}
