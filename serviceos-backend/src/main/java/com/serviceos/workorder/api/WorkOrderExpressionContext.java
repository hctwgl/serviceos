package com.serviceos.workorder.api;

import java.util.UUID;

/** ADR-018 M52：工单/区域事实，用于 SERVICEOS_EXPR_V1 白名单路径。 */
public record WorkOrderExpressionContext(
        UUID workOrderId,
        String clientCode,
        String brandCode,
        String serviceProductCode,
        String provinceCode,
        String cityCode,
        String districtCode
) {
}
