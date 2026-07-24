package com.serviceos.evidence.application;

import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * 审核门闸激活后，为触发该门闸的不可变资料快照创建总部审核单和独立审核任务。
 *
 * <p>工作流只发布稳定事实，不依赖 evidence；本消费者在同一事务内完成 Inbox、
 * ReviewCase、审核 Task、审计和 Outbox，失败时整体回滚并由可靠消息重试。</p>
 */
@Service
final class WorkflowReviewSubmissionRequiredHandler implements OutboxMessageHandler {
    private static final String CONSUMER = "evidence.workflow-review-submission-required.v1";
    private static final String SYSTEM_ACTOR = "system:workflow-review-bootstrap";

    private final InboxService inbox;
    private final ReviewCaseHandlingBootstrap reviewBootstrap;
    private final ObjectMapper objectMapper;

    WorkflowReviewSubmissionRequiredHandler(
            InboxService inbox,
            ReviewCaseHandlingBootstrap reviewBootstrap,
            ObjectMapper objectMapper
    ) {
        this.inbox = inbox;
        this.reviewBootstrap = reviewBootstrap;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String eventType, int schemaVersion) {
        return "workflow.review-submission-required".equals(eventType) && schemaVersion == 1;
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

        ReviewSubmissionRequiredPayload payload = read(message.payload());
        validatePayload(message, payload);
        var review = reviewBootstrap.openInternalForSnapshot(
                message.tenantId(), SYSTEM_ACTOR, message.correlationId(),
                message.eventId().toString(), payload.evidenceSetSnapshotId(), null);
        inbox.complete(
                message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(review.reviewCaseId() + "|" + review.reviewTaskId()
                        + "|" + payload.snapshotContentDigest()));
    }

    private static void validateEnvelope(OutboxMessage message) {
        if (!"workflow".equals(message.module())
                || !"Workflow".equals(message.aggregateType())) {
            throw new IllegalArgumentException("不支持的审核提交事件信封");
        }
    }

    private static void validatePayload(
            OutboxMessage message,
            ReviewSubmissionRequiredPayload payload
    ) {
        if (!payload.workflowInstanceId().toString().equals(message.aggregateId())
                || payload.projectId() == null
                || payload.workOrderId() == null
                || payload.sourceTaskId() == null
                || payload.reviewNodeInstanceId() == null
                || payload.evidenceSetSnapshotId() == null
                || payload.snapshotContentDigest() == null
                || !payload.snapshotContentDigest().matches("[0-9a-f]{64}")
                || payload.requiredAt() == null) {
            throw new IllegalArgumentException("审核提交事件信封与 payload 不一致");
        }
    }

    private ReviewSubmissionRequiredPayload read(String payload) {
        try {
            return objectMapper.readValue(payload, ReviewSubmissionRequiredPayload.class);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("审核提交事件 payload 无法解析", exception);
        }
    }

    private record ReviewSubmissionRequiredPayload(
            UUID workflowInstanceId,
            UUID reviewNodeInstanceId,
            UUID projectId,
            UUID workOrderId,
            UUID sourceTaskId,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            Instant requiredAt
    ) {
    }
}
