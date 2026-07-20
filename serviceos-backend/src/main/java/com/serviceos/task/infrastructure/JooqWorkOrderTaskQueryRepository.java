package com.serviceos.task.infrastructure;

import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.task.api.WorkOrderTaskSummary;
import com.serviceos.task.application.WorkOrderTaskQueryRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;

@Repository
final class JooqWorkOrderTaskQueryRepository implements WorkOrderTaskQueryRepository {
    private final DSLContext dsl;

    JooqWorkOrderTaskQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<WorkOrderTaskSummary> findPage(
            String tenantId, UUID workOrderId, Instant cursorCreatedAt, UUID cursorId, int fetchSize) {
        TskTask task = TSK_TASK;
        Condition condition = task.TENANT_ID.eq(tenantId).and(task.WORK_ORDER_ID.eq(workOrderId));
        if (cursorCreatedAt != null) {
            condition = condition.and(
                    DSL.row(task.CREATED_AT, task.TASK_ID).gt(cursorCreatedAt, cursorId));
        }
        return dsl.select(task.TASK_ID, task.PROJECT_ID, task.WORK_ORDER_ID, task.WORKFLOW_INSTANCE_ID,
                        task.STAGE_INSTANCE_ID, task.WORKFLOW_NODE_INSTANCE_ID, task.WORKFLOW_NODE_ID,
                        task.STAGE_CODE, task.TASK_TYPE, task.TASK_KIND, task.PRIORITY, task.STATUS,
                        task.NEXT_RUN_AT, task.CLAIMED_BY, task.CLAIMED_AT, task.STARTED_AT,
                        task.COMPLETED_AT, task.VERSION, task.CREATED_AT, task.UPDATED_AT)
                .from(task)
                .where(condition)
                .orderBy(task.CREATED_AT, task.TASK_ID)
                .limit(fetchSize)
                .fetch(JooqWorkOrderTaskQueryRepository::view);
    }

    private static WorkOrderTaskSummary view(Record record) {
        TskTask task = TSK_TASK;
        return new WorkOrderTaskSummary(
                record.get(task.TASK_ID), record.get(task.PROJECT_ID), record.get(task.WORK_ORDER_ID),
                record.get(task.WORKFLOW_INSTANCE_ID), record.get(task.STAGE_INSTANCE_ID),
                record.get(task.WORKFLOW_NODE_INSTANCE_ID), record.get(task.WORKFLOW_NODE_ID),
                record.get(task.STAGE_CODE), record.get(task.TASK_TYPE), record.get(task.TASK_KIND),
                record.get(task.PRIORITY), record.get(task.STATUS), record.get(task.NEXT_RUN_AT),
                record.get(task.CLAIMED_BY), record.get(task.CLAIMED_AT), record.get(task.STARTED_AT),
                record.get(task.COMPLETED_AT), record.get(task.VERSION), record.get(task.CREATED_AT),
                record.get(task.UPDATED_AT));
    }
}
