package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ServiceAssignmentActivationTimedOutPayload;
import com.serviceos.dispatch.api.ServiceAssignmentTimeoutScanner;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga;
import com.serviceos.jooq.generated.tables.DspServiceAssignmentSagaTimeout;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.jooq.Record8;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga.DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
import static com.serviceos.jooq.generated.tables.DspServiceAssignmentSagaTimeout.DSP_SERVICE_ASSIGNMENT_SAGA_TIMEOUT;

/**
 * M27 激活 saga 超时协调器。
 *
 * <p>扫描只记录事实并可靠通知异常中心，不改变 saga stage/version，也不解除 Task guard。阶段推进仍可按
 * 原事件向前恢复；是否放弃或执行切换后补偿必须由明确策略或授权人工命令决定。</p>
 */
@Service
final class JooqServiceAssignmentTimeoutScanner implements ServiceAssignmentTimeoutScanner {
    private static final String ERROR_CODE = "ACTIVATION_SAGA_TIMEOUT";

    private final DSLContext dsl;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqServiceAssignmentTimeoutScanner(
            DSLContext dsl,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean detectNextTimeout() {
        Instant now = clock.instant();
        DspServiceAssignmentActivationSaga saga = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA.as("saga");
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT.as("assignment");
        DspServiceAssignmentSagaTimeout timeout = DSP_SERVICE_ASSIGNMENT_SAGA_TIMEOUT.as("timeout");
        // FOR UPDATE OF saga SKIP LOCKED：并发扫描器互相跳过已锁行；NOT EXISTS 保证
        // 同一 (saga, stage, version) 只记录一次超时事实。
        Optional<DueSaga> due = dsl.select(
                        saga.TENANT_ID,
                        saga.ACTIVATION_SAGA_ID,
                        saga.NEW_SERVICE_ASSIGNMENT_ID,
                        assignment.WORK_ORDER_ID,
                        saga.TASK_ID,
                        saga.STAGE,
                        saga.VERSION,
                        saga.DEADLINE_AT)
                .from(saga)
                .join(assignment)
                .on(assignment.SERVICE_ASSIGNMENT_ID.eq(saga.NEW_SERVICE_ASSIGNMENT_ID))
                .and(assignment.TENANT_ID.eq(saga.TENANT_ID))
                .where(saga.STAGE.notIn("COMPLETED", "ABORTED"))
                .and(saga.DEADLINE_AT.le(now))
                .andNotExists(dsl.selectOne()
                        .from(timeout)
                        .where(timeout.TENANT_ID.eq(saga.TENANT_ID))
                        .and(timeout.ACTIVATION_SAGA_ID.eq(saga.ACTIVATION_SAGA_ID))
                        .and(timeout.STAGE.eq(saga.STAGE))
                        .and(timeout.SAGA_VERSION.eq(saga.VERSION)))
                .orderBy(saga.DEADLINE_AT, saga.ACTIVATION_SAGA_ID)
                .limit(1)
                .forUpdate().of(saga).skipLocked()
                .fetchOptional(JooqServiceAssignmentTimeoutScanner::mapDueSaga);
        if (due.isEmpty()) return false;

        DueSaga row = due.orElseThrow();
        UUID timeoutId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        DspServiceAssignmentSagaTimeout timeoutTable = DSP_SERVICE_ASSIGNMENT_SAGA_TIMEOUT;
        dsl.insertInto(timeoutTable)
                .set(timeoutTable.TIMEOUT_ID, timeoutId)
                .set(timeoutTable.TENANT_ID, row.tenantId())
                .set(timeoutTable.ACTIVATION_SAGA_ID, row.sagaId())
                .set(timeoutTable.SERVICE_ASSIGNMENT_ID, row.serviceAssignmentId())
                .set(timeoutTable.STAGE, row.stage())
                .set(timeoutTable.SAGA_VERSION, row.sagaVersion())
                .set(timeoutTable.DEADLINE_AT, row.deadlineAt())
                .set(timeoutTable.DETECTED_AT, now)
                .set(timeoutTable.EVENT_ID, eventId)
                .set(timeoutTable.ERROR_CODE, ERROR_CODE)
                .execute();
        DspServiceAssignmentActivationSaga sagaTable = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;
        dsl.update(sagaTable)
                .set(sagaTable.LAST_ERROR_CODE, ERROR_CODE)
                .set(sagaTable.UPDATED_AT, now)
                .where(sagaTable.TENANT_ID.eq(row.tenantId()))
                .and(sagaTable.ACTIVATION_SAGA_ID.eq(row.sagaId()))
                .and(sagaTable.STAGE.eq(row.stage()))
                .and(sagaTable.VERSION.eq(row.sagaVersion()))
                .execute();

        ServiceAssignmentActivationTimedOutPayload payload =
                new ServiceAssignmentActivationTimedOutPayload(
                        timeoutId, row.sagaId(), row.serviceAssignmentId(), row.workOrderId(),
                        row.taskId(), row.stage(), row.sagaVersion(), row.deadlineAt(), now, ERROR_CODE);
        String json = serialize(payload);
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), eventId, "dispatch", "service.assignment.activation-timed-out", 1,
                "ServiceAssignmentActivationSaga", row.sagaId().toString(), row.sagaVersion(),
                row.tenantId(), "dispatch-saga-timeout-" + row.sagaId(),
                "timeout-scan-" + timeoutId, row.taskId().toString(), json,
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

    private static DueSaga mapDueSaga(
            Record8<String, UUID, UUID, UUID, UUID, String, Long, Instant> row) {
        return new DueSaga(
                row.value1(), row.value2(), row.value3(), row.value4(),
                row.value5(), row.value6(), row.value7(), row.value8());
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
