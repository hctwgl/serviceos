package com.serviceos.task.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.ActivatePreparedTaskAssignmentCommand;
import com.serviceos.task.api.PrepareTaskReassignmentCommand;
import com.serviceos.task.api.TaskReassignmentReceipt;
import com.serviceos.task.api.TaskReassignmentService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * M25 Task 侧改派 Inbox 编排器。
 *
 * <p>只消费 Dispatch v2 握手事实；M24 v1 初派事实不会误入改派状态机。原命令主体在每个消费者事务中
 * 重新经过 task.reassignment.manage 授权，因此授权撤销会让消息失败关闭而不是绕过门禁。</p>
 */
@Service
final class ServiceAssignmentHandshakeHandler implements OutboxMessageHandler {
    private static final String PREPARE_CONSUMER = "task.service-assignment-pending.v2";
    private static final String ACTIVATE_CONSUMER = "task.service-assignment-activated.v2";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final TaskReassignmentService reassignments;
    private final ObjectMapper objectMapper;

    ServiceAssignmentHandshakeHandler(
            JdbcClient jdbc,
            InboxService inbox,
            TaskReassignmentService reassignments,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.reassignments = reassignments;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 2 && (
                "service.assignment.pending-activation".equals(eventType)
                        || "service.assignment.activated".equals(eventType));
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        validateEnvelope(message);
        HandshakePayload payload = read(message.payload());
        validatePayload(message, payload);
        if ("service.assignment.pending-activation".equals(message.eventType())) {
            prepare(message, payload);
        } else {
            activate(message, payload);
        }
    }

    private void prepare(OutboxMessage message, HandshakePayload payload) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), PREPARE_CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) return;
        if (!"PENDING_ACTIVATION".equals(payload.status())
                || payload.supersedesServiceAssignmentId() == null
                || payload.reasonCode() == null) {
            throw new IllegalArgumentException("v2 pending event must describe a reassignment");
        }
        long taskVersion = taskVersion(message.tenantId(), payload.taskId());
        TaskReassignmentReceipt receipt = reassignments.prepare(
                principal(message, payload.initiatedBy()), metadata(message, "prepare"),
                new PrepareTaskReassignmentCommand(
                        payload.taskId(), taskVersion, payload.sagaId().toString(),
                        payload.assigneeId(), payload.serviceAssignmentId().toString(),
                        payload.reasonCode()));
        inbox.complete(message.tenantId(), PREPARE_CONSUMER, message.eventId(), digest(receipt));
    }

    private void activate(OutboxMessage message, HandshakePayload payload) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), ACTIVATE_CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) return;
        if (!"ACTIVE".equals(payload.status())
                || payload.guardId() == null || payload.preparedTaskAssignmentId() == null) {
            throw new IllegalArgumentException("v2 activated event lacks prepared Task references");
        }
        long taskVersion = taskVersion(message.tenantId(), payload.taskId());
        TaskReassignmentReceipt receipt = reassignments.activate(
                principal(message, payload.initiatedBy()), metadata(message, "activate"),
                new ActivatePreparedTaskAssignmentCommand(
                        payload.taskId(), payload.guardId(), payload.preparedTaskAssignmentId(),
                        taskVersion, payload.serviceAssignmentId().toString()));
        inbox.complete(message.tenantId(), ACTIVATE_CONSUMER, message.eventId(), digest(receipt));
    }

    private long taskVersion(String tenantId, UUID taskId) {
        return jdbc.sql("SELECT version FROM tsk_task WHERE tenant_id = :tenantId AND task_id = :taskId")
                .param("tenantId", tenantId).param("taskId", taskId)
                .query(Long.class).single();
    }

    private HandshakePayload read(String payload) {
        try {
            return objectMapper.readValue(payload, HandshakePayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("ServiceAssignment handshake payload cannot be decoded", exception);
        }
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"dispatch".equals(message.module()) || !"ServiceAssignment".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported ServiceAssignment handshake envelope");
        }
    }

    private static void validatePayload(OutboxMessage message, HandshakePayload payload) {
        if (payload.protocolVersion() != 2
                || !payload.serviceAssignmentId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("ServiceAssignment handshake identity mismatch");
        }
    }

    private static CurrentPrincipal principal(OutboxMessage message, String initiatedBy) {
        return new CurrentPrincipal(initiatedBy, message.tenantId(),
                CurrentPrincipal.PrincipalType.USER, "dispatch-inbox",
                Set.of("task.reassignment.manage"));
    }

    private static CommandMetadata metadata(OutboxMessage message, String operation) {
        return new CommandMetadata(message.correlationId(),
                "inbox-" + operation + "-" + message.eventId());
    }

    private static String digest(TaskReassignmentReceipt receipt) {
        return Sha256.digest(receipt.taskId() + "|" + receipt.guardId() + "|"
                + receipt.preparedTaskAssignmentId() + "|" + receipt.status()
                + "|" + receipt.taskVersion());
    }

    private record HandshakePayload(
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
            String initiatedBy,
            int protocolVersion,
            Instant occurredAt
    ) {
    }
}
