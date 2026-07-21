package com.serviceos.operations.infrastructure;

import com.serviceos.jooq.generated.tables.OpsExceptionAckResult;
import com.serviceos.jooq.generated.tables.OpsOperationalException;
import com.serviceos.operations.api.OperationalExceptionAcknowledgement;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.application.OperationalExceptionWorkbenchRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.OpsExceptionAckResult.OPS_EXCEPTION_ACK_RESULT;
import static com.serviceos.jooq.generated.tables.OpsOperationalException.OPS_OPERATIONAL_EXCEPTION;

/**
 * 运营异常工作台查询的 jOOQ 实现。范围（tenant/project scope）与稳定游标在 SQL 中收敛，
 * 与原 MyBatis 动态查询逐条等价：空项目集合退化为 AND FALSE，游标为 (opened_at,exception_id) 行值比较。
 */
@Repository
final class JooqOperationalExceptionWorkbenchRepository
        implements OperationalExceptionWorkbenchRepository {
    private static final OpsOperationalException E = OPS_OPERATIONAL_EXCEPTION;
    private static final List<SelectField<?>> ITEM_FIELDS = List.of(
            E.EXCEPTION_ID, E.PROJECT_ID, E.SOURCE_TYPE, E.SOURCE_ID, E.SOURCE_ATTEMPT_ID,
            E.SOURCE_TASK_TYPE, E.CATEGORY_CODE, E.SEVERITY_CODE, E.ERROR_CODE, E.STATUS,
            E.WORK_ORDER_ID, E.TASK_ID, E.HANDLING_TASK_ID, E.OCCURRENCE_COUNT,
            E.AGGREGATE_VERSION, E.OPENED_AT, E.LAST_DETECTED_AT, E.ACKNOWLEDGED_AT,
            E.ACKNOWLEDGED_BY, E.ACKNOWLEDGEMENT_NOTE, E.RESOLVED_AT, E.RESOLUTION_CODE);

    private final DSLContext dsl;

    JooqOperationalExceptionWorkbenchRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<OperationalExceptionItem> findPage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            UUID projectId,
            String status,
            String category,
            String severity,
            UUID workOrderId,
            UUID taskId,
            Instant cursorOpenedAt,
            UUID cursorId,
            int fetchSize
    ) {
        Condition condition = E.TENANT_ID.eq(tenantId);
        if (!tenantWide) {
            // 非全租户视角且无任何授权项目时结果必须为空（AND FALSE），不得退化为全量。
            condition = condition.and(projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : E.PROJECT_ID.in(projectIds));
        }
        if (projectId != null) {
            condition = condition.and(E.PROJECT_ID.eq(projectId));
        }
        if (status != null) {
            condition = condition.and(E.STATUS.eq(status));
        }
        if (category != null) {
            condition = condition.and(E.CATEGORY_CODE.eq(category));
        }
        if (severity != null) {
            condition = condition.and(E.SEVERITY_CODE.eq(severity));
        }
        if (workOrderId != null) {
            condition = condition.and(E.WORK_ORDER_ID.eq(workOrderId));
        }
        if (taskId != null) {
            condition = condition.and(E.TASK_ID.eq(taskId));
        }
        if (cursorOpenedAt != null) {
            condition = condition.and(DSL.row(E.OPENED_AT, E.EXCEPTION_ID).lt(cursorOpenedAt, cursorId));
        }
        return dsl.select(ITEM_FIELDS)
                .from(E)
                .where(condition)
                .orderBy(E.OPENED_AT.desc(), E.EXCEPTION_ID.desc())
                .limit(fetchSize)
                .fetch(JooqOperationalExceptionWorkbenchRepository::item);
    }

    @Override
    public Optional<OperationalExceptionItem> findById(String tenantId, UUID exceptionId) {
        return dsl.select(ITEM_FIELDS)
                .from(E)
                .where(E.TENANT_ID.eq(tenantId))
                .and(E.EXCEPTION_ID.eq(exceptionId))
                .fetchOptional(JooqOperationalExceptionWorkbenchRepository::item);
    }

    @Override
    public List<OperationalExceptionItem> listByTask(String tenantId, UUID taskId) {
        return dsl.select(ITEM_FIELDS)
                .from(E)
                .where(E.TENANT_ID.eq(tenantId))
                .and(E.TASK_ID.eq(taskId))
                .orderBy(E.OPENED_AT.asc(), E.EXCEPTION_ID.asc())
                .fetch(JooqOperationalExceptionWorkbenchRepository::item);
    }

    @Override
    public boolean acknowledge(
            String tenantId, UUID exceptionId, long expectedVersion,
            String actorId, String note, Instant acknowledgedAt
    ) {
        // 状态与版本双条件防止并发确认互相覆盖；影响行数即是否抢到确认权。
        return dsl.update(E)
                .set(E.STATUS, "ACKNOWLEDGED")
                .set(E.ACKNOWLEDGED_AT, acknowledgedAt)
                .set(E.ACKNOWLEDGED_BY, actorId)
                .set(E.ACKNOWLEDGEMENT_NOTE, note)
                .set(E.AGGREGATE_VERSION, E.AGGREGATE_VERSION.plus(1))
                .where(E.TENANT_ID.eq(tenantId))
                .and(E.EXCEPTION_ID.eq(exceptionId))
                .and(E.STATUS.eq("OPEN"))
                .and(E.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public void saveAcknowledgement(
            String tenantId, String idempotencyKey,
            OperationalExceptionAcknowledgement acknowledgement
    ) {
        OpsExceptionAckResult ack = OPS_EXCEPTION_ACK_RESULT;
        dsl.insertInto(ack)
                .set(ack.TENANT_ID, tenantId)
                .set(ack.IDEMPOTENCY_KEY, idempotencyKey)
                .set(ack.EXCEPTION_ID, acknowledgement.exceptionId())
                .set(ack.AGGREGATE_VERSION, acknowledgement.aggregateVersion())
                .set(ack.ACKNOWLEDGED_AT, acknowledgement.acknowledgedAt())
                .set(ack.ACKNOWLEDGED_BY, acknowledgement.acknowledgedBy())
                .execute();
    }

    @Override
    public OperationalExceptionAcknowledgement findAcknowledgement(String tenantId, String idempotencyKey) {
        OpsExceptionAckResult ack = OPS_EXCEPTION_ACK_RESULT;
        return dsl.select(ack.EXCEPTION_ID, ack.AGGREGATE_VERSION, ack.ACKNOWLEDGED_AT, ack.ACKNOWLEDGED_BY)
                .from(ack)
                .where(ack.TENANT_ID.eq(tenantId))
                .and(ack.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(row -> new OperationalExceptionAcknowledgement(
                        row.get(ack.EXCEPTION_ID), "ACKNOWLEDGED", row.get(ack.AGGREGATE_VERSION),
                        row.get(ack.ACKNOWLEDGED_AT), row.get(ack.ACKNOWLEDGED_BY)))
                .orElseThrow(() -> new IllegalStateException("Frozen acknowledgement result is missing"));
    }

    private static OperationalExceptionItem item(Record row) {
        return new OperationalExceptionItem(
                row.get(E.EXCEPTION_ID), row.get(E.PROJECT_ID),
                row.get(E.SOURCE_TYPE), row.get(E.SOURCE_ID),
                row.get(E.SOURCE_ATTEMPT_ID), row.get(E.SOURCE_TASK_TYPE),
                row.get(E.CATEGORY_CODE), row.get(E.SEVERITY_CODE), row.get(E.ERROR_CODE),
                row.get(E.STATUS),
                row.get(E.WORK_ORDER_ID), row.get(E.TASK_ID), row.get(E.HANDLING_TASK_ID),
                row.get(E.OCCURRENCE_COUNT).longValue(), row.get(E.AGGREGATE_VERSION),
                row.get(E.OPENED_AT), row.get(E.LAST_DETECTED_AT),
                row.get(E.ACKNOWLEDGED_AT), row.get(E.ACKNOWLEDGED_BY),
                row.get(E.ACKNOWLEDGEMENT_NOTE), row.get(E.RESOLVED_AT),
                row.get(E.RESOLUTION_CODE), List.of());
    }
}
