package com.serviceos.integration.referenceoem.api;

/**
 * REFERENCE / SAMPLE 取消订单正文。
 *
 * <p>平台语义演示字段；非真实车企协议（{@code TBD_EXTERNAL_CONTRACT}）。</p>
 */
public record ReferenceOemCancelOrderPayload(
        String externalOrderCode,
        String reasonCode,
        /** 取消时间戳原文，用于 businessKey 后缀幂等。 */
        String cancelledAt
) {
}
