package com.serviceos.operations.application;

import com.serviceos.operations.api.OpenServiceAssignmentTimeoutCommand;
import com.serviceos.operations.api.OpenTaskFailureCommand;
import com.serviceos.operations.api.OperationalExceptionService;
import com.serviceos.operations.api.OperationalExceptionView;
import com.serviceos.operations.api.ResolveServiceAssignmentTimeoutCommand;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceAssignmentRecoveryHandlerTest {
    @Test
    void mapsOnlyTerminalActivationCompletionToRecoveryCommand() {
        CapturingExceptions exceptions = new CapturingExceptions();
        ServiceAssignmentRecoveryHandler handler =
                new ServiceAssignmentRecoveryHandler(exceptions, new ObjectMapper());
        UUID eventId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        String payload = payload(assignmentId, sagaId, workOrderId, taskId);

        handler.handle(message(eventId, assignmentId, payload));

        assertThat(exceptions.recovery.eventId()).isEqualTo(eventId);
        assertThat(exceptions.recovery.sagaId()).isEqualTo(sagaId);
        assertThat(exceptions.recovery.serviceAssignmentId()).isEqualTo(assignmentId);
        assertThat(exceptions.recovery.sagaVersion()).isEqualTo(4);
    }

    @Test
    void rejectsEnvelopeAndPayloadIdentityMismatch() {
        ServiceAssignmentRecoveryHandler handler = new ServiceAssignmentRecoveryHandler(
                new CapturingExceptions(), new ObjectMapper());
        String payload = payload(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> handler.handle(
                message(UUID.randomUUID(), UUID.randomUUID(), payload)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity mismatch");
    }

    private static OutboxMessage message(UUID eventId, UUID assignmentId, String payload) {
        return new OutboxMessage(
                UUID.randomUUID(), eventId, "dispatch", "service.assignment.activation-completed", 1,
                "ServiceAssignment", assignmentId.toString(), 4, "tenant-test", "corr-recovery",
                "cause", "partition", payload, Sha256.digest(payload),
                Instant.parse("2026-07-14T02:00:00Z"), 1);
    }

    private static String payload(UUID assignmentId, UUID sagaId, UUID workOrderId, UUID taskId) {
        return """
                {"serviceAssignmentId":"%s","sagaId":"%s","workOrderId":"%s","taskId":"%s",
                 "responsibilityLevel":"TECHNICIAN","assigneeId":"technician-2",
                 "businessType":"SITE_SURVEY","status":"ACTIVE",
                 "supersedesServiceAssignmentId":null,
                 "capacityReservationId":"%s","guardId":"%s","preparedTaskAssignmentId":"%s",
                 "reasonCode":"TASK_ASSIGNMENT_ACTIVATED","occurredAt":"2026-07-14T02:00:00Z"}
                """.formatted(
                assignmentId, sagaId, workOrderId, taskId,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static final class CapturingExceptions implements OperationalExceptionService {
        private ResolveServiceAssignmentTimeoutCommand recovery;

        @Override
        public OperationalExceptionView openFromTaskFailure(OpenTaskFailureCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationalExceptionView openFromServiceAssignmentTimeout(
                OpenServiceAssignmentTimeoutCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolveServiceAssignmentTimeout(ResolveServiceAssignmentTimeoutCommand command) {
            recovery = command;
        }
    }
}
