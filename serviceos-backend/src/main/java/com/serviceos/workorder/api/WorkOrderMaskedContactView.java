package com.serviceos.workorder.api;

import java.util.UUID;

/**
 * 工单客户联系信息的策略化输出投影。
 *
 * <p>字段名为兼容既有 OpenAPI 暂保留 {@code masked*}。当全局业务数据脱敏开关关闭时返回授权范围内的原值；
 * 开启时返回脱敏值。无论开关状态如何，调用方仍必须通过工单读取授权。</p>
 */
public record WorkOrderMaskedContactView(
        UUID workOrderId,
        String maskedCustomerName,
        String maskedCustomerPhone,
        String maskedServiceAddress
) {
}
