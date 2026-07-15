package com.serviceos.operations.application;

import com.serviceos.operations.api.OpenTaskFailureCommand;
import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/** 把所有自动 Task 的可靠人工接管事实转为去重 OperationalException 和 HUMAN Task。 */
@Service
final class TaskFailureOperationalExceptionHandler implements OutboxMessageHandler {
    private final OperationalExceptionService exceptions;
    private final ObjectMapper objectMapper;

    TaskFailureOperationalExceptionHandler(
            OperationalExceptionService exceptions, ObjectMapper objectMapper
    ) {
        this.exceptions = exceptions;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1 && "task.execution.manual-intervention-required".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported task failure envelope");
        }
        Payload payload = read(message.payload());
        if (!payload.taskId().toString().equals(message.aggregateId())
                || !"MANUAL_INTERVENTION".equals(payload.status())
                || payload.attemptId() == null || payload.taskType() == null
                || payload.taskType().isBlank() || payload.errorCode() == null
                || payload.errorCode().isBlank()) {
            throw new IllegalArgumentException("Task failure identity mismatch");
        }
        exceptions.openFromTaskFailure(new OpenTaskFailureCommand(
                message.tenantId(), message.eventId(), message.schemaVersion(), message.payloadDigest(),
                payload.taskId(), payload.attemptId(), payload.taskType(), payload.errorCode(),
                message.correlationId()));
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
