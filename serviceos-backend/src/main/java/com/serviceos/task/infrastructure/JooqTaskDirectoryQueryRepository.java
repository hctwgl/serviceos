package com.serviceos.task.infrastructure;

import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.jooq.generated.tables.TskTaskAssignment;
import com.serviceos.jooq.generated.tables.TskTaskExecutionAttempt;
import com.serviceos.jooq.generated.tables.TskTaskExecutionGuard;
import com.serviceos.task.api.InputVersionRef;
import com.serviceos.task.api.TaskDetail;
import com.serviceos.task.api.TaskDirectoryItem;
import com.serviceos.task.api.TaskExecutionAttemptView;
import com.serviceos.task.api.TaskTimelineContext;
import com.serviceos.task.application.TaskDirectoryQueryRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionAttempt.TSK_TASK_EXECUTION_ATTEMPT;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionGuard.TSK_TASK_EXECUTION_GUARD;

@Repository
final class JooqTaskDirectoryQueryRepository implements TaskDirectoryQueryRepository {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final DSLContext dsl;

    JooqTaskDirectoryQueryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<TaskDirectoryItem> findPage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            UUID projectId,
            String taskKind,
            String status,
            String assigneeId,
            Integer cursorPriority,
            Instant cursorNextRunAt,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize
    ) {
        TskTask task = TSK_TASK;
        Condition condition = task.TENANT_ID.eq(tenantId);
        if (!tenantWide) {
            // 非全租户视角且项目集合为空时与原 XML 的 AND FALSE 一致，直接无结果。
            condition = condition.and(projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : task.PROJECT_ID.in(projectIds));
        }
        if (projectId != null) {
            condition = condition.and(task.PROJECT_ID.eq(projectId));
        }
        if (taskKind != null) {
            condition = condition.and(task.TASK_KIND.eq(taskKind));
        }
        if (status != null) {
            condition = condition.and(task.STATUS.eq(status));
        }
        if (assigneeId != null) {
            TskTaskAssignment a = TSK_TASK_ASSIGNMENT.as("a");
            condition = condition.and(DSL.exists(DSL.selectOne()
                    .from(a)
                    .where(a.TENANT_ID.eq(task.TENANT_ID))
                    .and(a.TASK_ID.eq(task.TASK_ID))
                    .and(a.PRINCIPAL_TYPE.eq("USER"))
                    .and(a.PRINCIPAL_ID.eq(assigneeId))
                    .and(a.STATUS.eq("ACTIVE"))));
        }
        if (cursorPriority != null) {
            // 与原游标语义一致：优先级更靠后，或同优先级时 (next_run_at, created_at, task_id) 严格递增。
            condition = condition.and(task.PRIORITY.lt(cursorPriority)
                    .or(task.PRIORITY.eq(cursorPriority)
                            .and(DSL.row(task.NEXT_RUN_AT, task.CREATED_AT, task.TASK_ID)
                                    .gt(cursorNextRunAt, cursorCreatedAt, cursorId))));
        }
        return dsl.select(itemColumns())
                .from(task)
                .where(condition)
                .orderBy(task.PRIORITY.desc(), task.NEXT_RUN_AT, task.CREATED_AT, task.TASK_ID)
                .limit(fetchSize)
                .fetch(JooqTaskDirectoryQueryRepository::item);
    }

    @Override
    public Optional<TaskDetail> findDetail(String tenantId, UUID taskId, Instant asOf) {
        TskTask task = TSK_TASK;
        TskTaskAssignment responsible = TSK_TASK_ASSIGNMENT.as("a_responsible");
        TskTaskAssignment candidate = TSK_TASK_ASSIGNMENT.as("a_candidate");
        Field<String> inputVersionRefs = DSL
                .coalesce(task.INPUT_VERSION_REFS, DSL.val("[]", task.INPUT_VERSION_REFS))
                .as("input_version_refs");
        Field<String> responsibleUserId = DSL.field(dsl.select(responsible.PRINCIPAL_ID)
                        .from(responsible)
                        .where(responsible.TENANT_ID.eq(task.TENANT_ID))
                        .and(responsible.TASK_ID.eq(task.TASK_ID))
                        .and(responsible.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(responsible.STATUS.eq("ACTIVE"))
                        .orderBy(responsible.EFFECTIVE_FROM.desc())
                        .limit(1))
                .as("responsible_user_id");
        // 原 XML 用 jsonb_agg 产出候选人 JSON 数组；array_agg 同序等价，Java 侧直接得到列表。
        Field<String[]> candidateUserIds = DSL.field(dsl
                        .select(DSL.arrayAgg(candidate.PRINCIPAL_ID).orderBy(candidate.PRINCIPAL_ID))
                        .from(candidate)
                        .where(candidate.TENANT_ID.eq(task.TENANT_ID))
                        .and(candidate.TASK_ID.eq(task.TASK_ID))
                        .and(candidate.ASSIGNMENT_KIND.eq("CANDIDATE"))
                        .and(candidate.STATUS.eq("ACTIVE")))
                .as("candidate_user_ids");
        List<SelectField<?>> columns = new java.util.ArrayList<>(itemColumns());
        columns.add(task.WORKFLOW_INSTANCE_ID);
        columns.add(task.STAGE_INSTANCE_ID);
        columns.add(task.WORKFLOW_NODE_INSTANCE_ID);
        columns.add(task.WORKFLOW_NODE_ID);
        columns.add(task.WORKFLOW_DEFINITION_VERSION_ID);
        columns.add(task.WORKFLOW_DEFINITION_DIGEST);
        columns.add(task.CONFIGURATION_BUNDLE_ID);
        columns.add(task.CONFIGURATION_BUNDLE_DIGEST);
        columns.add(task.FORM_REF);
        columns.add(task.CLAIMED_AT);
        columns.add(task.STARTED_AT);
        columns.add(task.COMPLETED_AT);
        columns.add(task.RESULT_REF);
        columns.add(task.RESULT_DIGEST);
        columns.add(inputVersionRefs);
        columns.add(responsibleUserId);
        columns.add(candidateUserIds);
        return dsl.select(columns)
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .fetchOptional(record -> detail(record, inputVersionRefs, responsibleUserId,
                        candidateUserIds, asOf));
    }

    @Override
    public Optional<AllowedActionState> findAllowedActionState(
            String tenantId, UUID taskId, String principalId) {
        TskTask task = TSK_TASK;
        TskTaskAssignment candidate = TSK_TASK_ASSIGNMENT.as("a_candidate");
        TskTaskAssignment responsible = TSK_TASK_ASSIGNMENT.as("a_responsible");
        TskTaskExecutionGuard guard = TSK_TASK_EXECUTION_GUARD.as("g");
        Field<Boolean> actorCandidate = DSL.exists(DSL.selectOne()
                        .from(candidate)
                        .where(candidate.TENANT_ID.eq(task.TENANT_ID))
                        .and(candidate.TASK_ID.eq(task.TASK_ID))
                        .and(candidate.ASSIGNMENT_KIND.eq("CANDIDATE"))
                        .and(candidate.PRINCIPAL_TYPE.eq("USER"))
                        .and(candidate.PRINCIPAL_ID.eq(principalId))
                        .and(candidate.STATUS.eq("ACTIVE")))
                .as("actor_candidate");
        Field<Boolean> actorResponsible = DSL.exists(DSL.selectOne()
                        .from(responsible)
                        .where(responsible.TENANT_ID.eq(task.TENANT_ID))
                        .and(responsible.TASK_ID.eq(task.TASK_ID))
                        .and(responsible.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(responsible.PRINCIPAL_TYPE.eq("USER"))
                        .and(responsible.PRINCIPAL_ID.eq(principalId))
                        .and(responsible.STATUS.eq("ACTIVE")))
                .as("actor_responsible");
        Field<Boolean> activeGuard = DSL.exists(DSL.selectOne()
                        .from(guard)
                        .where(guard.TENANT_ID.eq(task.TENANT_ID))
                        .and(guard.TASK_ID.eq(task.TASK_ID))
                        .and(guard.STATUS.eq("ACTIVE")))
                .as("active_guard");
        return dsl.select(task.TASK_KIND, task.STATUS, task.VERSION, task.CLAIMED_BY,
                        task.WORKFLOW_NODE_INSTANCE_ID, actorCandidate, actorResponsible, activeGuard)
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .fetchOptional(record -> new AllowedActionState(
                        record.get(task.TASK_KIND), record.get(task.STATUS), record.get(task.VERSION),
                        record.get(task.CLAIMED_BY), record.get(task.WORKFLOW_NODE_INSTANCE_ID),
                        record.get(actorCandidate), record.get(actorResponsible),
                        record.get(activeGuard)));
    }

    @Override
    public List<TaskExecutionAttemptView> findExecutionAttempts(
            String tenantId,
            UUID taskId,
            Integer beforeAttemptNo,
            int fetchSize
    ) {
        TskTaskExecutionAttempt a = TSK_TASK_EXECUTION_ATTEMPT.as("a");
        TskTask t = TSK_TASK.as("t");
        Condition condition = a.TASK_ID.eq(taskId);
        if (beforeAttemptNo != null) {
            condition = condition.and(a.ATTEMPT_NO.lt(beforeAttemptNo));
        }
        return dsl.select(a.ATTEMPT_ID, a.ATTEMPT_NO, a.RESULT_CODE, a.ERROR_CODE, a.RESULT_REF,
                        a.NEXT_RETRY_AT, a.STARTED_AT, a.FINISHED_AT)
                .from(a)
                .join(t).on(t.TASK_ID.eq(a.TASK_ID)).and(t.TENANT_ID.eq(tenantId))
                .where(condition)
                .orderBy(a.ATTEMPT_NO.desc())
                .limit(fetchSize)
                .fetch(record -> new TaskExecutionAttemptView(
                        record.get(a.ATTEMPT_ID), record.get(a.ATTEMPT_NO), record.get(a.RESULT_CODE),
                        record.get(a.ERROR_CODE), record.get(a.RESULT_REF), record.get(a.NEXT_RETRY_AT),
                        record.get(a.STARTED_AT), record.get(a.FINISHED_AT)));
    }

    @Override
    public Optional<TaskTimelineContext> findTimelineContext(String tenantId, UUID taskId) {
        TskTask task = TSK_TASK;
        return dsl.select(task.TASK_ID, task.PROJECT_ID, task.WORK_ORDER_ID,
                        task.TASK_TYPE, task.TASK_KIND)
                .from(task)
                .where(task.TENANT_ID.eq(tenantId))
                .and(task.TASK_ID.eq(taskId))
                .fetchOptional(record -> new TaskTimelineContext(
                        record.get(task.TASK_ID), record.get(task.PROJECT_ID),
                        record.get(task.WORK_ORDER_ID), record.get(task.TASK_TYPE),
                        record.get(task.TASK_KIND)));
    }

    private static List<SelectField<?>> itemColumns() {
        TskTask task = TSK_TASK;
        return List.of(task.TASK_ID, task.PROJECT_ID, task.WORK_ORDER_ID, task.TASK_TYPE,
                task.TASK_KIND, task.STAGE_CODE, task.PRIORITY, task.STATUS, task.NEXT_RUN_AT,
                task.CLAIMED_BY, task.ATTEMPT_COUNT, task.MAX_ATTEMPTS, task.VERSION,
                task.CREATED_AT, task.UPDATED_AT);
    }

    private static TaskDirectoryItem item(Record record) {
        TskTask task = TSK_TASK;
        return new TaskDirectoryItem(
                record.get(task.TASK_ID), record.get(task.PROJECT_ID), record.get(task.WORK_ORDER_ID),
                record.get(task.TASK_TYPE), record.get(task.TASK_KIND), record.get(task.STAGE_CODE),
                record.get(task.PRIORITY), record.get(task.STATUS), record.get(task.NEXT_RUN_AT),
                record.get(task.CLAIMED_BY), record.get(task.ATTEMPT_COUNT),
                record.get(task.MAX_ATTEMPTS), record.get(task.VERSION),
                record.get(task.CREATED_AT), record.get(task.UPDATED_AT));
    }

    private static TaskDetail detail(
            Record record,
            Field<String> inputVersionRefs,
            Field<String> responsibleUserId,
            Field<String[]> candidateUserIds,
            Instant asOf
    ) {
        TskTask task = TSK_TASK;
        String[] candidates = record.get(candidateUserIds);
        return new TaskDetail(
                item(record),
                record.get(task.WORKFLOW_INSTANCE_ID),
                record.get(task.STAGE_INSTANCE_ID),
                record.get(task.WORKFLOW_NODE_INSTANCE_ID),
                record.get(task.WORKFLOW_NODE_ID),
                record.get(task.WORKFLOW_DEFINITION_VERSION_ID),
                record.get(task.WORKFLOW_DEFINITION_DIGEST),
                record.get(task.CONFIGURATION_BUNDLE_ID),
                record.get(task.CONFIGURATION_BUNDLE_DIGEST),
                record.get(task.FORM_REF),
                record.get(responsibleUserId),
                candidates == null ? List.of() : List.of(candidates),
                record.get(task.CLAIMED_AT),
                record.get(task.STARTED_AT),
                record.get(task.COMPLETED_AT),
                record.get(task.RESULT_REF),
                record.get(task.RESULT_DIGEST),
                inputVersionRefs(record.get(inputVersionRefs)),
                asOf);
    }

    private static List<InputVersionRef> inputVersionRefs(String json) {
        try {
            return List.of(JSON.readValue(json, InputVersionRef[].class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("任务详情包含非法输入版本 JSON", exception);
        }
    }
}
