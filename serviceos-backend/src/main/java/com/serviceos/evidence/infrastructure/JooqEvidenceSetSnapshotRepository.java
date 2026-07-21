package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.application.EvidenceSetSnapshotRepository;
import com.serviceos.jooq.generated.tables.records.EvdEvidenceSetMemberRecord;
import com.serviceos.jooq.generated.tables.records.EvdEvidenceSetSnapshotRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.EvdEvidenceCommandResult.EVD_EVIDENCE_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.EvdEvidenceSetMember.EVD_EVIDENCE_SET_MEMBER;
import static com.serviceos.jooq.generated.tables.EvdEvidenceSetSnapshot.EVD_EVIDENCE_SET_SNAPSHOT;

/** EvidenceSetSnapshot 持久化的 jOOQ 实现（取代 MyBatis Mapper + XML）。 */
@Repository
final class JooqEvidenceSetSnapshotRepository implements EvidenceSetSnapshotRepository {
    private final DSLContext dsl;

    JooqEvidenceSetSnapshotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(String tenantId, EvidenceSetSnapshotView snapshot) {
        dsl.insertInto(EVD_EVIDENCE_SET_SNAPSHOT)
                .set(EVD_EVIDENCE_SET_SNAPSHOT.EVIDENCE_SET_SNAPSHOT_ID, snapshot.evidenceSetSnapshotId())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.TENANT_ID, tenantId)
                .set(EVD_EVIDENCE_SET_SNAPSHOT.PROJECT_ID, snapshot.projectId())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.TASK_ID, snapshot.taskId())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.RESOLUTION_ID, snapshot.resolutionId())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.PURPOSE, snapshot.purpose())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.MEMBER_COUNT, snapshot.memberCount())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.CONTENT_DIGEST, snapshot.contentDigest())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.ELIGIBILITY_SUMMARY, snapshot.eligibilitySummaryJson())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.CREATED_BY, snapshot.createdBy())
                .set(EVD_EVIDENCE_SET_SNAPSHOT.CREATED_AT, snapshot.createdAt())
                .execute();
        for (EvidenceSetSnapshotMemberView member : snapshot.members()) {
            dsl.insertInto(EVD_EVIDENCE_SET_MEMBER)
                    .set(EVD_EVIDENCE_SET_MEMBER.MEMBER_ID, member.memberId())
                    .set(EVD_EVIDENCE_SET_MEMBER.TENANT_ID, tenantId)
                    .set(EVD_EVIDENCE_SET_MEMBER.EVIDENCE_SET_SNAPSHOT_ID, snapshot.evidenceSetSnapshotId())
                    .set(EVD_EVIDENCE_SET_MEMBER.PROJECT_ID, snapshot.projectId())
                    .set(EVD_EVIDENCE_SET_MEMBER.TASK_ID, snapshot.taskId())
                    .set(EVD_EVIDENCE_SET_MEMBER.SLOT_ID, member.evidenceSlotId())
                    .set(EVD_EVIDENCE_SET_MEMBER.EVIDENCE_ITEM_ID, member.evidenceItemId())
                    .set(EVD_EVIDENCE_SET_MEMBER.EVIDENCE_REVISION_ID, member.evidenceRevisionId())
                    .set(EVD_EVIDENCE_SET_MEMBER.REVISION_NUMBER, member.revisionNumber())
                    .set(EVD_EVIDENCE_SET_MEMBER.REVISION_STATUS, member.revisionStatus())
                    .set(EVD_EVIDENCE_SET_MEMBER.CONTENT_DIGEST, member.contentDigest())
                    .set(EVD_EVIDENCE_SET_MEMBER.VALIDATION_DIGEST, member.validationDigest())
                    .set(EVD_EVIDENCE_SET_MEMBER.MEMBER_ORDINAL, member.memberOrdinal())
                    .execute();
        }
    }

    @Override
    public Optional<EvidenceSetSnapshotView> find(String tenantId, UUID snapshotId) {
        return dsl.selectFrom(EVD_EVIDENCE_SET_SNAPSHOT)
                .where(EVD_EVIDENCE_SET_SNAPSHOT.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_SET_SNAPSHOT.EVIDENCE_SET_SNAPSHOT_ID.eq(snapshotId))
                .fetchOptional()
                .map(row -> view(row, listMembers(tenantId, snapshotId)));
    }

    @Override
    public Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey) {
        return dsl.select(EVD_EVIDENCE_COMMAND_RESULT.RESULT_ID)
                .from(EVD_EVIDENCE_COMMAND_RESULT)
                .where(EVD_EVIDENCE_COMMAND_RESULT.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_COMMAND_RESULT.OPERATION_TYPE.eq(operationType))
                .and(EVD_EVIDENCE_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(EVD_EVIDENCE_COMMAND_RESULT.RESULT_ID);
    }

    @Override
    public void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId) {
        dsl.insertInto(EVD_EVIDENCE_COMMAND_RESULT)
                .set(EVD_EVIDENCE_COMMAND_RESULT.TENANT_ID, tenantId)
                .set(EVD_EVIDENCE_COMMAND_RESULT.OPERATION_TYPE, operationType)
                .set(EVD_EVIDENCE_COMMAND_RESULT.IDEMPOTENCY_KEY, idempotencyKey)
                .set(EVD_EVIDENCE_COMMAND_RESULT.RESULT_ID, resultId)
                .onConflict(
                        EVD_EVIDENCE_COMMAND_RESULT.TENANT_ID,
                        EVD_EVIDENCE_COMMAND_RESULT.OPERATION_TYPE,
                        EVD_EVIDENCE_COMMAND_RESULT.IDEMPOTENCY_KEY)
                .doNothing()
                .execute();
    }

    private List<EvidenceSetSnapshotMemberView> listMembers(String tenantId, UUID snapshotId) {
        return dsl.selectFrom(EVD_EVIDENCE_SET_MEMBER)
                .where(EVD_EVIDENCE_SET_MEMBER.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_SET_MEMBER.EVIDENCE_SET_SNAPSHOT_ID.eq(snapshotId))
                .orderBy(EVD_EVIDENCE_SET_MEMBER.MEMBER_ORDINAL)
                .fetch(this::memberView);
    }

    private EvidenceSetSnapshotView view(
            EvdEvidenceSetSnapshotRecord row, List<EvidenceSetSnapshotMemberView> members
    ) {
        return new EvidenceSetSnapshotView(
                row.getEvidenceSetSnapshotId(), row.getTaskId(), row.getProjectId(), row.getResolutionId(),
                row.getPurpose(), row.getMemberCount(), row.getContentDigest(), row.getEligibilitySummary(),
                row.getCreatedBy(), row.getCreatedAt(), members);
    }

    private EvidenceSetSnapshotMemberView memberView(EvdEvidenceSetMemberRecord row) {
        return new EvidenceSetSnapshotMemberView(
                row.getMemberId(), row.getSlotId(), row.getEvidenceItemId(), row.getEvidenceRevisionId(),
                row.getRevisionNumber(), row.getRevisionStatus(), row.getContentDigest(),
                row.getValidationDigest(), row.getMemberOrdinal());
    }
}
