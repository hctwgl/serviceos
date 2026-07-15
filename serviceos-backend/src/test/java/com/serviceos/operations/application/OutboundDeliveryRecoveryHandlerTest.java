package com.serviceos.operations.application;

import com.serviceos.operations.api.OpenServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.OpenTaskFailureCommand;
import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.operations.api.OperationalExceptionView;
import com.serviceos.operations.api.ResolveServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.ResolveTaskFailureExceptionsCommand;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundDeliveryRecoveryHandlerTest {
    @Test
    void mapsAcknowledgedDeliveryRecoveryToUniqueTaskFailureClosureCommand() {
        CapturingExceptions exceptions = new CapturingExceptions();
        OutboundDeliveryRecoveryHandler handler =
                new OutboundDeliveryRecoveryHandler(exceptions, new ObjectMapper());
        UUID deliveryId = UUID.randomUUID();
        UUID originalTaskId = UUID.randomUUID();
        UUID replayTaskId = UUID.randomUUID();
        Instant acknowledgedAt = Instant.parse("2026-07-15T10:00:00Z");
        String payload = payload(deliveryId, replayTaskId, originalTaskId, replayTaskId, acknowledgedAt);
        OutboxMessage message = message(deliveryId, payload, acknowledgedAt);

        handler.handle(message);

        assertThat(exceptions.command.eventId()).isEqualTo(message.eventId());
        assertThat(exceptions.command.recoveryRef()).isEqualTo(deliveryId.toString());
        assertThat(exceptions.command.sourceTaskIds()).containsExactly(originalTaskId, replayTaskId);
        assertThat(exceptions.command.sourceTaskType()).isEqualTo("integration.byd.submit-review");
        assertThat(exceptions.command.recoveryType()).isEqualTo("OUTBOUND_DELIVERY_ACKNOWLEDGED");
    }

    @Test
    void rejectsDuplicateTasksAndEnvelopeIdentityMismatch() {
        OutboundDeliveryRecoveryHandler handler = new OutboundDeliveryRecoveryHandler(
                new CapturingExceptions(), new ObjectMapper());
        UUID deliveryId = UUID.randomUUID();
        UUID replayTaskId = UUID.randomUUID();
        Instant acknowledgedAt = Instant.parse("2026-07-15T10:00:00Z");
        String duplicatePayload = payload(
                deliveryId, replayTaskId, replayTaskId, replayTaskId, acknowledgedAt);

        assertThatThrownBy(() -> handler.handle(message(deliveryId, duplicatePayload, acknowledgedAt)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity mismatch");

        UUID otherDeliveryId = UUID.randomUUID();
        String validPayload = payload(
                deliveryId, replayTaskId, UUID.randomUUID(), replayTaskId, acknowledgedAt);
        assertThatThrownBy(() -> handler.handle(message(otherDeliveryId, validPayload, acknowledgedAt)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity mismatch");
    }

    private static OutboxMessage message(UUID deliveryId, String payload, Instant occurredAt) {
        return new OutboxMessage(
                UUID.randomUUID(), UUID.randomUUID(), "integration",
                "integration.outbound-delivery-recovered", 1,
                "OutboundDelivery", deliveryId.toString(), 7,
                "tenant-test", "corr-recovered", UUID.randomUUID().toString(),
                deliveryId.toString(), payload, Sha256.digest(payload), occurredAt, 1);
    }

    private static String payload(
            UUID deliveryId,
            UUID successfulTaskId,
            UUID firstTaskId,
            UUID secondTaskId,
            Instant acknowledgedAt
    ) {
        return """
                {"deliveryId":"%s","successfulExecutionTaskId":"%s",
                 "recoveredTaskIds":["%s","%s"],"acknowledgedAt":"%s"}
                """.formatted(
                deliveryId, successfulTaskId, firstTaskId, secondTaskId, acknowledgedAt);
    }

    private static final class CapturingExceptions implements OperationalExceptionService {
        private ResolveTaskFailureExceptionsCommand command;

        @Override
        public OperationalExceptionView openFromTaskFailure(OpenTaskFailureCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationalExceptionView openFromServiceAssignmentTimeout(
                OpenServiceAssignmentTimeoutCommand command
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolveServiceAssignmentTimeout(ResolveServiceAssignmentTimeoutCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolveTaskFailures(ResolveTaskFailureExceptionsCommand command) {
            this.command = command;
        }
    }
}
