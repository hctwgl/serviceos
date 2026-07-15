package com.serviceos.integration.application;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

/** 崩溃租约耗尽时把仍处于 SENDING 的 Delivery/Attempt 精确收敛为 UNKNOWN。 */
@Service
final class OutboundDeliveryTaskFailureHandler implements OutboxMessageHandler {
    private static final String TASK_TYPE = "integration.byd.submit-review";

    private final OutboundDeliveryRepository deliveries;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    OutboundDeliveryTaskFailureHandler(
            OutboundDeliveryRepository deliveries, ObjectMapper objectMapper, Clock clock
    ) {
        this.deliveries = deliveries;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1 && "task.execution.manual-intervention-required".equals(eventType);
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported task failure envelope");
        }
        Payload payload = read(message.payload());
        if (!TASK_TYPE.equals(payload.taskType())) {
            return;
        }
        if (!payload.taskId().toString().equals(message.aggregateId())
                || !"MANUAL_INTERVENTION".equals(payload.status())
                || payload.attemptId() == null || payload.errorCode() == null) {
            throw new IllegalArgumentException("OutboundDelivery task failure identity mismatch");
        }
        deliveries.markSendingUnknownByTaskAttempt(
                message.tenantId(), payload.attemptId(), payload.errorCode(), clock.instant());
    }

    private Payload read(String payload) {
        try {
            return objectMapper.readValue(payload, Payload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Task failure payload cannot be decoded", exception);
        }
    }

    private record Payload(
            UUID taskId,
            UUID attemptId,
            String taskType,
            String businessKey,
            int attemptNo,
            String status,
            String errorCode,
            String resultRef
    ) {
    }
}
