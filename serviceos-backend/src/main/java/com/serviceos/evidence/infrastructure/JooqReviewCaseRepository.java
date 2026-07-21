package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.ReviewCaseQueueItem;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;
import com.serviceos.evidence.application.ReviewCaseRepository;
import com.serviceos.evidence.application.ReviewCaseTimelineIdentity;
import com.serviceos.jooq.generated.tables.EvdReviewCase;
import com.serviceos.jooq.generated.tables.EvdReviewDecision;
import com.serviceos.jooq.generated.tables.records.EvdReviewCaseRecord;
import com.serviceos.jooq.generated.tables.records.EvdReviewDecisionRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.EvdReviewCase.EVD_REVIEW_CASE;
import static com.serviceos.jooq.generated.tables.EvdReviewCommandResult.EVD_REVIEW_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.EvdReviewDecision.EVD_REVIEW_DECISION;
import static com.serviceos.jooq.generated.tables.EvdReviewTargetDecision.EVD_REVIEW_TARGET_DECISION;

/**
 * ReviewCase / ReviewDecision 持久化的 jOOQ 实现（取代 MyBatis Mapper + XML）。
 *
 * <p>语义等价要点：状态迁移 UPDATE 携带原状态与 aggregate_version 条件并返回影响行数；
 * 队列游标为 (created_at, review_case_id) 行值比较；非全租户视角且无授权项目时退化为
 * AND FALSE；最新决定用 LEFT JOIN LATERAL 逐案例取 decision_ordinal 最大一行。</p>
 */
@Repository
final class JooqReviewCaseRepository implements ReviewCaseRepository {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqReviewCaseRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insertCase(String tenantId, ReviewCaseView reviewCase) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        dsl.insertInto(review)
                .set(review.REVIEW_CASE_ID, reviewCase.reviewCaseId())
                .set(review.TENANT_ID, tenantId)
                .set(review.PROJECT_ID, reviewCase.projectId())
                .set(review.TASK_ID, reviewCase.taskId())
                .set(review.REVIEW_TASK_ID, reviewCase.reviewTaskId())
                .set(review.EVIDENCE_SET_SNAPSHOT_ID, reviewCase.evidenceSetSnapshotId())
                .set(review.SNAPSHOT_CONTENT_DIGEST, reviewCase.snapshotContentDigest())
                .set(review.SCOPE_TYPE, reviewCase.scopeType())
                .set(review.ORIGIN, reviewCase.origin())
                .set(review.POLICY_VERSION, reviewCase.policyVersion())
                .set(review.STATUS, reviewCase.status())
                .set(review.CREATED_BY, reviewCase.createdBy())
                .set(review.CREATED_AT, reviewCase.createdAt())
                .set(review.DECIDED_AT, reviewCase.decidedAt())
                .set(review.SOURCE_REVIEW_CASE_ID, reviewCase.sourceReviewCaseId())
                .set(review.EXTERNAL_SUBMISSION_REF, reviewCase.externalSubmissionRef())
                .set(review.CALLBACK_BATCH_REF, reviewCase.callbackBatchRef())
                .set(review.MAPPING_VERSION_ID, reviewCase.mappingVersionId())
                .set(review.REOPENED_FROM_REVIEW_CASE_ID, reviewCase.reopenedFromReviewCaseId())
                .set(review.REOPEN_TRIGGER_REF, reviewCase.reopenTriggerRef())
                .set(review.AGGREGATE_VERSION,
                        reviewCase.aggregateVersion() <= 0 ? 1L : reviewCase.aggregateVersion())
                .execute();
    }

    @Override
    public int markDecided(
            String tenantId,
            UUID reviewCaseId,
            String expectedStatus,
            long expectedAggregateVersion,
            String status,
            Instant decidedAt
    ) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        // 并发防线：只有状态与版本均未漂移才允许写入决定结果；影响行数由调用方校验。
        return dsl.update(review)
                .set(review.STATUS, status)
                .set(review.DECIDED_AT, decidedAt)
                .set(review.AGGREGATE_VERSION, review.AGGREGATE_VERSION.plus(1))
                .where(review.TENANT_ID.eq(tenantId))
                .and(review.REVIEW_CASE_ID.eq(reviewCaseId))
                .and(review.STATUS.eq(expectedStatus))
                .and(review.AGGREGATE_VERSION.eq(expectedAggregateVersion))
                .execute();
    }

    @Override
    public int markReopened(String tenantId, UUID reviewCaseId, String expectedStatus) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        return dsl.update(review)
                .set(review.STATUS, "REOPENED")
                .where(review.TENANT_ID.eq(tenantId))
                .and(review.REVIEW_CASE_ID.eq(reviewCaseId))
                .and(review.STATUS.eq(expectedStatus))
                .execute();
    }

    @Override
    public void insertDecision(String tenantId, UUID projectId, ReviewDecisionView decision) {
        EvdReviewDecision reviewDecision = EVD_REVIEW_DECISION;
        dsl.insertInto(reviewDecision)
                .set(reviewDecision.REVIEW_DECISION_ID, decision.reviewDecisionId())
                .set(reviewDecision.TENANT_ID, tenantId)
                .set(reviewDecision.PROJECT_ID, projectId)
                .set(reviewDecision.REVIEW_CASE_ID, decision.reviewCaseId())
                .set(reviewDecision.DECISION_ORDINAL, decision.decisionOrdinal())
                .set(reviewDecision.DECISION, decision.decision())
                .set(reviewDecision.DECISION_SOURCE, decision.decisionSource())
                .set(reviewDecision.REASON_CODES, writeJson(decision.reasonCodes()))
                .set(reviewDecision.NOTE, decision.note())
                .set(reviewDecision.APPROVAL_REF, decision.approvalRef())
                .set(reviewDecision.DECIDED_BY, decision.decidedBy())
                .set(reviewDecision.DECIDED_AT, decision.decidedAt())
                .execute();
    }

    @Override
    public void insertTargetDecision(
            String tenantId,
            UUID projectId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            String targetType,
            UUID targetId,
            int targetVersion,
            String decision,
            List<String> reasonCodes,
            String note,
            Instant createdAt
    ) {
        dsl.insertInto(EVD_REVIEW_TARGET_DECISION)
                .set(EVD_REVIEW_TARGET_DECISION.REVIEW_TARGET_DECISION_ID, UUID.randomUUID())
                .set(EVD_REVIEW_TARGET_DECISION.TENANT_ID, tenantId)
                .set(EVD_REVIEW_TARGET_DECISION.PROJECT_ID, projectId)
                .set(EVD_REVIEW_TARGET_DECISION.REVIEW_CASE_ID, reviewCaseId)
                .set(EVD_REVIEW_TARGET_DECISION.REVIEW_DECISION_ID, reviewDecisionId)
                .set(EVD_REVIEW_TARGET_DECISION.TARGET_TYPE, targetType)
                .set(EVD_REVIEW_TARGET_DECISION.TARGET_ID, targetId)
                .set(EVD_REVIEW_TARGET_DECISION.TARGET_VERSION, targetVersion)
                .set(EVD_REVIEW_TARGET_DECISION.DECISION, decision)
                .set(EVD_REVIEW_TARGET_DECISION.REASON_CODES,
                        writeJson(reasonCodes == null ? List.of() : reasonCodes))
                .set(EVD_REVIEW_TARGET_DECISION.NOTE, note)
                .set(EVD_REVIEW_TARGET_DECISION.CREATED_AT, createdAt)
                .execute();
    }

    @Override
    public Optional<ReviewCaseView> find(String tenantId, UUID reviewCaseId) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        return dsl.selectFrom(review)
                .where(review.TENANT_ID.eq(tenantId))
                .and(review.REVIEW_CASE_ID.eq(reviewCaseId))
                .fetchOptional()
                .map(row -> caseView(row, listDecisions(tenantId, reviewCaseId)));
    }

    @Override
    public List<ReviewCaseView> listByTask(String tenantId, UUID taskId) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        return dsl.selectFrom(review)
                .where(review.TENANT_ID.eq(tenantId))
                .and(review.TASK_ID.eq(taskId))
                .orderBy(review.CREATED_AT.asc(), review.REVIEW_CASE_ID.asc())
                .fetch(row -> caseView(row, listDecisions(tenantId, row.getReviewCaseId())));
    }

    @Override
    public List<ReviewCaseQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            String origin,
            UUID taskId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize
    ) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        EvdReviewDecision decision = EVD_REVIEW_DECISION;
        // 每案例最新一条决定：LATERAL 子查询按 decision_ordinal 倒序取 1 行；
        // decided_at 与案例列重名，取别名 latest_decided_at 后按别名读取。
        Table<?> latest = DSL.lateral(dsl.select(
                        decision.REVIEW_DECISION_ID,
                        decision.DECISION,
                        decision.DECISION_SOURCE,
                        decision.REASON_CODES,
                        decision.DECIDED_AT.as("latest_decided_at"))
                .from(decision)
                .where(decision.TENANT_ID.eq(review.TENANT_ID))
                .and(decision.REVIEW_CASE_ID.eq(review.REVIEW_CASE_ID))
                .orderBy(decision.DECISION_ORDINAL.desc())
                .limit(1)).as("decision");

        Condition condition = review.TENANT_ID.eq(tenantId).and(review.STATUS.eq(status));
        if (!tenantWide) {
            // 非全租户视角且无任何授权项目时结果必须为空（AND FALSE），不得退化为全量。
            condition = condition.and(projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : review.PROJECT_ID.in(projectIds));
        }
        if (origin != null) {
            condition = condition.and(review.ORIGIN.eq(origin));
        }
        if (taskId != null) {
            condition = condition.and(review.TASK_ID.eq(taskId));
        }
        if (cursorCreatedAt != null) {
            // 稳定游标：取严格大于 (created_at, review_case_id) 的下一页。
            condition = condition.and(DSL.row(review.CREATED_AT, review.REVIEW_CASE_ID)
                    .gt(cursorCreatedAt, cursorId));
        }
        return dsl.select(
                        review.REVIEW_CASE_ID,
                        review.PROJECT_ID,
                        review.TASK_ID,
                        review.REVIEW_TASK_ID,
                        review.EVIDENCE_SET_SNAPSHOT_ID,
                        review.SCOPE_TYPE,
                        review.ORIGIN,
                        review.POLICY_VERSION,
                        review.STATUS,
                        review.CREATED_AT,
                        review.DECIDED_AT,
                        review.SOURCE_REVIEW_CASE_ID,
                        review.EXTERNAL_SUBMISSION_REF,
                        review.CALLBACK_BATCH_REF,
                        review.MAPPING_VERSION_ID,
                        review.REOPENED_FROM_REVIEW_CASE_ID,
                        review.REOPEN_TRIGGER_REF,
                        latest.field(decision.REVIEW_DECISION_ID),
                        latest.field(decision.DECISION),
                        latest.field(decision.DECISION_SOURCE),
                        latest.field(decision.REASON_CODES),
                        latest.field("latest_decided_at", Instant.class))
                .from(review)
                .leftJoin(latest).on(DSL.trueCondition())
                .where(condition)
                .orderBy(review.CREATED_AT, review.REVIEW_CASE_ID)
                .limit(fetchSize)
                .fetch(this::queueItem);
    }

    @Override
    public Optional<ReviewCaseTimelineIdentity> findTimelineIdentity(String tenantId, UUID reviewCaseId) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        return dsl.select(review.REVIEW_CASE_ID, review.PROJECT_ID, review.TASK_ID)
                .from(review)
                .where(review.TENANT_ID.eq(tenantId))
                .and(review.REVIEW_CASE_ID.eq(reviewCaseId))
                .fetchOptional(row -> new ReviewCaseTimelineIdentity(row.value1(), row.value2(), row.value3()));
    }

    @Override
    public Optional<UUID> findActiveBySnapshot(String tenantId, UUID snapshotId, String origin) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        return dsl.select(review.REVIEW_CASE_ID)
                .from(review)
                .where(review.TENANT_ID.eq(tenantId))
                .and(review.EVIDENCE_SET_SNAPSHOT_ID.eq(snapshotId))
                .and(review.ORIGIN.eq(origin))
                .and(review.STATUS.ne("REOPENED"))
                .orderBy(review.CREATED_AT.desc())
                .limit(1)
                .fetchOptional(review.REVIEW_CASE_ID);
    }

    @Override
    public Optional<UUID> findClientByExternalSubmissionRef(String tenantId, String externalSubmissionRef) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        // 引用唯一约束保证至多一行；与原 MyBatis 单值映射一样，多于一行即失败。
        return dsl.select(review.REVIEW_CASE_ID)
                .from(review)
                .where(review.TENANT_ID.eq(tenantId))
                .and(review.ORIGIN.eq("CLIENT"))
                .and(review.EXTERNAL_SUBMISSION_REF.eq(externalSubmissionRef))
                .fetchOptional(review.REVIEW_CASE_ID);
    }

    @Override
    public Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey) {
        return dsl.select(EVD_REVIEW_COMMAND_RESULT.RESULT_ID)
                .from(EVD_REVIEW_COMMAND_RESULT)
                .where(EVD_REVIEW_COMMAND_RESULT.TENANT_ID.eq(tenantId))
                .and(EVD_REVIEW_COMMAND_RESULT.OPERATION_TYPE.eq(operationType))
                .and(EVD_REVIEW_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(EVD_REVIEW_COMMAND_RESULT.RESULT_ID);
    }

    @Override
    public void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId) {
        dsl.insertInto(EVD_REVIEW_COMMAND_RESULT)
                .set(EVD_REVIEW_COMMAND_RESULT.TENANT_ID, tenantId)
                .set(EVD_REVIEW_COMMAND_RESULT.OPERATION_TYPE, operationType)
                .set(EVD_REVIEW_COMMAND_RESULT.IDEMPOTENCY_KEY, idempotencyKey)
                .set(EVD_REVIEW_COMMAND_RESULT.RESULT_ID, resultId)
                .onConflict(
                        EVD_REVIEW_COMMAND_RESULT.TENANT_ID,
                        EVD_REVIEW_COMMAND_RESULT.OPERATION_TYPE,
                        EVD_REVIEW_COMMAND_RESULT.IDEMPOTENCY_KEY)
                .doNothing()
                .execute();
    }

    @Override
    public int nextDecisionOrdinal(String tenantId, UUID reviewCaseId) {
        EvdReviewDecision decision = EVD_REVIEW_DECISION;
        Integer max = dsl.select(DSL.coalesce(DSL.max(decision.DECISION_ORDINAL), 0))
                .from(decision)
                .where(decision.TENANT_ID.eq(tenantId))
                .and(decision.REVIEW_CASE_ID.eq(reviewCaseId))
                .fetchSingle()
                .value1();
        return max + 1;
    }

    private List<ReviewDecisionView> listDecisions(String tenantId, UUID reviewCaseId) {
        EvdReviewDecision decision = EVD_REVIEW_DECISION;
        return dsl.selectFrom(decision)
                .where(decision.TENANT_ID.eq(tenantId))
                .and(decision.REVIEW_CASE_ID.eq(reviewCaseId))
                .orderBy(decision.DECISION_ORDINAL)
                .fetch(this::decisionView);
    }

    private ReviewCaseView caseView(EvdReviewCaseRecord row, List<ReviewDecisionView> decisions) {
        return new ReviewCaseView(
                row.getReviewCaseId(), row.getProjectId(), row.getTaskId(), row.getReviewTaskId(),
                row.getEvidenceSetSnapshotId(), row.getSnapshotContentDigest(), row.getScopeType(),
                row.getOrigin(), row.getPolicyVersion(), row.getStatus(), row.getCreatedBy(),
                row.getCreatedAt(), row.getDecidedAt(), row.getSourceReviewCaseId(),
                row.getExternalSubmissionRef(), row.getCallbackBatchRef(), row.getMappingVersionId(),
                row.getReopenedFromReviewCaseId(), row.getReopenTriggerRef(), decisions,
                row.getAggregateVersion());
    }

    private ReviewDecisionView decisionView(EvdReviewDecisionRecord row) {
        return new ReviewDecisionView(
                row.getReviewDecisionId(), row.getReviewCaseId(), row.getDecisionOrdinal(),
                row.getDecision(), row.getDecisionSource(), readCodes(row.getReasonCodes()),
                row.getNote(), row.getApprovalRef(), row.getDecidedBy(), row.getDecidedAt());
    }

    private ReviewCaseQueueItem queueItem(Record row) {
        EvdReviewCase review = EVD_REVIEW_CASE;
        EvdReviewDecision decision = EVD_REVIEW_DECISION;
        String latestReasonCodes = row.get(decision.REASON_CODES);
        return new ReviewCaseQueueItem(
                row.get(review.REVIEW_CASE_ID), row.get(review.PROJECT_ID), row.get(review.TASK_ID),
                row.get(review.REVIEW_TASK_ID), row.get(review.EVIDENCE_SET_SNAPSHOT_ID),
                row.get(review.SCOPE_TYPE), row.get(review.ORIGIN), row.get(review.POLICY_VERSION),
                row.get(review.STATUS), row.get(review.CREATED_AT), row.get(review.DECIDED_AT),
                row.get(review.SOURCE_REVIEW_CASE_ID), row.get(review.EXTERNAL_SUBMISSION_REF),
                row.get(review.CALLBACK_BATCH_REF), row.get(review.MAPPING_VERSION_ID),
                row.get(review.REOPENED_FROM_REVIEW_CASE_ID), row.get(review.REOPEN_TRIGGER_REF),
                row.get(decision.REVIEW_DECISION_ID), row.get(decision.DECISION),
                row.get(decision.DECISION_SOURCE),
                latestReasonCodes == null ? List.of() : readCodes(latestReasonCodes),
                row.get("latest_decided_at", Instant.class));
    }

    private List<String> readCodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("reasonCodes are invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("JSON serialization failed", exception);
        }
    }
}
