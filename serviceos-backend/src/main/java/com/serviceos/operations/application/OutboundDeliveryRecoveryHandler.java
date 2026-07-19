package com.serviceos.operations.application;

import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.operations.api.ResolveTaskFailureExceptionsCommand;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** 将明确外部 ACK 或人工处置后的恢复事实映射为 Operations 异常闭环命令。 */
@Service
final class OutboundDeliveryRecoveryHandler implements OutboxMessageHandler {
    private static final String DEFAULT_TASK_TYPE = "integration.byd.submit-review";
    private static final String RECOVERY_TYPE = "OUTBOUND_DELIVERY_ACKNOWLEDGED";

    private final OperationalExceptionService exceptions;
    private final ObjectMapper objectMapper;

    OutboundDeliveryRecoveryHandler(
            OperationalExceptionService exceptions,
            ObjectMapper objectMapper
    ) {
        this.exceptions = exceptions;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1 && "integration.outbound-delivery-recovered".equals(eventType);
    }

    @Override
    public void handle(OutboxMessage message) {
        if (!"integration".equals(message.module())
                || !"OutboundDelivery".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported outbound delivery recovery envelope");
        }
        Payload payload = read(message.payload());
        LinkedHashSet<UUID> taskIds = payload.recoveredTaskIds() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(payload.recoveredTaskIds());
        if (payload.deliveryId() == null
                || !payload.deliveryId().toString().equals(message.aggregateId())
                || payload.successfulExecutionTaskId() == null
                || taskIds.isEmpty()
                || taskIds.size() != payload.recoveredTaskIds().size()
                || !taskIds.contains(payload.successfulExecutionTaskId())
                || payload.acknowledgedAt() == null
                || !sameInstant(payload.acknowledgedAt(), message.occurredAt())) {
            throw new IllegalArgumentException("OutboundDelivery recovery identity mismatch");
        }
        String taskType = payload.sourceTaskType() == null || payload.sourceTaskType().isBlank()
                ? DEFAULT_TASK_TYPE : payload.sourceTaskType().trim();
        exceptions.resolveTaskFailures(new ResolveTaskFailureExceptionsCommand(
                message.tenantId(), message.eventId(), message.schemaVersion(), message.payloadDigest(),
                taskType, List.copyOf(taskIds), RECOVERY_TYPE, payload.deliveryId().toString(),
                payload.acknowledgedAt(), message.correlationId()));
    }

    private Payload read(String payload) {
        try {
            return objectMapper.readValue(payload, Payload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("OutboundDelivery recovery payload cannot be decoded", exception);
        }
    }

    private record Payload(
            UUID deliveryId,
            UUID successfulExecutionTaskId,
            List<UUID> recoveredTaskIds,
            Instant acknowledgedAt,
            String sourceTaskType
    ) {
    }
    private static boolean sameInstant(Instant left, Instant right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.truncatedTo(ChronoUnit.MICROS).equals(right.truncatedTo(ChronoUnit.MICROS));
    }

}
