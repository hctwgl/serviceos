package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.CorrectionCaseQueueItem;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.application.CorrectionCaseRepository;
import com.serviceos.jooq.generated.tables.EvdCorrectionCase;
import com.serviceos.jooq.generated.tables.EvdCorrectionResubmission;
import com.serviceos.jooq.generated.tables.records.EvdCorrectionCaseRecord;
import com.serviceos.jooq.generated.tables.records.EvdCorrectionResubmissionRecord;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.EvdCorrectionCase.EVD_CORRECTION_CASE;
import static com.serviceos.jooq.generated.tables.EvdCorrectionCommandResult.EVD_CORRECTION_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.EvdCorrectionResubmission.EVD_CORRECTION_RESUBMISSION;

/**
 * CorrectionCase / 补传轮次持久化的 jOOQ 实现（取代 MyBatis Mapper + XML）。
 *
 * <p>语义等价要点：状态迁移 UPDATE 均携带原状态条件并返回影响行数；
 * linkCorrectionTask 仅当 correction_task_id 仍为 NULL 时生效；队列游标为
 * (created_at, correction_case_id) 行值比较；非全租户视角且无授权项目时退化为 AND FALSE。</p>
 */
@Repository
final class JooqCorrectionCaseRepository implements CorrectionCaseRepository {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqCorrectionCaseRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insertCase(String tenantId, CorrectionCaseView correctionCase) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        dsl.insertInto(correction)
                .set(correction.CORRECTION_CASE_ID, correctionCase.correctionCaseId())
                .set(correction.TENANT_ID, tenantId)
                .set(correction.PROJECT_ID, correctionCase.projectId())
                .set(correction.TASK_ID, correctionCase.taskId())
                .set(correction.SOURCE_REVIEW_CASE_ID, correctionCase.sourceReviewCaseId())
                .set(correction.SOURCE_REVIEW_DECISION_ID, correctionCase.sourceReviewDecisionId())
                .set(correction.SOURCE_EVIDENCE_SET_SNAPSHOT_ID, correctionCase.sourceEvidenceSetSnapshotId())
                .set(correction.SOURCE_SNAPSHOT_CONTENT_DIGEST, correctionCase.sourceSnapshotContentDigest())
                .set(correction.REASON_CODES, writeJson(correctionCase.reasonCodes()))
                .set(correction.STATUS, correctionCase.status())
                .set(correction.CREATED_BY, correctionCase.createdBy())
                .set(correction.CREATED_AT, correctionCase.createdAt())
                .set(correction.LATEST_RESUBMISSION_SNAPSHOT_ID, correctionCase.latestResubmissionSnapshotId())
                .set(correction.CLOSED_BY, correctionCase.closedBy())
                .set(correction.CLOSED_AT, correctionCase.closedAt())
                .set(correction.CORRECTION_TASK_ID, correctionCase.correctionTaskId())
                .execute();
    }

    @Override
    public int linkCorrectionTask(String tenantId, UUID correctionCaseId, UUID correctionTaskId) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        // 只认领尚未关联整改 Task 的案例；已关联时影响行数为 0，由调用方按并发冲突处理。
        return dsl.update(correction)
                .set(correction.CORRECTION_TASK_ID, correctionTaskId)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.CORRECTION_CASE_ID.eq(correctionCaseId))
                .and(correction.CORRECTION_TASK_ID.isNull())
                .execute();
    }

    @Override
    public int markInProgress(String tenantId, UUID correctionCaseId, String expectedStatus) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.update(correction)
                .set(correction.STATUS, "IN_PROGRESS")
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.CORRECTION_CASE_ID.eq(correctionCaseId))
                .and(correction.STATUS.eq(expectedStatus))
                .execute();
    }

    @Override
    public void insertResubmission(String tenantId, UUID projectId, CorrectionResubmissionView resubmission) {
        EvdCorrectionResubmission round = EVD_CORRECTION_RESUBMISSION;
        dsl.insertInto(round)
                .set(round.CORRECTION_RESUBMISSION_ID, resubmission.correctionResubmissionId())
                .set(round.TENANT_ID, tenantId)
                .set(round.PROJECT_ID, projectId)
                .set(round.CORRECTION_CASE_ID, resubmission.correctionCaseId())
                .set(round.RESUBMISSION_ORDINAL, resubmission.resubmissionOrdinal())
                .set(round.EVIDENCE_SET_SNAPSHOT_ID, resubmission.evidenceSetSnapshotId())
                .set(round.SNAPSHOT_CONTENT_DIGEST, resubmission.snapshotContentDigest())
                .set(round.SUBMITTED_BY, resubmission.submittedBy())
                .set(round.SUBMITTED_AT, resubmission.submittedAt())
                .execute();
    }

    @Override
    public int markResubmitted(
            String tenantId, UUID correctionCaseId, String expectedStatus, UUID latestSnapshotId, Instant updatedAt
    ) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.update(correction)
                .set(correction.STATUS, "RESUBMITTED")
                .set(correction.LATEST_RESUBMISSION_SNAPSHOT_ID, latestSnapshotId)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.CORRECTION_CASE_ID.eq(correctionCaseId))
                .and(correction.STATUS.eq(expectedStatus))
                .execute();
    }

    @Override
    public int markClosed(
            String tenantId, UUID correctionCaseId, String expectedStatus, String closedBy, Instant closedAt
    ) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.update(correction)
                .set(correction.STATUS, "CLOSED")
                .set(correction.CLOSED_BY, closedBy)
                .set(correction.CLOSED_AT, closedAt)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.CORRECTION_CASE_ID.eq(correctionCaseId))
                .and(correction.STATUS.eq(expectedStatus))
                .execute();
    }

    @Override
    public int markWaived(
            String tenantId,
            UUID correctionCaseId,
            String expectedStatus,
            String waivedBy,
            Instant waivedAt,
            String approvalRef,
            String note
    ) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.update(correction)
                .set(correction.STATUS, "WAIVED")
                .set(correction.WAIVED_BY, waivedBy)
                .set(correction.WAIVED_AT, waivedAt)
                .set(correction.WAIVE_APPROVAL_REF, approvalRef)
                .set(correction.WAIVE_NOTE, note)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.CORRECTION_CASE_ID.eq(correctionCaseId))
                .and(correction.STATUS.eq(expectedStatus))
                .execute();
    }

    @Override
    public Optional<CorrectionCaseView> find(String tenantId, UUID correctionCaseId) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.selectFrom(correction)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.CORRECTION_CASE_ID.eq(correctionCaseId))
                .fetchOptional()
                .map(row -> caseView(row, listResubmissions(tenantId, correctionCaseId)));
    }

    @Override
    public Optional<CorrectionCaseView> findByCorrectionTaskId(String tenantId, UUID correctionTaskId) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.selectFrom(correction)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.CORRECTION_TASK_ID.eq(correctionTaskId))
                .fetchOptional()
                .map(row -> caseView(row, listResubmissions(tenantId, row.getCorrectionCaseId())));
    }

    @Override
    public List<CorrectionCaseView> listByTask(String tenantId, UUID taskId) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.selectFrom(correction)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.TASK_ID.eq(taskId))
                .orderBy(correction.CREATED_AT.asc(), correction.CORRECTION_CASE_ID.asc())
                .fetch(row -> caseView(row, listResubmissions(tenantId, row.getCorrectionCaseId())));
    }

    @Override
    public List<CorrectionCaseQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            UUID taskId,
            UUID sourceReviewCaseId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize
    ) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        EvdCorrectionResubmission round = EVD_CORRECTION_RESUBMISSION;
        // 补传轮次计数：相关标量子查询（COUNT 空集返回 0），等价于原 LATERAL + COALESCE(..., 0)。
        Field<Integer> resubmissionCount = DSL.field(dsl.selectCount()
                .from(round)
                .where(round.TENANT_ID.eq(correction.TENANT_ID))
                .and(round.CORRECTION_CASE_ID.eq(correction.CORRECTION_CASE_ID)))
                .as("resubmission_count");

        Condition condition = correction.TENANT_ID.eq(tenantId).and(correction.STATUS.eq(status));
        if (!tenantWide) {
            // 非全租户视角且无任何授权项目时结果必须为空（AND FALSE），不得退化为全量。
            condition = condition.and(projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : correction.PROJECT_ID.in(projectIds));
        }
        if (taskId != null) {
            condition = condition.and(correction.TASK_ID.eq(taskId));
        }
        if (sourceReviewCaseId != null) {
            condition = condition.and(correction.SOURCE_REVIEW_CASE_ID.eq(sourceReviewCaseId));
        }
        if (cursorCreatedAt != null) {
            // 稳定游标：取严格大于 (created_at, correction_case_id) 的下一页。
            condition = condition.and(DSL.row(correction.CREATED_AT, correction.CORRECTION_CASE_ID)
                    .gt(cursorCreatedAt, cursorId));
        }
        return dsl.select(
                        correction.CORRECTION_CASE_ID,
                        correction.PROJECT_ID,
                        correction.TASK_ID,
                        correction.SOURCE_REVIEW_CASE_ID,
                        correction.SOURCE_REVIEW_DECISION_ID,
                        correction.REASON_CODES,
                        correction.CORRECTION_TASK_ID,
                        correction.STATUS,
                        correction.CREATED_AT,
                        correction.LATEST_RESUBMISSION_SNAPSHOT_ID,
                        correction.CLOSED_AT,
                        correction.WAIVED_AT,
                        resubmissionCount)
                .from(correction)
                .where(condition)
                .orderBy(correction.CREATED_AT, correction.CORRECTION_CASE_ID)
                .limit(fetchSize)
                .fetch(row -> queueItem(row, resubmissionCount));
    }

    @Override
    public Optional<UUID> findBySourceDecision(String tenantId, UUID reviewDecisionId) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return dsl.select(correction.CORRECTION_CASE_ID)
                .from(correction)
                .where(correction.TENANT_ID.eq(tenantId))
                .and(correction.SOURCE_REVIEW_DECISION_ID.eq(reviewDecisionId))
                .fetchOptional(correction.CORRECTION_CASE_ID);
    }

    @Override
    public Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey) {
        return dsl.select(EVD_CORRECTION_COMMAND_RESULT.RESULT_ID)
                .from(EVD_CORRECTION_COMMAND_RESULT)
                .where(EVD_CORRECTION_COMMAND_RESULT.TENANT_ID.eq(tenantId))
                .and(EVD_CORRECTION_COMMAND_RESULT.OPERATION_TYPE.eq(operationType))
                .and(EVD_CORRECTION_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(EVD_CORRECTION_COMMAND_RESULT.RESULT_ID);
    }

    @Override
    public void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId) {
        dsl.insertInto(EVD_CORRECTION_COMMAND_RESULT)
                .set(EVD_CORRECTION_COMMAND_RESULT.TENANT_ID, tenantId)
                .set(EVD_CORRECTION_COMMAND_RESULT.OPERATION_TYPE, operationType)
                .set(EVD_CORRECTION_COMMAND_RESULT.IDEMPOTENCY_KEY, idempotencyKey)
                .set(EVD_CORRECTION_COMMAND_RESULT.RESULT_ID, resultId)
                .execute();
    }

    @Override
    public int nextResubmissionOrdinal(String tenantId, UUID correctionCaseId) {
        EvdCorrectionResubmission round = EVD_CORRECTION_RESUBMISSION;
        Integer max = dsl.select(DSL.coalesce(DSL.max(round.RESUBMISSION_ORDINAL), 0))
                .from(round)
                .where(round.TENANT_ID.eq(tenantId))
                .and(round.CORRECTION_CASE_ID.eq(correctionCaseId))
                .fetchSingle()
                .value1();
        return max + 1;
    }

    private List<CorrectionResubmissionView> listResubmissions(String tenantId, UUID correctionCaseId) {
        EvdCorrectionResubmission round = EVD_CORRECTION_RESUBMISSION;
        return dsl.selectFrom(round)
                .where(round.TENANT_ID.eq(tenantId))
                .and(round.CORRECTION_CASE_ID.eq(correctionCaseId))
                .orderBy(round.RESUBMISSION_ORDINAL)
                .fetch(this::resubmissionView);
    }

    private CorrectionCaseView caseView(EvdCorrectionCaseRecord row, List<CorrectionResubmissionView> rounds) {
        return new CorrectionCaseView(
                row.getCorrectionCaseId(), row.getProjectId(), row.getTaskId(),
                row.getSourceReviewCaseId(), row.getSourceReviewDecisionId(),
                row.getSourceEvidenceSetSnapshotId(), row.getSourceSnapshotContentDigest(),
                readCodes(row.getReasonCodes()), row.getCorrectionTaskId(), row.getStatus(),
                row.getCreatedBy(), row.getCreatedAt(), row.getLatestResubmissionSnapshotId(),
                row.getClosedBy(), row.getClosedAt(), row.getWaivedBy(), row.getWaivedAt(),
                row.getWaiveApprovalRef(), row.getWaiveNote(), rounds);
    }

    private CorrectionResubmissionView resubmissionView(EvdCorrectionResubmissionRecord row) {
        return new CorrectionResubmissionView(
                row.getCorrectionResubmissionId(), row.getCorrectionCaseId(), row.getResubmissionOrdinal(),
                row.getEvidenceSetSnapshotId(), row.getSnapshotContentDigest(),
                row.getSubmittedBy(), row.getSubmittedAt());
    }

    private CorrectionCaseQueueItem queueItem(Record row, Field<Integer> resubmissionCount) {
        EvdCorrectionCase correction = EVD_CORRECTION_CASE;
        return new CorrectionCaseQueueItem(
                row.get(correction.CORRECTION_CASE_ID),
                row.get(correction.PROJECT_ID),
                row.get(correction.TASK_ID),
                row.get(correction.SOURCE_REVIEW_CASE_ID),
                row.get(correction.SOURCE_REVIEW_DECISION_ID),
                readCodes(row.get(correction.REASON_CODES)),
                row.get(correction.CORRECTION_TASK_ID),
                row.get(correction.STATUS),
                row.get(correction.CREATED_AT),
                row.get(correction.LATEST_RESUBMISSION_SNAPSHOT_ID),
                row.get(correction.CLOSED_AT),
                row.get(correction.WAIVED_AT),
                row.get(resubmissionCount));
    }

    private List<String> readCodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("reasonCodes deserialization failed", exception);
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
