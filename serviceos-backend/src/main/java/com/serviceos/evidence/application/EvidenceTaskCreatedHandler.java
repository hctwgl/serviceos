package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskCreatedPayload;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** task.created@v1 的可靠消费者；所有 generation 写入统一委托给单一解析服务。 */
@Service
final class EvidenceTaskCreatedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "evidence.task-created.fixed-slots.v1";
    private static final String SYSTEM_ACTOR = "system:evidence-slot-resolver";

    private final InboxService inbox;
    private final TaskFulfillmentContextService tasks;
    private final EvidenceResolutionGenerationService generations;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvidenceTaskCreatedHandler(
            InboxService inbox,
            TaskFulfillmentContextService tasks,
            EvidenceResolutionGenerationService generations,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.inbox = inbox;
        this.tasks = tasks;
        this.generations = generations;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "task.created".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        validateEnvelope(message);
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        TaskCreatedPayload created = read(message.payload());
        if (!created.taskId().toString().equals(message.aggregateId())) {
            throw new IllegalArgumentException("TaskCreated aggregateId 与 payload 不一致");
        }
        TaskFulfillmentContext task = tasks.find(message.tenantId(), created.taskId())
                .orElseThrow(() -> new IllegalStateException("TaskCreated 对应 Task 不存在"));
        validateContext(created, task);

        EvidenceResolutionApplyResult result = generations.apply(
                message.tenantId(), task, message.eventId(), message.payloadDigest(),
                "TASK_CREATED", message.eventId().toString(), 0, Map.of());
        Instant now = clock.instant();
        String requestDigest = Sha256.digest(message.payloadDigest() + "|"
                + task.configurationBundleDigest() + "|" + result.outcome());
        if (result.applied()) {
            EvidenceTaskResolution resolution = result.resolution();
            String eventPayload = write(new EvidenceSlotsResolvedPayload(
                    resolution.resolutionId(), task.taskId(), task.projectId(),
                    task.configurationBundleId(), task.configurationBundleDigest(), task.stageCode(),
                    result.activeSlotCount(), now));
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.slots-resolved", 1,
                    "EvidenceTaskResolution", resolution.resolutionId().toString(),
                    resolution.generationNo(), message.tenantId(), message.correlationId(),
                    message.eventId().toString(), task.taskId().toString(),
                    eventPayload, Sha256.digest(eventPayload), now));
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR, "EVIDENCE_SLOTS_RESOLVED", null,
                "Task", task.taskId().toString(), "SYSTEM", List.of(),
                EvidenceTemplateResolver.VERSION, result.outcome(), null, requestDigest,
                message.correlationId(), now));
        String resultRef = result.applied()
                ? result.resolution().resolutionId().toString() : result.outcome();
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(resultRef + "|" + result.activeSlotCount() + "|" + requestDigest));
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("不支持的 TaskCreated 事件信封");
        }
    }

    private static void validateContext(TaskCreatedPayload created, TaskFulfillmentContext task) {
        if (!created.projectId().equals(task.projectId())
                || !created.workOrderId().equals(task.workOrderId())) {
            throw new IllegalArgumentException("TaskCreated 所有权与 Task 上下文不一致");
        }
        if (task.configurationBundleId() == null || task.configurationBundleDigest() == null
                || task.stageCode() == null) {
            throw new IllegalStateException("Workflow Task 缺少冻结的资料解析上下文");
        }
    }

    private TaskCreatedPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, TaskCreatedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("TaskCreated payload 无法解析", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence 事件无法序列化", exception);
        }
    }

    private record EvidenceSlotsResolvedPayload(
            UUID resolutionId,
            UUID taskId,
            UUID projectId,
            UUID configurationBundleId,
            String configurationBundleDigest,
            String stageCode,
            int slotCount,
            Instant resolvedAt
    ) {
    }
}
