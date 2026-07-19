package com.serviceos.task.application;

import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.NotificationEventDispatchCommand;
import com.serviceos.configuration.api.NotificationEventDispatchService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * M326：消费 task.created / task.completed，委托 configuration 完成 NOTIFICATION 可靠投递。
 *
 * <p>无冻结 Bundle 时直接跳过（不进入 Inbox），避免无配置工单产生噪声。</p>
 */
@Service
final class TaskNotificationEventHandler implements OutboxMessageHandler {
    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final NotificationEventDispatchService notificationDispatch;
    private final ObjectMapper objectMapper;

    TaskNotificationEventHandler(
            TaskFulfillmentContextService tasks,
            WorkOrderExpressionContextQuery workOrderContexts,
            NotificationEventDispatchService notificationDispatch,
            ObjectMapper objectMapper
    ) {
        this.tasks = tasks;
        this.workOrderContexts = workOrderContexts;
        this.notificationDispatch = notificationDispatch;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1
                && ("task.created".equals(eventType) || "task.completed".equals(eventType));
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported Task notification event envelope");
        }
        UUID taskId = readTaskId(message);
        TaskFulfillmentContext task = tasks.find(message.tenantId(), taskId)
                .orElseThrow(() -> new IllegalStateException(
                        "Task does not exist for notification dispatch"));
        if (task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            return;
        }
        WorkOrderExpressionContext wo = workOrderContexts.find(message.tenantId(), task.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder expression context missing for notification dispatch"));
        ExpressionContext expressionContext = new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        wo.clientCode(), wo.brandCode(), wo.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        wo.provinceCode(), wo.cityCode(), wo.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()));
        notificationDispatch.dispatch(new NotificationEventDispatchCommand(
                message.tenantId(),
                message.eventId(),
                message.schemaVersion(),
                message.payloadDigest(),
                message.correlationId(),
                message.eventType(),
                message.aggregateType(),
                message.aggregateId(),
                task.projectId(),
                task.workOrderId(),
                taskId,
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                expressionContext));
    }

    private UUID readTaskId(OutboxMessage message) {
        UUID taskId;
        Instant occurred;
        if ("task.completed".equals(message.eventType())) {
            TaskCompleted payload = read(message.payload(), TaskCompleted.class);
            taskId = payload.taskId();
            occurred = payload.completedAt();
        } else {
            TaskCreated payload = read(message.payload(), TaskCreated.class);
            taskId = payload.taskId();
            occurred = payload.createdAt();
        }
        if (taskId == null || occurred == null
                || !taskId.toString().equals(message.aggregateId())
                || !occurred.equals(message.occurredAt())) {
            throw new IllegalArgumentException("Task notification event identity mismatch");
        }
        return taskId;
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "Task notification event payload cannot be decoded", exception);
        }
    }

    private record TaskCreated(UUID taskId, Instant createdAt) {
    }

    private record TaskCompleted(UUID taskId, Instant completedAt) {
    }
}
