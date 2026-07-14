package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ServiceAssignmentActivationTimedOutPayload;
import com.serviceos.dispatch.api.ServiceAssignmentTimeoutScanner;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * M27 激活 saga 超时协调器。
 *
 * <p>扫描只记录事实并可靠通知异常中心，不改变 saga stage/version，也不解除 Task guard。阶段推进仍可按
 * 原事件向前恢复；是否放弃或执行切换后补偿必须由明确策略或授权人工命令决定。</p>
 */
@Service
final class DefaultServiceAssignmentTimeoutScanner implements ServiceAssignmentTimeoutScanner {
    private static final String ERROR_CODE = "ACTIVATION_SAGA_TIMEOUT";

    private final JdbcClient jdbc;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultServiceAssignmentTimeoutScanner(
            JdbcClient jdbc,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean detectNextTimeout() {
        Instant now = clock.instant();
        Optional<DueSaga> due = jdbc.sql("""
                        SELECT saga.tenant_id, saga.activation_saga_id AS saga_id,
                               saga.new_service_assignment_id AS service_assignment_id,
                               assignment.work_order_id, saga.task_id, saga.stage,
                               saga.version AS saga_version, saga.deadline_at
                          FROM dsp_service_assignment_activation_saga saga
                          JOIN dsp_service_assignment assignment
                            ON assignment.service_assignment_id = saga.new_service_assignment_id
                           AND assignment.tenant_id = saga.tenant_id
                         WHERE saga.stage NOT IN ('COMPLETED', 'ABORTED')
                           AND saga.deadline_at <= :now
                           AND NOT EXISTS (
                               SELECT 1 FROM dsp_service_assignment_saga_timeout timeout
                                WHERE timeout.tenant_id = saga.tenant_id
                                  AND timeout.activation_saga_id = saga.activation_saga_id
                                  AND timeout.stage = saga.stage
                                  AND timeout.saga_version = saga.version
                           )
                         ORDER BY saga.deadline_at, saga.activation_saga_id
                         LIMIT 1
                         FOR UPDATE OF saga SKIP LOCKED
                        """)
                .param("now", timestamptz(now))
                .query(DueSaga.class).optional();
        if (due.isEmpty()) return false;

        DueSaga saga = due.orElseThrow();
        UUID timeoutId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO dsp_service_assignment_saga_timeout (
                            timeout_id, tenant_id, activation_saga_id, service_assignment_id,
                            stage, saga_version, deadline_at, detected_at, event_id, error_code
                        ) VALUES (
                            :timeoutId, :tenantId, :sagaId, :assignmentId,
                            :stage, :sagaVersion, :deadlineAt, :detectedAt, :eventId, :errorCode
                        )
                        """)
                .param("timeoutId", timeoutId).param("tenantId", saga.tenantId())
                .param("sagaId", saga.sagaId()).param("assignmentId", saga.serviceAssignmentId())
                .param("stage", saga.stage()).param("sagaVersion", saga.sagaVersion())
                .param("deadlineAt", timestamptz(saga.deadlineAt()))
                .param("detectedAt", timestamptz(now)).param("eventId", eventId)
                .param("errorCode", ERROR_CODE).update();
        jdbc.sql("""
                        UPDATE dsp_service_assignment_activation_saga
                           SET last_error_code = :errorCode, updated_at = :detectedAt
                         WHERE tenant_id = :tenantId AND activation_saga_id = :sagaId
                           AND stage = :stage AND version = :sagaVersion
                        """)
                .param("errorCode", ERROR_CODE).param("detectedAt", timestamptz(now))
                .param("tenantId", saga.tenantId()).param("sagaId", saga.sagaId())
                .param("stage", saga.stage()).param("sagaVersion", saga.sagaVersion()).update();

        ServiceAssignmentActivationTimedOutPayload payload =
                new ServiceAssignmentActivationTimedOutPayload(
                        timeoutId, saga.sagaId(), saga.serviceAssignmentId(), saga.workOrderId(),
                        saga.taskId(), saga.stage(), saga.sagaVersion(), saga.deadlineAt(), now, ERROR_CODE);
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), eventId, "dispatch", "service.assignment.activation-timed-out", 1,
                "ServiceAssignmentActivationSaga", saga.sagaId().toString(), saga.sagaVersion(),
                saga.tenantId(), "dispatch-saga-timeout-" + saga.sagaId(),
                "timeout-scan-" + timeoutId, saga.taskId().toString(), json,
                Sha256.digest(json), now));
        return true;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("ServiceAssignment timeout payload cannot be serialized", exception);
        }
    }

    private record DueSaga(
            String tenantId,
            UUID sagaId,
            UUID serviceAssignmentId,
            UUID workOrderId,
            UUID taskId,
            String stage,
            long sagaVersion,
            Instant deadlineAt
    ) {
    }
}
