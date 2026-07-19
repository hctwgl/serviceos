package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Task 领域事件触发的 NOTIFICATION 派发命令。
 *
 * <p>调用方负责提供冻结 Bundle 引用与表达式上下文；本模块负责 Inbox、角色收件人快照、
 * Runtime 发送与 Intent/Delivery/Attempt 持久化。</p>
 */
public record NotificationEventDispatchCommand(
        String tenantId,
        UUID eventId,
        int schemaVersion,
        String payloadDigest,
        String correlationId,
        String sourceEventType,
        String sourceAggregateType,
        String sourceAggregateId,
        UUID projectId,
        UUID workOrderId,
        UUID taskId,
        UUID bundleId,
        String bundleDigest,
        ExpressionContext expressionContext
) {
    public NotificationEventDispatchCommand {
        tenantId = required(tenantId, "tenantId");
        Objects.requireNonNull(eventId, "eventId");
        payloadDigest = required(payloadDigest, "payloadDigest");
        sourceEventType = required(sourceEventType, "sourceEventType");
        sourceAggregateType = required(sourceAggregateType, "sourceAggregateType");
        sourceAggregateId = required(sourceAggregateId, "sourceAggregateId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(workOrderId, "workOrderId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(bundleId, "bundleId");
        bundleDigest = required(bundleDigest, "bundleDigest").toLowerCase(java.util.Locale.ROOT);
        Objects.requireNonNull(expressionContext, "expressionContext");
        if (correlationId == null) {
            correlationId = "";
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
