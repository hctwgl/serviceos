package com.serviceos.integration.api;

import java.util.UUID;

/** 外发交付队列的受控筛选；status 为空时服务端默认 UNKNOWN。 */
public record OutboundDeliveryQueueQuery(
        UUID projectId,
        String status,
        String businessMessageType,
        UUID sourceWorkOrderId,
        UUID sourceReviewCaseId,
        String cursor,
        int limit
) {
}
