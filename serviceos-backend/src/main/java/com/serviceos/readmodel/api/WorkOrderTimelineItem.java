package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.UUID;

/** 面向用户的规范化时间线条目；业务文案由客户端按稳定 eventType/templateVersion 渲染。 */
public record WorkOrderTimelineItem(
        UUID id,
        String category,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        Instant receivedAt,
        String actorId,
        String resourceType,
        UUID resourceId,
        long resourceVersion,
        String resourceCode,
        String outcomeCode,
        String correlationId,
        String displayTemplateCode,
        int displayTemplateVersion
) {
}
