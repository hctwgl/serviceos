package com.serviceos.workorder.api;

import java.util.UUID;

/**
 * 工单客户联系信息的服务端脱敏投影。
 *
 * <p>仅返回已脱敏字段，供终审等工作区安全展示；不得暴露完整手机号或完整地址。</p>
 */
public record WorkOrderMaskedContactView(
        UUID workOrderId,
        String maskedCustomerName,
        String maskedCustomerPhone,
        String maskedServiceAddress
) {
}
