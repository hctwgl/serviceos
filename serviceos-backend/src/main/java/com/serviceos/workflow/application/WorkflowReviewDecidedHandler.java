package com.serviceos.workflow.application;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 消费 {@code evidence.review-decided}，按审核来源推进不同的工作流等待点。
 *
 * <p>内部审核通过唤醒 REVIEW_TASK 门闸；如果审核先于门闸激活，则保存早期信号。
 * 外部车企复核通过只允许唤醒已经激活的 OEM 回调等待点，不保存早期信号：
 * 外部回执只能发生在平台完成提审之后，等待点不存在代表链路状态不一致，必须失败并由
 * Outbox 重试或人工接管，不能把回执静默标记为已消费。</p>
 *
 * <p>REJECTED 不推进（整改/复审仍停留在对应门闸）。不修改 ReviewCase.reviewTaskId 绑定语义。</p>
 */
@Service
final class WorkflowReviewDecidedHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "workflow.evidence-review-decided.v1";
    private static final String INTERNAL = "INTERNAL";
    private static final String EXTERNAL = "EXTERNAL";
    private static final String OEM_ACKNOWLEDGED_EVENT_TYPE = "platform.oem.acknowledged";
    private static final Set<String> PASSING = Set.of("APPROVED", "FORCE_APPROVED");

    private final JdbcClient jdbc;
    private final InboxService inbox;
    private final TaskFulfillmentContextService taskContexts;
    private final WorkflowWaitSignalService waitSignals;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    WorkflowReviewDecidedHandler(
            JdbcClient jdbc,
            InboxService inbox,
            TaskFulfillmentContextService taskContexts,
            WorkflowWaitSignalService waitSignals,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
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

        if (EXTERNAL.equals(decided.decisionSource())) {
            wakeExternalReviewWait(message, decided, sourceTask.workOrderId());
            return;
        }
        if (!INTERNAL.equals(decided.decisionSource())) {
            throw new IllegalArgumentException(
                    "Unsupported review decisionSource: " + decided.decisionSource());
        }

        Instant now = clock.instant();
        String signalId = decided.reviewDecisionId().toString();
        // 先落早期信号，再尝试唤醒；门闸尚未激活时保留 token，激活时由 WorkflowTaskCompletedHandler 消费。
        upsertEarlySignal(
                message.tenantId(), sourceTask.workOrderId(), decided.reviewCaseId(),
                decided.reviewDecisionId(), decided.decision(), signalId,
                message.correlationId(), now);

        String correlationKey = "workOrder:" + sourceTask.workOrderId();
        boolean waiting = jdbc.sql("""
                        SELECT 1
                          FROM wfl_wait_subscription
                         WHERE tenant_id = :tenantId
                           AND wait_event_type = :waitEventType
                           AND correlation_key = :correlationKey
                           AND status = 'WAITING'
                         LIMIT 1
                        """)
                .param("tenantId", message.tenantId())
                .param("waitEventType", ReviewGateWait.WAIT_EVENT_TYPE)
                .param("correlationKey", correlationKey)
                .query(Integer.class)
                .optional()
                .isPresent();
        if (waiting) {
            waitSignals.signal(new SignalWorkflowWaitCommand(
                    message.tenantId(), ReviewGateWait.WAIT_EVENT_TYPE, correlationKey,
                    signalId, message.correlationId()));
            jdbc.sql("""
                            UPDATE wfl_review_gate_early_signal
                               SET consumed_at = :consumedAt
                             WHERE tenant_id = :tenantId
                               AND work_order_id = :workOrderId
                               AND consumed_at IS NULL
                            """)
                    .param("consumedAt", java.sql.Timestamp.from(now))
                    .param("tenantId", message.tenantId())
                    .param("workOrderId", sourceTask.workOrderId())
                    .update();
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest("WOKE|" + sourceTask.workOrderId()));
            return;
        }

        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest("PARKED|" + sourceTask.workOrderId()));
    }

    private void wakeExternalReviewWait(
            OutboxMessage message,
            ReviewDecidedPayload decided,
            UUID workOrderId
    ) {
        String correlationKey = "workOrder:" + workOrderId;
        boolean waiting = hasWaitingSubscription(
                message.tenantId(), OEM_ACKNOWLEDGED_EVENT_TYPE, correlationKey);
        if (!waiting) {
            throw new IllegalStateException(
                    "External review approved but OEM acknowledgement wait is not active: "
                            + workOrderId);
        }
        waitSignals.signal(new SignalWorkflowWaitCommand(
                message.tenantId(), OEM_ACKNOWLEDGED_EVENT_TYPE, correlationKey,
                decided.reviewDecisionId().toString(), message.correlationId()));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest("WOKE_EXTERNAL|" + workOrderId));
    }

    private boolean hasWaitingSubscription(
            String tenantId,
            String waitEventType,
            String correlationKey
    ) {
        return jdbc.sql("""
                        SELECT 1
                          FROM wfl_wait_subscription
                         WHERE tenant_id = :tenantId
                           AND wait_event_type = :waitEventType
                           AND correlation_key = :correlationKey
                           AND status = 'WAITING'
                         LIMIT 1
                        """)
                .param("tenantId", tenantId)
                .param("waitEventType", waitEventType)
                .param("correlationKey", correlationKey)
                .query(Integer.class)
                .optional()
                .isPresent();
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
        jdbc.sql("""
                        INSERT INTO wfl_review_gate_early_signal (
                            tenant_id, work_order_id, review_case_id, review_decision_id,
                            decision, signal_id, correlation_id, created_at, consumed_at
                        ) VALUES (
                            :tenantId, :workOrderId, :reviewCaseId, :reviewDecisionId,
                            :decision, :signalId, :correlationId, :createdAt, NULL
                        )
                        ON CONFLICT (tenant_id, work_order_id) DO UPDATE
                           SET review_case_id = EXCLUDED.review_case_id,
                               review_decision_id = EXCLUDED.review_decision_id,
                               decision = EXCLUDED.decision,
                               signal_id = EXCLUDED.signal_id,
                               correlation_id = EXCLUDED.correlation_id,
                               created_at = EXCLUDED.created_at,
                               consumed_at = NULL
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .param("reviewCaseId", reviewCaseId)
                .param("reviewDecisionId", reviewDecisionId)
                .param("decision", decision)
                .param("signalId", signalId)
                .param("correlationId", correlationId)
                .param("createdAt", java.sql.Timestamp.from(createdAt))
                .update();
    }

    private ReviewDecidedPayload readPayload(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                payload = root;
            }
            String decisionSource = payload.path("decisionSource").asText();
            // v1 早期内部审核事件未携带 decisionSource；已发布契约中该字段为可选。
            // 缺失只能解释为历史 INTERNAL 语义，EXTERNAL 必须始终显式声明。
            if (decisionSource.isBlank()) {
                decisionSource = INTERNAL;
            }
            return new ReviewDecidedPayload(
                    UUID.fromString(payload.path("reviewCaseId").asText()),
                    UUID.fromString(payload.path("reviewDecisionId").asText()),
                    UUID.fromString(payload.path("taskId").asText()),
                    payload.path("decision").asText(),
                    decisionSource);
        } catch (JacksonException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("evidence.review-decided payload is invalid", exception);
        }
    }

    private record ReviewDecidedPayload(
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID taskId,
            String decision,
            String decisionSource
    ) {
    }
}
