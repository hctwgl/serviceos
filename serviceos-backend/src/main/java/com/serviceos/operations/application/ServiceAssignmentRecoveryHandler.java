package com.serviceos.operations.application;

import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.operations.api.ResolveServiceAssignmentTimeoutCommand;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * 只接受已经完成 TaskAssignment 对齐的终态事件；ServiceAssignmentActivated 尚处于 guard 窗口，
 * 不能被误当作恢复完成。
 */
@Service
final class ServiceAssignmentRecoveryHandler implements OutboxMessageHandler {
    private final OperationalExceptionService exceptions;
    private final ObjectMapper objectMapper;

    ServiceAssignmentRecoveryHandler(
            OperationalExceptionService exceptions,
            ObjectMapper objectMapper
    ) {
        this.exceptions = exceptions;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1
                && "service.assignment.activation-completed".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"dispatch".equals(message.module())
                || !"ServiceAssignment".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported ServiceAssignment recovery envelope");
        }
        RecoveryPayload payload = read(message.payload());
        if (!payload.serviceAssignmentId().toString().equals(message.aggregateId())
                || !"ACTIVE".equals(payload.status())
                || !"TASK_ASSIGNMENT_ACTIVATED".equals(payload.reasonCode())) {
            throw new IllegalArgumentException("ServiceAssignment recovery identity mismatch");
        }
        exceptions.resolveServiceAssignmentTimeout(new ResolveServiceAssignmentTimeoutCommand(
                message.tenantId(), message.eventId(), message.schemaVersion(), message.payloadDigest(),
                payload.sagaId(), payload.serviceAssignmentId(), payload.workOrderId(), payload.taskId(),
                message.aggregateVersion(), payload.occurredAt(), message.correlationId()));
    }

    private RecoveryPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, RecoveryPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "ServiceAssignment recovery payload cannot be decoded", exception);
        }
    }

    private record RecoveryPayload(
            UUID serviceAssignmentId,
            UUID sagaId,
            UUID workOrderId,
            UUID taskId,
            String responsibilityLevel,
            String assigneeId,
            String businessType,
            String status,
            UUID supersedesServiceAssignmentId,
            UUID capacityReservationId,
            UUID guardId,
            UUID preparedTaskAssignmentId,
            String reasonCode,
            Instant occurredAt
    ) {
    }
}
