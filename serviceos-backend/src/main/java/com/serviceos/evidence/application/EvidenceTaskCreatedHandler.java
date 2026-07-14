package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.evidence.api.EvidenceSlotView;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** task.created@v1 的可靠消费者，在一个事务中冻结 slot、审计、事件和 Inbox 结果。 */
@Service
final class EvidenceTaskCreatedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "evidence.task-created.fixed-slots.v1";
    private static final String SYSTEM_ACTOR = "system:evidence-slot-resolver";

    private final InboxService inbox;
    private final TaskFulfillmentContextService tasks;
    private final ConfigurationService configurations;
    private final EvidenceTemplateResolver resolver;
    private final EvidenceSlotRepository repository;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvidenceTaskCreatedHandler(
            InboxService inbox, TaskFulfillmentContextService tasks,
            ConfigurationService configurations, EvidenceTemplateResolver resolver,
            EvidenceSlotRepository repository, AuditAppender audit,
            OutboxAppender outbox, ObjectMapper objectMapper, Clock clock
    ) {
        this.inbox = inbox;
        this.tasks = tasks;
        this.configurations = configurations;
        this.resolver = resolver;
        this.repository = repository;
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
            throw new IllegalArgumentException("TaskCreated aggregateId does not match payload");
        }
        TaskFulfillmentContext task = tasks.find(message.tenantId(), created.taskId())
                .orElseThrow(() -> new IllegalStateException("TaskCreated task does not exist"));
        validateContext(created, task);

        List<ConfigurationAssetDefinition> templates = configurations.listBundleAssets(
                message.tenantId(), task.configurationBundleId(), task.configurationBundleDigest(),
                ConfigurationAssetType.EVIDENCE);
        List<ResolvedEvidenceRequirement> requirements = templates.stream()
                .flatMap(template -> resolver.resolve(template, task.stageCode()).stream())
                .toList();

        Instant now = clock.instant();
        UUID resolutionId = UUID.randomUUID();
        List<EvidenceSlotView> slots = new ArrayList<>();
        for (ResolvedEvidenceRequirement requirement : requirements) {
            slots.add(new EvidenceSlotView(
                    UUID.randomUUID(), resolutionId, task.taskId(), task.projectId(),
                    requirement.templateVersionId(), requirement.templateKey(),
                    requirement.templateVersion(), requirement.templateDigest(),
                    requirement.requirementCode(), "default", requirement.requirementName(),
                    requirement.mediaType(), requirement.required(), requirement.minCount(),
                    requirement.maxCount(), requirement.conditionInputDigest(),
                    requirement.resolutionExplanationJson(), requirement.requirementDefinitionJson(),
                    requirement.requirementDigest(),
                    requirement.minCount() > 0 ? "MISSING" : "SATISFIED", now));
        }

        EvidenceTaskResolution resolution = new EvidenceTaskResolution(
                resolutionId, message.tenantId(), task.projectId(), task.taskId(),
                task.configurationBundleId(), task.configurationBundleDigest(), task.stageCode(),
                message.eventId(), message.payloadDigest(), EvidenceTemplateResolver.VERSION,
                now, slots);
        repository.insert(resolution);

        String requestDigest = Sha256.digest(message.payloadDigest() + "|"
                + task.configurationBundleDigest() + "|"
                + templates.stream().map(ConfigurationAssetDefinition::contentDigest)
                .reduce((left, right) -> left + "|" + right).orElse(""));
        String eventPayload = write(new EvidenceSlotsResolvedPayload(
                resolutionId, task.taskId(), task.projectId(), task.configurationBundleId(),
                task.configurationBundleDigest(), task.stageCode(), slots.size(), now));
        outbox.append(new OutboxEvent(
                UUID.randomUUID(), UUID.randomUUID(), "evidence", "evidence.slots-resolved", 1,
                "EvidenceTaskResolution", resolutionId.toString(), 1,
                message.tenantId(), message.correlationId(), message.eventId().toString(),
                task.taskId().toString(), eventPayload, Sha256.digest(eventPayload), now));
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR, "EVIDENCE_SLOTS_RESOLVED", null,
                "Task", task.taskId().toString(), "SYSTEM", List.of(),
                EvidenceTemplateResolver.VERSION, "RESOLVED", null, requestDigest,
                message.correlationId(), now));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(resolutionId + "|" + slots.size() + "|" + requestDigest));
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"task".equals(message.module()) || !"Task".equals(message.aggregateType())) {
            throw new IllegalArgumentException("unsupported TaskCreated envelope");
        }
    }

    private static void validateContext(TaskCreatedPayload created, TaskFulfillmentContext task) {
        if (!created.projectId().equals(task.projectId())
                || !created.workOrderId().equals(task.workOrderId())) {
            throw new IllegalArgumentException("TaskCreated ownership does not match Task context");
        }
        if (task.configurationBundleId() == null || task.configurationBundleDigest() == null
                || task.stageCode() == null) {
            throw new IllegalStateException("Workflow Task has incomplete frozen evidence context");
        }
    }

    private TaskCreatedPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, TaskCreatedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("TaskCreated payload cannot be decoded", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence event serialization failed", exception);
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
