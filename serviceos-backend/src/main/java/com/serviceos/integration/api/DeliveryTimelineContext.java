package com.serviceos.integration.api;

import java.util.UUID;

/** 供跨模块时间线投影解析 OutboundDelivery 所属工单的最小非敏感上下文。 */
public record DeliveryTimelineContext(
        UUID deliveryId,
        UUID projectId,
        UUID workOrderId
) {
}
