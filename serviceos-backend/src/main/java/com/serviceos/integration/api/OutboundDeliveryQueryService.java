package com.serviceos.integration.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** 跨工单外发交付队列查询边界。 */
public interface OutboundDeliveryQueryService {
    OutboundDeliveryQueuePage list(
            CurrentPrincipal principal, String correlationId, OutboundDeliveryQueueQuery query);
}
