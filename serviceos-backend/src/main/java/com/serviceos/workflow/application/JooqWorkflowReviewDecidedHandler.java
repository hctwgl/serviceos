package com.serviceos.workflow.application;

import com.serviceos.jooq.generated.tables.WflReviewGateEarlySignal;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.workflow.api.ReviewGateWait;
import com.serviceos.workflow.api.SignalWorkflowWaitCommand;
import com.serviceos.workflow.api.WorkflowWaitSignalService;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.WflReviewGateEarlySignal.WFL_REVIEW_GATE_EARLY_SIGNAL;
import static com.serviceos.jooq.generated.tables.WflWaitSubscription.WFL_WAIT_SUBSCRIPTION;
import static org.jooq.impl.DSL.excluded;

/**
 * M365 A5-B：消费 {@code evidence.review-decided}，在 APPROVED / FORCE_APPROVED 时
 * 唤醒 REVIEW_TASK 编排门闸（或写入早期信号供门闸激活时消费）。
 *
 * <p>REJECTED 不推进（整改/复审仍停留在门闸）。不修改 ReviewCase.reviewTaskId 绑定语义。</p>
 */
@Service
final class JooqWorkflowReviewDecidedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.evidence-review-decided.v1";
    private static final Set<String> PASSING = Set.of("APPROVED", "FORCE_APPROVED");

    private final DSLContext dsl;
    private final InboxService inbox;
    private final TaskFulfillmentContextService taskContexts;
    private final WorkflowWaitSignalService waitSignals;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqWorkflowReviewDecidedHandler(
            DSLContext dsl,
            InboxService inbox,
            TaskFulfillmentContextService taskContexts,
            WorkflowWaitSignalService waitSignals,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
        this.inbox = inbox;
        this.taskContexts = taskContexts;
        this.waitSignals = waitSignals;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "evidence.review-decided".equals(eventType) && schemaVersion == 1;
    }

    @Override
    @Transactional
    public void handle(OutboxMessage message) {
        InboxDecision inboxDecision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (inboxDecision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        ReviewDecidedPayload decided = readPayload(message.payload());
        if (!PASSING.contains(decided.decision())) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest("IGNORED|" + decided.decision()));
            return;
        }

        TaskFulfillmentContext sourceTask = taskContexts
                .find(message.tenantId(), decided.taskId())
                .orElseThrow(() -> new IllegalStateException(
                        "Review decided source Task missing for workflow gate: " + decided.taskId()));
        if (sourceTask.workOrderId() == null) {
            throw new IllegalStateException(
                    "Review decided source Task has no workOrderId: " + decided.taskId());
        }

        Instant now = clock.instant();
        String signalId = decided.reviewDecisionId().toString();
        // 先落早期信号，再尝试唤醒；门闸尚未激活时保留 token，激活时由 JooqWorkflowTaskCompletedHandler 消费。
        upsertEarlySignal(
                message.tenantId(), sourceTask.workOrderId(), decided.reviewCaseId(),
                decided.reviewDecisionId(), decided.decision(), signalId,
                message.correlationId(), now);

        String correlationKey = "workOrder:" + sourceTask.workOrderId();
        boolean waiting = dsl.fetchExists(
                dsl.selectOne()
                        .from(WFL_WAIT_SUBSCRIPTION)
                        .where(WFL_WAIT_SUBSCRIPTION.TENANT_ID.eq(message.tenantId()))
                        .and(WFL_WAIT_SUBSCRIPTION.WAIT_EVENT_TYPE.eq(ReviewGateWait.WAIT_EVENT_TYPE))
                        .and(WFL_WAIT_SUBSCRIPTION.CORRELATION_KEY.eq(correlationKey))
                        .and(WFL_WAIT_SUBSCRIPTION.STATUS.eq("WAITING")));
        if (waiting) {
            waitSignals.signal(new SignalWorkflowWaitCommand(
                    message.tenantId(), ReviewGateWait.WAIT_EVENT_TYPE, correlationKey,
                    signalId, message.correlationId()));
            WflReviewGateEarlySignal signal = WFL_REVIEW_GATE_EARLY_SIGNAL;
            dsl.update(signal)
                    .set(signal.CONSUMED_AT, now)
                    .where(signal.TENANT_ID.eq(message.tenantId()))
                    .and(signal.WORK_ORDER_ID.eq(sourceTask.workOrderId()))
                    .and(signal.CONSUMED_AT.isNull())
                    .execute();
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest("WOKE|" + sourceTask.workOrderId()));
            return;
        }

        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest("PARKED|" + sourceTask.workOrderId()));
    }

    private void upsertEarlySignal(
            String tenantId,
            UUID workOrderId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            String decision,
            String signalId,
            String correlationId,
            Instant createdAt
    ) {
        WflReviewGateEarlySignal signal = WFL_REVIEW_GATE_EARLY_SIGNAL;
        // 冲突即同工单已有早期信号：整行替换为最新决定，并重新置为未消费。
        dsl.insertInto(signal)
                .set(signal.TENANT_ID, tenantId)
                .set(signal.WORK_ORDER_ID, workOrderId)
                .set(signal.REVIEW_CASE_ID, reviewCaseId)
                .set(signal.REVIEW_DECISION_ID, reviewDecisionId)
                .set(signal.DECISION, decision)
                .set(signal.SIGNAL_ID, signalId)
                .set(signal.CORRELATION_ID, correlationId)
                .set(signal.CREATED_AT, createdAt)
                .setNull(signal.CONSUMED_AT)
                .onConflict(signal.TENANT_ID, signal.WORK_ORDER_ID)
                .doUpdate()
                .set(signal.REVIEW_CASE_ID, excluded(signal.REVIEW_CASE_ID))
                .set(signal.REVIEW_DECISION_ID, excluded(signal.REVIEW_DECISION_ID))
                .set(signal.DECISION, excluded(signal.DECISION))
                .set(signal.SIGNAL_ID, excluded(signal.SIGNAL_ID))
                .set(signal.CORRELATION_ID, excluded(signal.CORRELATION_ID))
                .set(signal.CREATED_AT, excluded(signal.CREATED_AT))
                .setNull(signal.CONSUMED_AT)
                .execute();
    }

    private ReviewDecidedPayload readPayload(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                payload = root;
            }
            return new ReviewDecidedPayload(
                    UUID.fromString(payload.path("reviewCaseId").asText()),
                    UUID.fromString(payload.path("reviewDecisionId").asText()),
                    UUID.fromString(payload.path("taskId").asText()),
                    payload.path("decision").asText());
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("evidence.review-decided payload is invalid", exception);
        }
    }

    private record ReviewDecidedPayload(
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID taskId,
            String decision
    ) {
    }
}
