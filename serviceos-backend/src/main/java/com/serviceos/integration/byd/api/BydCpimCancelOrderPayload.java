package com.serviceos.integration.byd.api;

/**
 * BYD 用户取消订单入站载荷（严格标量字段；未知字段失败关闭）。
 */
public record BydCpimCancelOrderPayload(
        String orderCode,
        String cancelDate,
        String cancelReason
) {
}
