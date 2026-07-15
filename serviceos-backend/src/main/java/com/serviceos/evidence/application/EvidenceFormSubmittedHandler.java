package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.forms.api.FormSubmissionFacts;
import com.serviceos.forms.api.FormSubmissionFactsQuery;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.api.OutboxAppender;
import com.serviceos.reliability.api.OutboxEvent;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** VALIDATED form.submitted@v1 的可靠消费者；完整 values 只经 forms 公开只读端口获取。 */
@Service
final class EvidenceFormSubmittedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "evidence.form-submitted.reresolution.v1";
    private static final String SYSTEM_ACTOR = "system:evidence-slot-reresolver";

    private final InboxService inbox;
    private final FormSubmissionFactsQuery forms;
    private final TaskFulfillmentContextService tasks;
    private final EvidenceResolutionGenerationService generations;
    private final AuditAppender audit;
    private final OutboxAppender outbox;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    EvidenceFormSubmittedHandler(
            InboxService inbox,
            FormSubmissionFactsQuery forms,
            TaskFulfillmentContextService tasks,
            EvidenceResolutionGenerationService generations,
            AuditAppender audit,
            OutboxAppender outbox,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.inbox = inbox;
        this.forms = forms;
        this.tasks = tasks;
        this.generations = generations;
        this.audit = audit;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "form.submitted".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        validateEnvelope(message);
        InboxDecision inboxDecision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (inboxDecision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }
        FormSubmittedPayload submitted = read(message.payload());
        validatePayloadEnvelope(message, submitted);
        Instant now = clock.instant();
        if (!"VALIDATED".equals(submitted.validationStatus())) {
            completeWithoutChange(message, submitted, "INVALID_NO_CHANGE", now);
            return;
        }

        FormSubmissionFacts facts = forms.findValidated(message.tenantId(), submitted.submissionId())
                .orElseThrow(() -> new IllegalStateException(
                        "form.submitted 标记 VALIDATED 但权威 submission 不存在: " + submitted.submissionId()));
        validateFacts(submitted, facts);
        TaskFulfillmentContext task = tasks.find(message.tenantId(), submitted.taskId())
                .orElseThrow(() -> new IllegalStateException(
                        "form.submitted 对应 Task 不存在: " + submitted.taskId()));
        if (!task.projectId().equals(submitted.projectId())) {
            throw new IllegalArgumentException("form.submitted projectId 与 Task 不一致");
        }

        EvidenceResolutionApplyResult result = generations.apply(
                message.tenantId(), task, message.eventId(), message.payloadDigest(),
                "FORM_SUBMISSION", submitted.submissionId().toString(),
                submitted.submissionVersion(), generations.readFormValues(facts.valuesJson()));
        String requestDigest = Sha256.digest(message.payloadDigest() + "|"
                + facts.contentDigest() + "|" + result.outcome());
        if (result.applied()) {
            EvidenceTaskResolution resolution = result.resolution();
            String payload = write(new EvidenceSlotsReresolvedPayload(
                    resolution.resolutionId(), resolution.previousResolutionId(), task.taskId(),
                    task.projectId(), resolution.generationNo(), resolution.conditionFactType(),
                    resolution.conditionFactRef(), resolution.conditionFactRevision(),
                    result.activeSlotCount(), result.activatedCount(), result.deactivatedCount(),
                    result.reviewRequiredCount(), now));
            outbox.append(new OutboxEvent(
                    UUID.randomUUID(), UUID.randomUUID(), "evidence",
                    "evidence.slots-reresolved", 1, "EvidenceTaskResolution",
                    resolution.resolutionId().toString(), resolution.generationNo(),
                    message.tenantId(), message.correlationId(), message.eventId().toString(),
                    task.taskId().toString(), payload, Sha256.digest(payload), now));
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "EVIDENCE_SLOTS_RERESOLVED", null, "Task", task.taskId().toString(),
                "SYSTEM", List.of(), EvidenceTemplateResolver.VERSION, result.outcome(), null,
                requestDigest, message.correlationId(), now));
        String resultRef = result.applied()
                ? result.resolution().resolutionId().toString() : result.outcome();
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(resultRef + "|" + result.activeSlotCount() + "|" + requestDigest));
    }

    private void completeWithoutChange(
            OutboxMessage message,
            FormSubmittedPayload submitted,
            String outcome,
            Instant now
    ) {
        String digest = Sha256.digest(message.payloadDigest() + "|" + outcome);
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "EVIDENCE_SLOTS_RERESOLUTION_SKIPPED", null, "Task",
                submitted.taskId().toString(), "SYSTEM", List.of(),
                EvidenceTemplateResolver.VERSION, outcome, null, digest,
                message.correlationId(), now));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(), digest);
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"forms".equals(message.module()) || !"FormSubmission".equals(message.aggregateType())) {
            throw new IllegalArgumentException("不支持的 FormSubmitted 事件信封");
        }
    }

    private static void validatePayloadEnvelope(OutboxMessage message, FormSubmittedPayload payload) {
        if (!payload.submissionId().toString().equals(message.aggregateId())
                || !"form.submitted".equals(payload.eventType())) {
            throw new IllegalArgumentException("FormSubmitted 信封与 payload 不一致");
        }
    }

    private static void validateFacts(FormSubmittedPayload payload, FormSubmissionFacts facts) {
        if (!facts.submissionId().equals(payload.submissionId())
                || !facts.taskId().equals(payload.taskId())
                || !facts.projectId().equals(payload.projectId())
                || !facts.formVersionId().equals(payload.formVersionId())
                || !facts.formKey().equals(payload.formKey())
                || facts.submissionVersion() != payload.submissionVersion()
                || !facts.contentDigest().equals(payload.contentDigest())) {
            throw new IllegalArgumentException("FormSubmitted payload 与权威 submission facts 不一致");
        }
    }

    private FormSubmittedPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, FormSubmittedPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("FormSubmitted payload 无法解析", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence 重解析事件无法序列化", exception);
        }
    }

    private record FormSubmittedPayload(
            UUID submissionId,
            UUID taskId,
            UUID projectId,
            UUID formVersionId,
            String formKey,
            int submissionVersion,
            String contentDigest,
            String validationStatus,
            int errorCount,
            int warningCount,
            Instant occurredAt,
            String eventType
    ) {
    }

    private record EvidenceSlotsReresolvedPayload(
            UUID resolutionId,
            UUID previousResolutionId,
            UUID taskId,
            UUID projectId,
            int generationNo,
            String conditionFactType,
            String conditionFactRef,
            int conditionFactRevision,
            int activeSlotCount,
            long activatedCount,
            long deactivatedCount,
            long reviewRequiredCount,
            Instant occurredAt
    ) {
    }
}
