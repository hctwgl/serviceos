package com.serviceos.sla.application;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/** 只接受 Task 权威创建/完成事件，事件身份与业务发生时间不一致时失败关闭。 */
@Service
final class TaskSlaEventHandler implements OutboxMessageHandler {
    private final TaskSlaEventConsumer clocks;
    private final ObjectMapper objectMapper;

    TaskSlaEventHandler(TaskSlaEventConsumer clocks, ObjectMapper objectMapper) {
        this.clocks = clocks;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return ("task.created".equals(eventType) && schemaVersion == 1)
                || ("task.completed".equals(eventType) && (schemaVersion == 1 || schemaVersion == 2));
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported Task SLA event envelope");
        }
        if ("task.created".equals(message.eventType())) {
            TaskCreated payload = read(message.payload(), TaskCreated.class);
            requireIdentity(message, payload.taskId(), payload.createdAt());
            clocks.start(message, payload.taskId(), payload.taskType(), payload.createdAt());
            return;
        }
        TaskCompleted payload = read(message.payload(), TaskCompleted.class);
        requireIdentity(message, payload.taskId(), payload.completedAt());
        clocks.stop(message, payload.taskId(), payload.completedAt());
    }

    private static void requireIdentity(OutboxMessage message, UUID taskId, Instant occurredAt) {
        if (taskId == null || occurredAt == null
                || !taskId.toString().equals(message.aggregateId())
                || !occurredAt.equals(message.occurredAt())) {
            throw new IllegalArgumentException("Task SLA event identity mismatch");
        }
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task SLA event payload cannot be decoded", exception);
        }
    }

    private record TaskCreated(UUID taskId, String taskType, Instant createdAt) {
    }

    private record TaskCompleted(UUID taskId, Instant completedAt) {
    }
}
