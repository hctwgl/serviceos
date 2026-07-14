package com.serviceos.operations.application;

import com.serviceos.operations.api.OpenServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/** 把可靠的 ServiceAssignment saga 超时事实转成去重运营异常与人工处理 Task。 */
@Service
final class ServiceAssignmentTimeoutHandler implements OutboxMessageHandler {
    private final OperationalExceptionService exceptions;
    private final ObjectMapper objectMapper;

    ServiceAssignmentTimeoutHandler(
            OperationalExceptionService exceptions,
            ObjectMapper objectMapper
    ) {
        this.exceptions = exceptions;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1 && "service.assignment.activation-timed-out".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"dispatch".equals(message.module())
                || !"ServiceAssignmentActivationSaga".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported ServiceAssignment timeout envelope");
        }
        TimeoutPayload payload = read(message.payload());
        if (!payload.sagaId().toString().equals(message.aggregateId())
                || payload.sagaVersion() != message.aggregateVersion()
                || !"ACTIVATION_SAGA_TIMEOUT".equals(payload.errorCode())) {
            throw new IllegalArgumentException("ServiceAssignment timeout identity mismatch");
        }
        exceptions.openFromServiceAssignmentTimeout(new OpenServiceAssignmentTimeoutCommand(
                message.tenantId(), message.eventId(), message.schemaVersion(), message.payloadDigest(),
                payload.timeoutId(), payload.sagaId(), payload.serviceAssignmentId(),
                payload.workOrderId(), payload.taskId(), payload.stage(), payload.sagaVersion(),
                payload.errorCode(), payload.detectedAt(), message.correlationId()));
    }

    private TimeoutPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, TimeoutPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("ServiceAssignment timeout payload cannot be decoded", exception);
        }
    }

    private record TimeoutPayload(
            UUID timeoutId,
            UUID sagaId,
            UUID serviceAssignmentId,
            UUID workOrderId,
            UUID taskId,
            String stage,
            long sagaVersion,
            Instant deadlineAt,
            Instant detectedAt,
            String errorCode
    ) {
    }
}
