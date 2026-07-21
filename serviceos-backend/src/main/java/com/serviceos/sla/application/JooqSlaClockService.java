package com.serviceos.sla.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.jooq.generated.tables.SlaInstance;
import com.serviceos.jooq.generated.tables.SlaMilestone;
import com.serviceos.jooq.generated.tables.TskTask;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.Sha256;
import com.serviceos.sla.api.SlaClockService;
import com.serviceos.sla.api.SlaInstanceView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.SlaClockSegment.SLA_CLOCK_SEGMENT;
import static com.serviceos.jooq.generated.tables.SlaInstance.SLA_INSTANCE;
import static com.serviceos.jooq.generated.tables.SlaMilestone.SLA_MILESTONE;
import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;

/**
 * M61 Task ELAPSED SLA；M369 扩展 BUSINESS 日历截止（ADR-090 D1-R）。ADR-091 jOOQ 实现。
 *
 * <p>Task 创建/完成、SLA 状态、里程碑、Inbox 与 Outbox 分别在消费者本地事务中提交。到期扫描同时
 * 锁定 SLA、里程碑和 Task（FOR UPDATE OF ... SKIP LOCKED），避免“Task 已完成但完成事件尚未消费”
 * 被误判为超时。BUSINESS 在 start 时锁定日历版本并用纯函数预计算 deadlineAt。</p>
 */
@Service
final class JooqSlaClockService implements SlaClockService, TaskSlaEventConsumer {
    private static final String START_CONSUMER = "sla.task-created.v1";

    private final DSLContext dsl;
    private final TaskFulfillmentContextService tasks;
    private final ConfigurationService configurations;
    private final InboxService inbox;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqSlaClockService(
            DSLContext dsl,
            TaskFulfillmentContextService tasks,
            ConfigurationService configurations,
            InboxService inbox,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
        this.tasks = tasks;
        this.configurations = configurations;
        this.inbox = inbox;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void start(OutboxMessage message, UUID taskId, String eventTaskType, Instant startedAt) {
        InboxDecision decision = inbox.begin(message.tenantId(), START_CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }
        TaskFulfillmentContext task = requireTask(message.tenantId(), taskId);
        if (!task.taskType().equals(eventTaskType)) {
            throw new IllegalArgumentException("Task SLA event taskType mismatch");
        }
        if (task.slaRef() == null) {
            completeNotApplicable(message, START_CONSUMER, taskId);
            return;
        }
        ConfigurationAssetDefinition asset = requirePolicy(message.tenantId(), task);
        TaskSlaPolicy policy = policy(asset);
        if (!policy.taskTypes().contains(task.taskType())) {
            throw new IllegalStateException("Frozen SLA policy does not cover Task type");
        }
        ConfigurationAssetDefinition calendarAsset = null;
        BusinessCalendar calendar = null;
        if ("BUSINESS".equals(policy.clockMode())) {
            calendarAsset = requireCalendar(message.tenantId(), task, policy.calendarRef());
            calendar = BusinessCalendar.parse(readTree(calendarAsset.definitionJson()));
        }
        Instant deadlineAt;
        try {
            deadlineAt = "BUSINESS".equals(policy.clockMode())
                    ? BusinessCalendarDeadlineCalculator.addBusinessSeconds(
                            startedAt, policy.targetDurationSeconds(), calendar)
                    : startedAt.plusSeconds(policy.targetDurationSeconds());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("SLA deadline exceeds supported Instant range", exception);
        }
        UUID instanceId = UUID.randomUUID();
        SlaInstance instance = SLA_INSTANCE;
        int inserted = dsl.insertInto(instance)
                .set(instance.SLA_INSTANCE_ID, instanceId)
                .set(instance.TENANT_ID, message.tenantId())
                .set(instance.PROJECT_ID, task.projectId())
                .set(instance.WORK_ORDER_ID, task.workOrderId())
                .set(instance.TASK_ID, taskId)
                .set(instance.SLA_REF, task.slaRef())
                .set(instance.POLICY_VERSION_ID, asset.versionId())
                .set(instance.POLICY_SEMANTIC_VERSION, asset.semanticVersion())
                .set(instance.POLICY_CONTENT_DIGEST, asset.contentDigest())
                .set(instance.CLOCK_MODE, policy.clockMode())
                .set(instance.TARGET_DURATION_SECONDS, policy.targetDurationSeconds())
                .set(instance.START_EVENT_ID, message.eventId())
                .set(instance.STARTED_AT, startedAt)
                .set(instance.DEADLINE_AT, deadlineAt)
                .set(instance.STATUS, "RUNNING")
                .set(instance.AGGREGATE_VERSION, 1L)
                .set(instance.CORRELATION_ID, message.correlationId())
                .set(instance.CREATED_AT, startedAt)
                .set(instance.UPDATED_AT, startedAt)
                .set(instance.CALENDAR_REF, calendarAsset == null ? null : calendarAsset.assetKey())
                .set(instance.CALENDAR_VERSION_ID, calendarAsset == null ? null : calendarAsset.versionId())
                .set(instance.CALENDAR_SEMANTIC_VERSION,
                        calendarAsset == null ? null : calendarAsset.semanticVersion())
                .set(instance.CALENDAR_CONTENT_DIGEST,
                        calendarAsset == null ? null : calendarAsset.contentDigest())
                .onConflict(instance.TENANT_ID, instance.TASK_ID)
                .doNothing()
                .execute();
        if (inserted != 1) {
            throw new IllegalStateException("Task is already bound to another SLA start fact");
        }
        dsl.insertInto(SLA_CLOCK_SEGMENT)
                .set(SLA_CLOCK_SEGMENT.SEGMENT_ID, UUID.randomUUID())
                .set(SLA_CLOCK_SEGMENT.TENANT_ID, message.tenantId())
                .set(SLA_CLOCK_SEGMENT.SLA_INSTANCE_ID, instanceId)
                .set(SLA_CLOCK_SEGMENT.SEGMENT_NO, 1)
                .set(SLA_CLOCK_SEGMENT.SEGMENT_TYPE, "RUNNING")
                .set(SLA_CLOCK_SEGMENT.STARTED_AT, startedAt)
                .set(SLA_CLOCK_SEGMENT.START_EVENT_ID, message.eventId())
                .execute();
        dsl.insertInto(SLA_MILESTONE)
                .set(SLA_MILESTONE.MILESTONE_ID, UUID.randomUUID())
                .set(SLA_MILESTONE.TENANT_ID, message.tenantId())
                .set(SLA_MILESTONE.SLA_INSTANCE_ID, instanceId)
                .set(SLA_MILESTONE.MILESTONE_TYPE, "TARGET_DUE")
                .set(SLA_MILESTONE.SCHEDULED_AT, deadlineAt)
                .set(SLA_MILESTONE.STATUS, "PENDING")
                .execute();
        appendStarted(message, instanceId, task, asset, policy, calendarAsset, startedAt, deadlineAt);
        inbox.complete(message.tenantId(), START_CONSUMER, message.eventId(),
                Sha256.digest(instanceId + "|RUNNING|" + deadlineAt));
    }

    @Transactional
    public void stop(OutboxMessage message, UUID taskId, Instant completedAt) {
        String consumer = "sla.task-completed.v" + message.schemaVersion();
        InboxDecision decision = inbox.begin(message.tenantId(), consumer, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }
        TaskFulfillmentContext task = requireTask(message.tenantId(), taskId);
        if (task.slaRef() == null) {
            completeNotApplicable(message, consumer, taskId);
            return;
        }
        InstanceState state = lockByTask(message.tenantId(), taskId)
                .orElseThrow(() -> new IllegalStateException("SLA start event has not been processed"));
        if (completedAt.isBefore(state.startedAt())) {
            throw new IllegalArgumentException("Task completion precedes SLA start");
        }
        boolean late = completedAt.isAfter(state.deadlineAt());
        if (!late && !"RUNNING".equals(state.status())) {
            throw new IllegalStateException("On-time Task completion conflicts with recorded SLA breach");
        }
        if (late && !("RUNNING".equals(state.status()) || "BREACHED".equals(state.status()))) {
            throw new IllegalStateException("Task SLA is already terminal");
        }
        UUID breachEventId = state.milestoneTriggerEventId();
        if (late && "RUNNING".equals(state.status())) {
            breachEventId = triggerMilestone(
                    message.tenantId(), state, completedAt, message.eventId(), message.correlationId());
            state = markBreached(state, completedAt, breachEventId);
        } else if (!late) {
            cancelMilestone(message.tenantId(), state.slaInstanceId());
        }
        long elapsedSeconds = elapsedSeconds(message.tenantId(), task, state, completedAt);
        String terminalStatus = late ? "MET_LATE" : "MET";
        SlaInstance instance = SLA_INSTANCE;
        // 终态迁移必须带原状态条件并校验影响行数；CASE/GREATEST 表达式保持原 SQL 的单语句原子语义。
        int updated = dsl.update(instance)
                .set(instance.STATUS, terminalStatus)
                .set(instance.BREACHED_AT, DSL.when(DSL.condition(DSL.val(late)), instance.DEADLINE_AT)
                        .otherwise((Instant) null))
                .set(instance.BREACH_DETECTED_AT, DSL.when(DSL.condition(DSL.val(late))
                                .and(instance.BREACH_DETECTED_AT.isNull()), completedAt)
                        .otherwise(instance.BREACH_DETECTED_AT))
                .set(instance.STOP_EVENT_ID, message.eventId())
                .set(instance.COMPLETED_AT, completedAt)
                .set(instance.ELAPSED_SECONDS, elapsedSeconds)
                .set(instance.AGGREGATE_VERSION, instance.AGGREGATE_VERSION.plus(1))
                .set(instance.UPDATED_AT, DSL.greatest(instance.UPDATED_AT,
                        DSL.val(completedAt, instance.UPDATED_AT)))
                .where(instance.SLA_INSTANCE_ID.eq(state.slaInstanceId()))
                .and(instance.STATUS.in("RUNNING", "BREACHED"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("SLA changed during Task completion");
        }
        closeSegment(message.tenantId(), state.slaInstanceId(), message.eventId(), completedAt, elapsedSeconds);
        long version = state.aggregateVersion() + 1;
        appendMet(message, state, terminalStatus, completedAt, elapsedSeconds, version, breachEventId);
        inbox.complete(message.tenantId(), consumer, message.eventId(),
                Sha256.digest(state.slaInstanceId() + "|" + terminalStatus + "|" + completedAt));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlaInstanceView> findByTask(String tenantId, UUID taskId) {
        requireText(tenantId, "tenantId");
        Objects.requireNonNull(taskId, "taskId must not be null");
        SlaInstance instance = SLA_INSTANCE;
        return dsl.select(
                        instance.SLA_INSTANCE_ID, instance.PROJECT_ID, instance.WORK_ORDER_ID,
                        instance.TASK_ID, instance.SLA_REF, instance.POLICY_VERSION_ID,
                        instance.POLICY_SEMANTIC_VERSION, instance.POLICY_CONTENT_DIGEST,
                        instance.CLOCK_MODE, instance.TARGET_DURATION_SECONDS,
                        instance.STARTED_AT, instance.DEADLINE_AT, instance.STATUS,
                        instance.BREACHED_AT, instance.BREACH_DETECTED_AT, instance.COMPLETED_AT,
                        instance.ELAPSED_SECONDS, instance.AGGREGATE_VERSION)
                .from(instance)
                .where(instance.TENANT_ID.eq(tenantId))
                .and(instance.TASK_ID.eq(taskId))
                .fetchOptional(row -> new SlaInstanceView(
                        row.get(instance.SLA_INSTANCE_ID),
                        row.get(instance.PROJECT_ID), row.get(instance.WORK_ORDER_ID),
                        row.get(instance.TASK_ID), row.get(instance.SLA_REF),
                        row.get(instance.POLICY_VERSION_ID), row.get(instance.POLICY_SEMANTIC_VERSION),
                        row.get(instance.POLICY_CONTENT_DIGEST), row.get(instance.CLOCK_MODE),
                        row.get(instance.TARGET_DURATION_SECONDS), row.get(instance.STARTED_AT),
                        row.get(instance.DEADLINE_AT), row.get(instance.STATUS), row.get(instance.BREACHED_AT),
                        row.get(instance.BREACH_DETECTED_AT), row.get(instance.COMPLETED_AT),
                        row.get(instance.ELAPSED_SECONDS), row.get(instance.AGGREGATE_VERSION)));
    }

    @Override
    @Transactional
    public boolean detectNextBreach() {
        Instant detectedAt = clock.instant();
        SlaInstance instanceRow = SLA_INSTANCE.as("instance_row");
        SlaMilestone milestone = SLA_MILESTONE.as("milestone");
        TskTask taskRow = TSK_TASK.as("task_row");
        // limit 必须先于 forUpdate；FOR UPDATE OF 三表 + SKIP LOCKED 与原到期对账 SQL 等价。
        Optional<InstanceState> candidate = dsl.select(
                        instanceRow.SLA_INSTANCE_ID, instanceRow.TENANT_ID, instanceRow.TASK_ID,
                        instanceRow.STARTED_AT, instanceRow.DEADLINE_AT,
                        instanceRow.STATUS, instanceRow.AGGREGATE_VERSION,
                        instanceRow.CORRELATION_ID, instanceRow.CLOCK_MODE,
                        instanceRow.CALENDAR_REF, milestone.MILESTONE_ID,
                        milestone.TRIGGER_EVENT_ID)
                .from(instanceRow)
                .join(milestone)
                .on(milestone.TENANT_ID.eq(instanceRow.TENANT_ID))
                .and(milestone.SLA_INSTANCE_ID.eq(instanceRow.SLA_INSTANCE_ID))
                .and(milestone.MILESTONE_TYPE.eq("TARGET_DUE"))
                .join(taskRow)
                .on(taskRow.TENANT_ID.eq(instanceRow.TENANT_ID))
                .and(taskRow.TASK_ID.eq(instanceRow.TASK_ID))
                .where(instanceRow.STATUS.eq("RUNNING"))
                .and(milestone.STATUS.eq("PENDING"))
                .and(milestone.SCHEDULED_AT.le(detectedAt))
                .and(taskRow.STATUS.ne("COMPLETED"))
                .orderBy(milestone.SCHEDULED_AT, milestone.MILESTONE_ID)
                .limit(1)
                .forUpdate().of(instanceRow, milestone, taskRow).skipLocked()
                .fetchOptional(row -> instanceState(row, instanceRow, milestone));
        if (candidate.isEmpty()) {
            return false;
        }
        InstanceState state = candidate.get();
        UUID breachEventId = triggerMilestone(
                state.tenantId(), state, detectedAt, state.milestoneId(), state.correlationId());
        markBreached(state, detectedAt, breachEventId);
        return true;
    }

    /**
     * 先提交聚合的 BREACHED 迁移，再允许同事务内进入 MET_LATE。这样 breach/met 两个领域事件拥有
     * 单调且互不重复的 aggregateVersion，重放方无需猜测同版本事件顺序。
     */
    private InstanceState markBreached(InstanceState state, Instant detectedAt, UUID breachEventId) {
        SlaInstance instance = SLA_INSTANCE;
        int updated = dsl.update(instance)
                .set(instance.STATUS, "BREACHED")
                .set(instance.BREACHED_AT, instance.DEADLINE_AT)
                .set(instance.BREACH_DETECTED_AT, detectedAt)
                .set(instance.AGGREGATE_VERSION, instance.AGGREGATE_VERSION.plus(1))
                .set(instance.UPDATED_AT, detectedAt)
                .where(instance.SLA_INSTANCE_ID.eq(state.slaInstanceId()))
                .and(instance.STATUS.eq("RUNNING"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("SLA changed during breach reconciliation");
        }
        return new InstanceState(
                state.slaInstanceId(), state.tenantId(), state.taskId(), state.startedAt(),
                state.deadlineAt(), "BREACHED", state.aggregateVersion() + 1,
                state.correlationId(), state.clockMode(), state.calendarRef(),
                state.milestoneId(), breachEventId);
    }

    private UUID triggerMilestone(
            String tenantId,
            InstanceState state,
            Instant detectedAt,
            UUID causationId,
            String correlationId
    ) {
        UUID eventId = UUID.randomUUID();
        int updated = dsl.update(SLA_MILESTONE)
                .set(SLA_MILESTONE.STATUS, "TRIGGERED")
                .set(SLA_MILESTONE.TRIGGERED_AT, SLA_MILESTONE.SCHEDULED_AT)
                .set(SLA_MILESTONE.DETECTED_AT, detectedAt)
                .set(SLA_MILESTONE.TRIGGER_EVENT_ID, eventId)
                .where(SLA_MILESTONE.SLA_INSTANCE_ID.eq(state.slaInstanceId()))
                .and(SLA_MILESTONE.MILESTONE_TYPE.eq("TARGET_DUE"))
                .and(SLA_MILESTONE.STATUS.eq("PENDING"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("SLA TARGET_DUE milestone is no longer pending");
        }
        String payload = json(new SlaBreachedPayload(
                state.slaInstanceId(), state.taskId(), state.deadlineAt(), state.deadlineAt(), detectedAt));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), eventId, "sla", "sla.breached", 1,
                "SlaInstance", state.slaInstanceId().toString(), state.aggregateVersion() + 1,
                tenantId, correlationId, causationId.toString(), state.slaInstanceId().toString(),
                payload, Sha256.digest(payload), detectedAt));
        return eventId;
    }

    private void cancelMilestone(String tenantId, UUID instanceId) {
        int updated = dsl.update(SLA_MILESTONE)
                .set(SLA_MILESTONE.STATUS, "CANCELLED")
                .where(SLA_MILESTONE.TENANT_ID.eq(tenantId))
                .and(SLA_MILESTONE.SLA_INSTANCE_ID.eq(instanceId))
                .and(SLA_MILESTONE.MILESTONE_TYPE.eq("TARGET_DUE"))
                .and(SLA_MILESTONE.STATUS.eq("PENDING"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("On-time SLA TARGET_DUE milestone is not pending");
        }
    }

    private void closeSegment(
            String tenantId, UUID instanceId, UUID stopEventId, Instant completedAt, long elapsedSeconds
    ) {
        int updated = dsl.update(SLA_CLOCK_SEGMENT)
                .set(SLA_CLOCK_SEGMENT.ENDED_AT, completedAt)
                .set(SLA_CLOCK_SEGMENT.ELAPSED_SECONDS, elapsedSeconds)
                .set(SLA_CLOCK_SEGMENT.END_EVENT_ID, stopEventId)
                .where(SLA_CLOCK_SEGMENT.TENANT_ID.eq(tenantId))
                .and(SLA_CLOCK_SEGMENT.SLA_INSTANCE_ID.eq(instanceId))
                .and(SLA_CLOCK_SEGMENT.ENDED_AT.isNull())
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("SLA RUNNING segment is missing or already closed");
        }
    }

    private Optional<InstanceState> lockByTask(String tenantId, UUID taskId) {
        SlaInstance instanceRow = SLA_INSTANCE.as("instance_row");
        SlaMilestone milestone = SLA_MILESTONE.as("milestone");
        return dsl.select(
                        instanceRow.SLA_INSTANCE_ID, instanceRow.TENANT_ID, instanceRow.TASK_ID,
                        instanceRow.STARTED_AT, instanceRow.DEADLINE_AT,
                        instanceRow.STATUS, instanceRow.AGGREGATE_VERSION,
                        instanceRow.CORRELATION_ID, instanceRow.CLOCK_MODE,
                        instanceRow.CALENDAR_REF, milestone.MILESTONE_ID,
                        milestone.TRIGGER_EVENT_ID)
                .from(instanceRow)
                .join(milestone)
                .on(milestone.TENANT_ID.eq(instanceRow.TENANT_ID))
                .and(milestone.SLA_INSTANCE_ID.eq(instanceRow.SLA_INSTANCE_ID))
                .and(milestone.MILESTONE_TYPE.eq("TARGET_DUE"))
                .where(instanceRow.TENANT_ID.eq(tenantId))
                .and(instanceRow.TASK_ID.eq(taskId))
                .forUpdate().of(instanceRow, milestone)
                .fetchOptional(row -> instanceState(row, instanceRow, milestone));
    }

    private static InstanceState instanceState(Record row, SlaInstance instanceRow, SlaMilestone milestone) {
        return new InstanceState(
                row.get(instanceRow.SLA_INSTANCE_ID),
                row.get(instanceRow.TENANT_ID), row.get(instanceRow.TASK_ID), row.get(instanceRow.STARTED_AT),
                row.get(instanceRow.DEADLINE_AT), row.get(instanceRow.STATUS),
                row.get(instanceRow.AGGREGATE_VERSION), row.get(instanceRow.CORRELATION_ID),
                row.get(instanceRow.CLOCK_MODE), row.get(instanceRow.CALENDAR_REF),
                row.get(milestone.MILESTONE_ID),
                row.get(milestone.TRIGGER_EVENT_ID));
    }

    private TaskFulfillmentContext requireTask(String tenantId, UUID taskId) {
        return tasks.find(tenantId, taskId)
                .orElseThrow(() -> new IllegalStateException("Task SLA source Task does not exist"));
    }

    private ConfigurationAssetDefinition requirePolicy(String tenantId, TaskFulfillmentContext task) {
        List<ConfigurationAssetDefinition> matches = configurations.listBundleAssets(
                        tenantId, task.configurationBundleId(), task.configurationBundleDigest(),
                        ConfigurationAssetType.SLA).stream()
                .filter(asset -> task.slaRef().equals(asset.assetKey())).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Task slaRef must resolve to exactly one frozen SLA policy");
        }
        return matches.getFirst();
    }

    private ConfigurationAssetDefinition requireCalendar(
            String tenantId, TaskFulfillmentContext task, String calendarRef
    ) {
        if (calendarRef == null || calendarRef.isBlank()) {
            throw new IllegalStateException("BUSINESS SLA requires calendarRef");
        }
        List<ConfigurationAssetDefinition> matches = configurations.listBundleAssets(
                        tenantId, task.configurationBundleId(), task.configurationBundleDigest(),
                        ConfigurationAssetType.CALENDAR).stream()
                .filter(asset -> calendarRef.equals(asset.assetKey())).toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "BUSINESS SLA calendarRef must resolve to exactly one frozen CALENDAR");
        }
        return matches.getFirst();
    }

    private TaskSlaPolicy policy(ConfigurationAssetDefinition asset) {
        try {
            TaskSlaPolicy policy = objectMapper.readValue(asset.definitionJson(), TaskSlaPolicy.class);
            boolean business = "BUSINESS".equals(policy.clockMode());
            boolean elapsed = "ELAPSED".equals(policy.clockMode());
            boolean calendarOk = business
                    ? policy.calendarRef() != null && !policy.calendarRef().isBlank()
                    : policy.calendarRef() == null || policy.calendarRef().isBlank();
            if (!asset.assetKey().equals(policy.policyKey())
                    || !asset.semanticVersion().equals(policy.version())
                    || !"TASK".equals(policy.subjectType())
                    || !"TASK_CREATED".equals(policy.startEvent())
                    || !"TASK_COMPLETED".equals(policy.stopEvent())
                    || !(elapsed || business)
                    || !calendarOk
                    || policy.taskTypes() == null || policy.taskTypes().isEmpty()
                    || policy.targetDurationSeconds() < 1 || policy.targetDurationSeconds() > 31536000) {
                throw new IllegalStateException("Frozen SLA policy identity or semantics are invalid");
            }
            return policy;
        } catch (JacksonException exception) {
            throw new IllegalStateException("Frozen SLA policy cannot be decoded", exception);
        }
    }

    private long elapsedSeconds(
            String tenantId, TaskFulfillmentContext task, InstanceState state, Instant completedAt
    ) {
        if (!"BUSINESS".equals(state.clockMode())) {
            return Duration.between(state.startedAt(), completedAt).getSeconds();
        }
        ConfigurationAssetDefinition calendarAsset = requireCalendar(
                tenantId, task, state.calendarRef());
        BusinessCalendar calendar = BusinessCalendar.parse(readTree(calendarAsset.definitionJson()));
        return BusinessCalendarDeadlineCalculator.businessSecondsBetween(
                state.startedAt(), completedAt, calendar);
    }

    private tools.jackson.databind.JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Frozen calendar cannot be decoded", exception);
        }
    }

    private void appendStarted(
            OutboxMessage source,
            UUID instanceId,
            TaskFulfillmentContext task,
            ConfigurationAssetDefinition asset,
            TaskSlaPolicy policy,
            ConfigurationAssetDefinition calendarAsset,
            Instant startedAt,
            Instant deadlineAt
    ) {
        String payload = json(new SlaStartedPayload(
                instanceId, task.taskId(), task.projectId(), task.workOrderId(), task.slaRef(),
                asset.versionId(), asset.semanticVersion(), asset.contentDigest(), policy.clockMode(),
                policy.targetDurationSeconds(), startedAt, deadlineAt,
                calendarAsset == null ? null : calendarAsset.assetKey(),
                calendarAsset == null ? null : calendarAsset.versionId(),
                calendarAsset == null ? null : calendarAsset.semanticVersion(),
                calendarAsset == null ? null : calendarAsset.contentDigest()));
        // M369：BUSINESS 需冻结日历字段；ELAPSED 日历字段为 null。已发布 v1 不可变，故统一发 @v2。
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "sla", "sla.started", 2,
                "SlaInstance", instanceId.toString(), 1,
                source.tenantId(), source.correlationId(), source.eventId().toString(),
                instanceId.toString(), payload, Sha256.digest(payload), startedAt));
    }

    private void appendMet(
            OutboxMessage source,
            InstanceState state,
            String status,
            Instant completedAt,
            long elapsedSeconds,
            long version,
            UUID breachEventId
    ) {
        String payload = json(new SlaMetPayload(
                state.slaInstanceId(), state.taskId(), status, state.startedAt(), state.deadlineAt(),
                "MET_LATE".equals(status) ? state.deadlineAt() : null,
                completedAt, elapsedSeconds, breachEventId));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "sla", "sla.met", 1,
                "SlaInstance", state.slaInstanceId().toString(), version,
                source.tenantId(), source.correlationId(), source.eventId().toString(),
                state.slaInstanceId().toString(), payload, Sha256.digest(payload), completedAt));
    }

    private void completeNotApplicable(OutboxMessage message, String consumer, UUID taskId) {
        inbox.complete(message.tenantId(), consumer, message.eventId(),
                Sha256.digest(taskId + "|SLA_NOT_CONFIGURED"));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("SLA event serialization failed", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private record TaskSlaPolicy(
            String policyKey,
            String version,
            String subjectType,
            List<String> taskTypes,
            String startEvent,
            String stopEvent,
            String clockMode,
            long targetDurationSeconds,
            String calendarRef
    ) {
    }

    private record InstanceState(
            UUID slaInstanceId,
            String tenantId,
            UUID taskId,
            Instant startedAt,
            Instant deadlineAt,
            String status,
            long aggregateVersion,
            String correlationId,
            String clockMode,
            String calendarRef,
            UUID milestoneId,
            UUID milestoneTriggerEventId
    ) {
    }

    private record SlaStartedPayload(
            UUID slaInstanceId,
            UUID taskId,
            UUID projectId,
            UUID workOrderId,
            String slaRef,
            UUID policyVersionId,
            String policySemanticVersion,
            String policyContentDigest,
            String clockMode,
            long targetDurationSeconds,
            Instant startedAt,
            Instant deadlineAt,
            String calendarRef,
            UUID calendarVersionId,
            String calendarSemanticVersion,
            String calendarContentDigest
    ) {
    }

    private record SlaBreachedPayload(
            UUID slaInstanceId,
            UUID taskId,
            Instant deadlineAt,
            Instant breachedAt,
            Instant detectedAt
    ) {
    }

    private record SlaMetPayload(
            UUID slaInstanceId,
            UUID taskId,
            String status,
            Instant startedAt,
            Instant deadlineAt,
            Instant breachedAt,
            Instant completedAt,
            long elapsedSeconds,
            UUID breachEventId
    ) {
    }
}
