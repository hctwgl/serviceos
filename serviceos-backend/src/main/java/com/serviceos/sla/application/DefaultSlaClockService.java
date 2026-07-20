package com.serviceos.sla.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
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
import org.springframework.jdbc.core.simple.JdbcClient;
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

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * M61 Task ELAPSED SLA；M369 扩展 BUSINESS 日历截止（ADR-090 D1-R）。
 *
 * <p>Task 创建/完成、SLA 状态、里程碑、Inbox 与 Outbox 分别在消费者本地事务中提交。到期扫描同时
 * 锁定 SLA、里程碑和 Task，避免“Task 已完成但完成事件尚未消费”被误判为超时。
 * BUSINESS 在 start 时锁定日历版本并用纯函数预计算 deadlineAt。</p>
 */
@Service
final class DefaultSlaClockService implements SlaClockService, TaskSlaEventConsumer {
    private static final String START_CONSUMER = "sla.task-created.v1";

    private final JdbcClient jdbc;
    private final TaskFulfillmentContextService tasks;
    private final ConfigurationService configurations;
    private final InboxService inbox;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultSlaClockService(
            JdbcClient jdbc,
            TaskFulfillmentContextService tasks,
            ConfigurationService configurations,
            InboxService inbox,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
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
        int inserted = jdbc.sql("""
                        INSERT INTO sla_instance (
                            sla_instance_id, tenant_id, project_id, work_order_id, task_id,
                            sla_ref, policy_version_id, policy_semantic_version, policy_content_digest,
                            clock_mode, target_duration_seconds, start_event_id, started_at, deadline_at,
                            status, aggregate_version, correlation_id, created_at, updated_at,
                            calendar_ref, calendar_version_id, calendar_semantic_version, calendar_content_digest)
                        VALUES (
                            :instanceId, :tenantId, :projectId, :workOrderId, :taskId,
                            :slaRef, :policyVersionId, :policyVersion, :policyDigest,
                            :clockMode, :duration, :eventId, :startedAt, :deadlineAt,
                            'RUNNING', 1, :correlationId, :startedAt, :startedAt,
                            :calendarRef, :calendarVersionId, :calendarVersion, :calendarDigest)
                        ON CONFLICT (tenant_id, task_id) DO NOTHING
                        """)
                .param("instanceId", instanceId).param("tenantId", message.tenantId())
                .param("projectId", task.projectId()).param("workOrderId", task.workOrderId())
                .param("taskId", taskId).param("slaRef", task.slaRef())
                .param("policyVersionId", asset.versionId()).param("policyVersion", asset.semanticVersion())
                .param("policyDigest", asset.contentDigest())
                .param("clockMode", policy.clockMode())
                .param("duration", policy.targetDurationSeconds())
                .param("eventId", message.eventId()).param("startedAt", timestamptz(startedAt))
                .param("deadlineAt", timestamptz(deadlineAt)).param("correlationId", message.correlationId())
                .param("calendarRef", calendarAsset == null ? null : calendarAsset.assetKey())
                .param("calendarVersionId", calendarAsset == null ? null : calendarAsset.versionId())
                .param("calendarVersion", calendarAsset == null ? null : calendarAsset.semanticVersion())
                .param("calendarDigest", calendarAsset == null ? null : calendarAsset.contentDigest())
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("Task is already bound to another SLA start fact");
        }
        jdbc.sql("""
                        INSERT INTO sla_clock_segment (
                            segment_id, tenant_id, sla_instance_id, segment_no,
                            segment_type, started_at, start_event_id)
                        VALUES (:segmentId, :tenantId, :instanceId, 1, 'RUNNING', :startedAt, :eventId)
                        """)
                .param("segmentId", UUID.randomUUID()).param("tenantId", message.tenantId())
                .param("instanceId", instanceId).param("startedAt", timestamptz(startedAt))
                .param("eventId", message.eventId()).update();
        jdbc.sql("""
                        INSERT INTO sla_milestone (
                            milestone_id, tenant_id, sla_instance_id, milestone_type, scheduled_at, status)
                        VALUES (:milestoneId, :tenantId, :instanceId, 'TARGET_DUE', :deadlineAt, 'PENDING')
                        """)
                .param("milestoneId", UUID.randomUUID()).param("tenantId", message.tenantId())
                .param("instanceId", instanceId).param("deadlineAt", timestamptz(deadlineAt)).update();
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
        int updated = jdbc.sql("""
                        UPDATE sla_instance
                           SET status=:status,
                               breached_at=CASE WHEN :late THEN deadline_at ELSE NULL END,
                               breach_detected_at=CASE
                                   WHEN :late AND breach_detected_at IS NULL THEN :completedAt
                                   ELSE breach_detected_at END,
                               stop_event_id=:stopEventId, completed_at=:completedAt,
                               elapsed_seconds=:elapsedSeconds,
                               aggregate_version=aggregate_version+1,
                               updated_at=GREATEST(updated_at, :completedAt)
                         WHERE sla_instance_id=:instanceId
                           AND status IN ('RUNNING','BREACHED')
                        """)
                .param("status", terminalStatus).param("late", late)
                .param("completedAt", timestamptz(completedAt)).param("stopEventId", message.eventId())
                .param("elapsedSeconds", elapsedSeconds).param("instanceId", state.slaInstanceId()).update();
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
        return jdbc.sql("""
                        SELECT sla_instance_id, project_id, work_order_id, task_id, sla_ref,
                               policy_version_id, policy_semantic_version, policy_content_digest,
                               clock_mode, target_duration_seconds, started_at, deadline_at, status,
                               breached_at, breach_detected_at, completed_at, elapsed_seconds,
                               aggregate_version
                          FROM sla_instance WHERE tenant_id=:tenantId AND task_id=:taskId
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .query((rs, row) -> new SlaInstanceView(
                        rs.getObject("sla_instance_id", UUID.class),
                        rs.getObject("project_id", UUID.class), rs.getObject("work_order_id", UUID.class),
                        rs.getObject("task_id", UUID.class), rs.getString("sla_ref"),
                        rs.getObject("policy_version_id", UUID.class), rs.getString("policy_semantic_version"),
                        rs.getString("policy_content_digest"), rs.getString("clock_mode"),
                        rs.getLong("target_duration_seconds"), instant(rs, "started_at"),
                        instant(rs, "deadline_at"), rs.getString("status"), instant(rs, "breached_at"),
                        instant(rs, "breach_detected_at"), instant(rs, "completed_at"),
                        rs.getObject("elapsed_seconds", Long.class), rs.getLong("aggregate_version")))
                .optional();
    }

    @Override
    @Transactional
    public boolean detectNextBreach() {
        Instant detectedAt = clock.instant();
        Optional<InstanceState> candidate = jdbc.sql("""
                        SELECT instance_row.sla_instance_id, instance_row.tenant_id, instance_row.task_id,
                               instance_row.started_at, instance_row.deadline_at,
                               instance_row.status, instance_row.aggregate_version,
                               instance_row.correlation_id, instance_row.clock_mode,
                               instance_row.calendar_ref, milestone.milestone_id,
                               milestone.trigger_event_id
                          FROM sla_instance instance_row
                          JOIN sla_milestone milestone
                            ON milestone.tenant_id=instance_row.tenant_id
                           AND milestone.sla_instance_id=instance_row.sla_instance_id
                           AND milestone.milestone_type='TARGET_DUE'
                          JOIN tsk_task task_row
                            ON task_row.tenant_id=instance_row.tenant_id
                           AND task_row.task_id=instance_row.task_id
                         WHERE instance_row.status='RUNNING' AND milestone.status='PENDING'
                           AND milestone.scheduled_at <= :detectedAt
                           AND task_row.status <> 'COMPLETED'
                         ORDER BY milestone.scheduled_at, milestone.milestone_id
                         FOR UPDATE OF instance_row, milestone, task_row SKIP LOCKED
                         LIMIT 1
                        """)
                .param("detectedAt", timestamptz(detectedAt))
                .query((rs, row) -> new InstanceState(
                        rs.getObject("sla_instance_id", UUID.class),
                        rs.getString("tenant_id"), rs.getObject("task_id", UUID.class), instant(rs, "started_at"),
                        instant(rs, "deadline_at"), rs.getString("status"),
                        rs.getLong("aggregate_version"), rs.getString("correlation_id"),
                        rs.getString("clock_mode"), rs.getString("calendar_ref"),
                        rs.getObject("milestone_id", UUID.class),
                        rs.getObject("trigger_event_id", UUID.class)))
                .optional();
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
        int updated = jdbc.sql("""
                        UPDATE sla_instance
                           SET status='BREACHED', breached_at=deadline_at,
                               breach_detected_at=:detectedAt,
                               aggregate_version=aggregate_version+1, updated_at=:detectedAt
                         WHERE sla_instance_id=:instanceId AND status='RUNNING'
                        """)
                .param("detectedAt", timestamptz(detectedAt))
                .param("instanceId", state.slaInstanceId()).update();
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
        int updated = jdbc.sql("""
                        UPDATE sla_milestone
                           SET status='TRIGGERED', triggered_at=scheduled_at,
                               detected_at=:detectedAt, trigger_event_id=:eventId
                         WHERE sla_instance_id=:instanceId
                           AND milestone_type='TARGET_DUE' AND status='PENDING'
                        """)
                .param("detectedAt", timestamptz(detectedAt)).param("eventId", eventId)
                .param("instanceId", state.slaInstanceId()).update();
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
        int updated = jdbc.sql("""
                        UPDATE sla_milestone SET status='CANCELLED'
                         WHERE tenant_id=:tenantId AND sla_instance_id=:instanceId
                           AND milestone_type='TARGET_DUE' AND status='PENDING'
                        """)
                .param("tenantId", tenantId).param("instanceId", instanceId).update();
        if (updated != 1) {
            throw new IllegalStateException("On-time SLA TARGET_DUE milestone is not pending");
        }
    }

    private void closeSegment(
            String tenantId, UUID instanceId, UUID stopEventId, Instant completedAt, long elapsedSeconds
    ) {
        int updated = jdbc.sql("""
                        UPDATE sla_clock_segment
                           SET ended_at=:completedAt, elapsed_seconds=:elapsedSeconds, end_event_id=:stopEventId
                         WHERE tenant_id=:tenantId AND sla_instance_id=:instanceId AND ended_at IS NULL
                        """)
                .param("completedAt", timestamptz(completedAt)).param("elapsedSeconds", elapsedSeconds)
                .param("stopEventId", stopEventId).param("tenantId", tenantId)
                .param("instanceId", instanceId).update();
        if (updated != 1) {
            throw new IllegalStateException("SLA RUNNING segment is missing or already closed");
        }
    }

    private Optional<InstanceState> lockByTask(String tenantId, UUID taskId) {
        return jdbc.sql("""
                        SELECT instance_row.sla_instance_id, instance_row.tenant_id, instance_row.task_id,
                               instance_row.started_at, instance_row.deadline_at,
                               instance_row.status, instance_row.aggregate_version,
                               instance_row.correlation_id, instance_row.clock_mode,
                               instance_row.calendar_ref, milestone.milestone_id,
                               milestone.trigger_event_id
                          FROM sla_instance instance_row
                          JOIN sla_milestone milestone
                            ON milestone.tenant_id=instance_row.tenant_id
                           AND milestone.sla_instance_id=instance_row.sla_instance_id
                           AND milestone.milestone_type='TARGET_DUE'
                         WHERE instance_row.tenant_id=:tenantId AND instance_row.task_id=:taskId
                         FOR UPDATE OF instance_row, milestone
                        """)
                .param("tenantId", tenantId).param("taskId", taskId)
                .query((rs, row) -> new InstanceState(
                        rs.getObject("sla_instance_id", UUID.class),
                        rs.getString("tenant_id"), rs.getObject("task_id", UUID.class), instant(rs, "started_at"),
                        instant(rs, "deadline_at"), rs.getString("status"),
                        rs.getLong("aggregate_version"), rs.getString("correlation_id"),
                        rs.getString("clock_mode"), rs.getString("calendar_ref"),
                        rs.getObject("milestone_id", UUID.class),
                        rs.getObject("trigger_event_id", UUID.class)))
                .optional();
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

    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.time.OffsetDateTime value = rs.getObject(column, java.time.OffsetDateTime.class);
        return value == null ? null : value.toInstant();
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
