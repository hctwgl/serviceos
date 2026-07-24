package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 工单终态后结束整单服务责任并释放容量。
 *
 * <p>事务边界：Inbox begin → 按 counter/assignment 稳定顺序锁定 ACTIVE 责任及 CONFIRMED
 * reservation → 结束责任 → 释放 reservation → 归还 capacity → 审计 → Inbox complete。
 * Inbox 键为终态事件 ID；任一步并发条件不满足均整体回滚并重试，绝不留下“责任已结束但容量仍占用”
 * 或相反的半完成状态。</p>
 */
@Service
final class WorkOrderTerminalResponsibilityHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "dispatch.work-order-terminal-responsibility.v1";
    private static final String ACTOR = "system:dispatch-lifecycle";

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final AuditAppender audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkOrderTerminalResponsibilityHandler(
            JdbcClient jdbc,
            InboxService inbox,
            AuditAppender audit,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.inbox = inbox;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return schemaVersion == 1
                && ("workorder.fulfilled".equals(eventType)
                || "workorder.cancelled".equals(eventType));
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        if (!"workorder".equals(message.module()) || !"WorkOrder".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported WorkOrder terminal responsibility envelope");
        }
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }
        TerminalPayload payload = read(message.payload());
        if (payload.workOrderId() == null
                || !payload.workOrderId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("WorkOrder terminal responsibility identity mismatch");
        }

        Instant now = clock.instant();
        String reasonCode = "workorder.fulfilled".equals(message.eventType())
                ? "WORK_ORDER_FULFILLED"
                : "WORK_ORDER_CANCELLED";
        List<AssignmentReservation> active = lockActive(
                message.tenantId(), payload.workOrderId());
        for (AssignmentReservation row : active) {
            endAssignment(message, row, reasonCode, now);
        }

        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), ACTOR,
                "WORK_ORDER_SERVICE_RESPONSIBILITY_ENDED", "dispatch.assignment.manage",
                "WorkOrder", payload.workOrderId().toString(),
                "ALLOW", List.of(), "work-order-terminal-v1", reasonCode, null,
                Sha256.digest(payload.workOrderId() + "|" + reasonCode + "|" + active.size()),
                message.correlationId(), now));
        inbox.complete(
                message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(payload.workOrderId() + "|" + reasonCode + "|" + active.size()));
    }

    private List<AssignmentReservation> lockActive(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT assignment.service_assignment_id AS "serviceAssignmentId",
                               reservation.capacity_reservation_id AS "reservationId",
                               reservation.capacity_counter_id AS "counterId",
                               reservation.units
                          FROM dsp_service_assignment assignment
                          JOIN dsp_capacity_reservation reservation
                            ON reservation.tenant_id = assignment.tenant_id
                           AND reservation.service_assignment_id = assignment.service_assignment_id
                          JOIN dsp_capacity_counter counter
                            ON counter.tenant_id = reservation.tenant_id
                           AND counter.capacity_counter_id = reservation.capacity_counter_id
                         WHERE assignment.tenant_id = :tenantId
                           AND assignment.work_order_id = :workOrderId
                           AND assignment.status = 'ACTIVE'
                           AND reservation.status = 'CONFIRMED'
                         ORDER BY reservation.capacity_counter_id, assignment.service_assignment_id
                         FOR UPDATE OF assignment, reservation, counter
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query(AssignmentReservation.class)
                .list();
    }

    private void endAssignment(
            OutboxMessage message,
            AssignmentReservation row,
            String reasonCode,
            Instant now
    ) {
        int assignmentUpdated = jdbc.sql("""
                        UPDATE dsp_service_assignment
                           SET status = 'ENDED', effective_to = :now,
                               ended_by = :actor, end_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId
                           AND service_assignment_id = :assignmentId
                           AND status = 'ACTIVE'
                        """)
                .param("now", Timestamp.from(now))
                .param("actor", ACTOR)
                .param("reasonCode", reasonCode)
                .param("tenantId", message.tenantId())
                .param("assignmentId", row.serviceAssignmentId())
                .update();
        int reservationUpdated = jdbc.sql("""
                        UPDATE dsp_capacity_reservation
                           SET status = 'RELEASED', released_at = :now,
                               released_by = :actor, release_reason_code = :reasonCode
                         WHERE tenant_id = :tenantId
                           AND capacity_reservation_id = :reservationId
                           AND status = 'CONFIRMED'
                        """)
                .param("now", Timestamp.from(now))
                .param("actor", ACTOR)
                .param("reasonCode", reasonCode)
                .param("tenantId", message.tenantId())
                .param("reservationId", row.reservationId())
                .update();
        int counterUpdated = jdbc.sql("""
                        UPDATE dsp_capacity_counter
                           SET occupied_units = occupied_units - :units,
                               version = version + 1,
                               updated_by = :actor, updated_at = :now
                         WHERE tenant_id = :tenantId
                           AND capacity_counter_id = :counterId
                           AND occupied_units >= :units
                        """)
                .param("units", row.units())
                .param("actor", ACTOR)
                .param("now", Timestamp.from(now))
                .param("tenantId", message.tenantId())
                .param("counterId", row.counterId())
                .update();
        if (assignmentUpdated != 1 || reservationUpdated != 1 || counterUpdated != 1) {
            throw new IllegalStateException(
                    "WorkOrder terminal responsibility release lost concurrent state");
        }
    }

    private TerminalPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, TerminalPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException(
                    "WorkOrder terminal responsibility payload cannot be decoded", exception);
        }
    }

    private record TerminalPayload(UUID workOrderId) {
    }

    private record AssignmentReservation(
            UUID serviceAssignmentId,
            UUID reservationId,
            UUID counterId,
            int units
    ) {
    }
}
