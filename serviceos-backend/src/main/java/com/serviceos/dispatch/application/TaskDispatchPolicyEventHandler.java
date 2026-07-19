package com.serviceos.dispatch.application;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/** 消费 task.created，将冻结 DISPATCH 解析为 NETWORK ServiceAssignment。 */
@Service
final class TaskDispatchPolicyEventHandler implements OutboxMessageHandler {
    private final TaskDispatchPolicyEventConsumer consumer;
    private final ObjectMapper objectMapper;

    TaskDispatchPolicyEventHandler(TaskDispatchPolicyEventConsumer consumer, ObjectMapper objectMapper) {
        this.consumer = consumer;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "task.created".equals(eventType) && schemaVersion == 1;
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported Task dispatch-policy event envelope");
        }
        TaskCreated payload = read(message.payload(), TaskCreated.class);
        if (payload.taskId() == null || payload.createdAt() == null
                || !payload.taskId().toString().equals(message.aggregateId())
                || !payload.createdAt().equals(message.occurredAt())) {
            throw new IllegalArgumentException("Task dispatch-policy event identity mismatch");
        }
        consumer.applyFrozenPolicy(message, payload.taskId(), payload.createdAt());
    }

    private <T> T read(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task dispatch-policy event payload cannot be decoded", exception);
        }
    }

    private record TaskCreated(UUID taskId, String taskType, Instant createdAt) {
    }
}
