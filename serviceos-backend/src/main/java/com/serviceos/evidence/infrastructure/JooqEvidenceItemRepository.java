package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceItemSummaryView;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
import com.serviceos.evidence.application.EvidenceItemRepository;
import com.serviceos.evidence.application.EvidenceUploadBinding;
import com.serviceos.jooq.generated.tables.EvdEvidenceItem;
import com.serviceos.jooq.generated.tables.EvdEvidenceResolutionMember;
import com.serviceos.jooq.generated.tables.EvdEvidenceRevision;
import com.serviceos.jooq.generated.tables.EvdEvidenceSlot;
import com.serviceos.jooq.generated.tables.EvdTaskEvidenceResolution;
import com.serviceos.jooq.generated.tables.records.EvdEvidenceItemRecord;
import com.serviceos.jooq.generated.tables.records.EvdEvidenceRevisionRecord;
import com.serviceos.jooq.generated.tables.records.EvdEvidenceUploadSessionRecord;
import com.serviceos.jooq.generated.tables.records.EvdEvidenceValidationRecord;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.EvdEvidenceCommandResult.EVD_EVIDENCE_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.EvdEvidenceItem.EVD_EVIDENCE_ITEM;
import static com.serviceos.jooq.generated.tables.EvdEvidenceResolutionMember.EVD_EVIDENCE_RESOLUTION_MEMBER;
import static com.serviceos.jooq.generated.tables.EvdEvidenceRevision.EVD_EVIDENCE_REVISION;
import static com.serviceos.jooq.generated.tables.EvdEvidenceSlot.EVD_EVIDENCE_SLOT;
import static com.serviceos.jooq.generated.tables.EvdEvidenceUploadSession.EVD_EVIDENCE_UPLOAD_SESSION;
import static com.serviceos.jooq.generated.tables.EvdEvidenceValidation.EVD_EVIDENCE_VALIDATION;
import static com.serviceos.jooq.generated.tables.EvdTaskEvidenceResolution.EVD_TASK_EVIDENCE_RESOLUTION;

/**
 * EvidenceItem / Revision / UploadSession 持久化的 jOOQ 实现（取代 MyBatis Mapper + XML）。
 *
 * <p>语义等价要点：Revision 状态迁移 UPDATE 携带原状态条件并返回影响行数；
 * lockSlot 在最新 resolution generation 的 active slot 上取 FOR UPDATE OF 行锁；
 * jsonb 列（capture_metadata / details 等）按 String 直接绑定，不再 CAST/::text。</p>
 */
@Repository
final class JooqEvidenceItemRepository implements EvidenceItemRepository {
    private final DSLContext dsl;

    JooqEvidenceItemRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<EvidenceSlotView> findSlot(String tenantId, UUID taskId, UUID slotId) {
        EvdTaskEvidenceResolution resolution = EVD_TASK_EVIDENCE_RESOLUTION;
        EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
        EvdEvidenceSlot slot = EVD_EVIDENCE_SLOT;
        CommonTableExpression<?> latest = DSL.name("latest")
                .fields("resolution_id", "generation_no")
                .as(dsl.select(resolution.RESOLUTION_ID, resolution.GENERATION_NO)
                        .from(resolution)
                        .where(resolution.TENANT_ID.eq(tenantId))
                        .and(resolution.TASK_ID.eq(taskId))
                        .orderBy(resolution.GENERATION_NO.desc())
                        .limit(1));
        Field<UUID> latestResolutionId = latest.field("resolution_id", UUID.class);
        Field<Integer> resolutionGeneration = latest.field("generation_no", Integer.class);
        return dsl.with(latest)
                .select(slotViewFields(slot, member, resolutionGeneration))
                .from(latest)
                .join(member).on(member.TENANT_ID.eq(tenantId))
                .and(member.RESOLUTION_ID.eq(latestResolutionId))
                .and(member.CONDITION_RESULT.isTrue())
                .join(slot).on(slot.TENANT_ID.eq(member.TENANT_ID))
                .and(slot.SLOT_ID.eq(member.ACTIVE_SLOT_ID))
                .where(slot.SLOT_ID.eq(slotId))
                .fetchOptional()
                .map(row -> slotView(row, slot, member, resolutionGeneration));
    }

    @Override
    public EvidenceSlotView lockSlot(String tenantId, UUID slotId) {
        EvdTaskEvidenceResolution resolution = EVD_TASK_EVIDENCE_RESOLUTION;
        EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
        EvdEvidenceSlot slot = EVD_EVIDENCE_SLOT;
        // 通过 slot 反查 task，再定位该 task 最新 resolution generation；
        // FOR UPDATE OF slot 只锁 slot 行，不锁 resolution/member 投影。
        CommonTableExpression<?> latest = DSL.name("latest")
                .fields("resolution_id", "generation_no", "task_id")
                .as(dsl.select(resolution.RESOLUTION_ID, resolution.GENERATION_NO, resolution.TASK_ID)
                        .from(resolution)
                        .where(resolution.TENANT_ID.eq(tenantId))
                        .and(resolution.TASK_ID.eq(dsl.select(slot.TASK_ID)
                                .from(slot)
                                .where(slot.TENANT_ID.eq(tenantId))
                                .and(slot.SLOT_ID.eq(slotId))))
                        .orderBy(resolution.GENERATION_NO.desc())
                        .limit(1));
        Field<UUID> latestResolutionId = latest.field("resolution_id", UUID.class);
        Field<Integer> resolutionGeneration = latest.field("generation_no", Integer.class);
        Record row = dsl.with(latest)
                .select(slotViewFields(slot, member, resolutionGeneration))
                .from(latest)
                .join(member).on(member.TENANT_ID.eq(tenantId))
                .and(member.RESOLUTION_ID.eq(latestResolutionId))
                .and(member.CONDITION_RESULT.isTrue())
                .join(slot).on(slot.TENANT_ID.eq(member.TENANT_ID))
                .and(slot.SLOT_ID.eq(member.ACTIVE_SLOT_ID))
                .where(slot.SLOT_ID.eq(slotId))
                .forUpdate()
                .of(slot)
                .fetchOptional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "EvidenceSlot does not exist"));
        return slotView(row, slot, member, resolutionGeneration);
    }

    @Override
    public void insertUploadBinding(EvidenceUploadBinding binding) {
        dsl.insertInto(EVD_EVIDENCE_UPLOAD_SESSION)
                .set(EVD_EVIDENCE_UPLOAD_SESSION.UPLOAD_SESSION_ID, binding.uploadSessionId())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.TENANT_ID, binding.tenantId())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.PROJECT_ID, binding.projectId())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.TASK_ID, binding.taskId())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.SLOT_ID, binding.slotId())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.FILE_ID, binding.fileId())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.EVIDENCE_ITEM_ID, binding.evidenceItemId())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.EXPECTED_SHA256, binding.expectedSha256())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.DECLARED_MIME_TYPE, binding.declaredMimeType())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.EXPECTED_SIZE_BYTES, binding.expectedSizeBytes())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.ORIGINAL_FILE_NAME, binding.originalFileName())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.CAPTURE_METADATA, binding.captureMetadataJson())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.STATUS, binding.status())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.CREATED_BY, binding.createdBy())
                .set(EVD_EVIDENCE_UPLOAD_SESSION.CREATED_AT, binding.createdAt())
                .execute();
    }

    @Override
    public Optional<EvidenceUploadBinding> findUploadBinding(String tenantId, UUID uploadSessionId) {
        return dsl.selectFrom(EVD_EVIDENCE_UPLOAD_SESSION)
                .where(EVD_EVIDENCE_UPLOAD_SESSION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                .fetchOptional()
                .map(this::binding);
    }

    @Override
    public Optional<EvidenceUploadBinding> findUploadBindingByFileId(String tenantId, UUID fileId) {
        return dsl.selectFrom(EVD_EVIDENCE_UPLOAD_SESSION)
                .where(EVD_EVIDENCE_UPLOAD_SESSION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_UPLOAD_SESSION.FILE_ID.eq(fileId))
                .fetchOptional()
                .map(this::binding);
    }

    @Override
    public void markUploadFinalized(String tenantId, UUID uploadSessionId) {
        dsl.update(EVD_EVIDENCE_UPLOAD_SESSION)
                .set(EVD_EVIDENCE_UPLOAD_SESSION.STATUS, "FINALIZED")
                .where(EVD_EVIDENCE_UPLOAD_SESSION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_UPLOAD_SESSION.UPLOAD_SESSION_ID.eq(uploadSessionId))
                .and(EVD_EVIDENCE_UPLOAD_SESSION.STATUS.eq("PENDING"))
                .execute();
    }

    @Override
    public Optional<EvidenceItemView> findItem(String tenantId, UUID evidenceItemId) {
        EvdEvidenceItem item = EVD_EVIDENCE_ITEM;
        return dsl.selectFrom(item)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.EVIDENCE_ITEM_ID.eq(evidenceItemId))
                .fetchOptional()
                .map(row -> {
                    Map<UUID, List<EvidenceValidationView>> validations =
                            validationsByRevision(listValidationsForItem(tenantId, evidenceItemId));
                    List<EvidenceRevisionView> revisions = dsl.selectFrom(EVD_EVIDENCE_REVISION)
                            .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                            .and(EVD_EVIDENCE_REVISION.EVIDENCE_ITEM_ID.eq(evidenceItemId))
                            .orderBy(EVD_EVIDENCE_REVISION.REVISION_NUMBER)
                            .fetch(revision -> revisionView(revision, validations));
                    return itemView(row, revisions);
                });
    }

    @Override
    public List<EvidenceItemView> listItems(String tenantId, UUID taskId) {
        EvdEvidenceItem item = EVD_EVIDENCE_ITEM;
        List<EvdEvidenceItemRecord> items = dsl.selectFrom(item)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.TASK_ID.eq(taskId))
                .orderBy(item.SLOT_ID, item.ITEM_ORDINAL)
                .fetch();
        Map<UUID, List<EvidenceValidationView>> validations =
                validationsByRevision(listValidationsForTask(tenantId, taskId));
        Map<UUID, List<EvidenceRevisionView>> byItem = new LinkedHashMap<>();
        dsl.selectFrom(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.TASK_ID.eq(taskId))
                .orderBy(EVD_EVIDENCE_REVISION.EVIDENCE_ITEM_ID, EVD_EVIDENCE_REVISION.REVISION_NUMBER)
                .fetch(revision -> revisionView(revision, validations))
                .forEach(view -> byItem.computeIfAbsent(view.evidenceItemId(), key -> new ArrayList<>())
                        .add(view));
        return items.stream()
                .map(row -> itemView(row, byItem.getOrDefault(row.getEvidenceItemId(), List.of())))
                .toList();
    }

    @Override
    public List<EvidenceItemSummaryView> listItemSummaries(String tenantId, UUID taskId) {
        EvdEvidenceItem item = EVD_EVIDENCE_ITEM;
        EvdEvidenceRevision revision = EVD_EVIDENCE_REVISION;
        // 每个 item 的最新 Revision：LATERAL 子查询内 count(*) OVER () 统计全部版本数，
        // 再按 revision_number 倒序取 1 行；无版本时 LEFT JOIN 为 NULL，计数归零。
        Table<?> latest = DSL.lateral(dsl.select(
                        DSL.count().over().cast(Integer.class).as("revision_count"),
                        revision.REVISION_NUMBER.as("latest_revision_number"),
                        revision.STATUS.as("latest_revision_status"))
                .from(revision)
                .where(revision.TENANT_ID.eq(item.TENANT_ID))
                .and(revision.EVIDENCE_ITEM_ID.eq(item.EVIDENCE_ITEM_ID))
                .orderBy(revision.REVISION_NUMBER.desc())
                .limit(1)).as("latest");
        Field<Integer> revisionCount = latest.field("revision_count", Integer.class);
        Field<Integer> latestRevisionNumber = latest.field("latest_revision_number", Integer.class);
        Field<String> latestRevisionStatus = latest.field("latest_revision_status", String.class);
        Field<Integer> revisionCountOrZero = DSL.coalesce(revisionCount, 0);
        return dsl.select(
                        item.EVIDENCE_ITEM_ID,
                        item.TASK_ID,
                        item.PROJECT_ID,
                        item.SLOT_ID,
                        item.ITEM_ORDINAL,
                        item.STATUS,
                        revisionCountOrZero,
                        latestRevisionNumber,
                        latestRevisionStatus)
                .from(item)
                .leftJoin(latest).on(DSL.trueCondition())
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.TASK_ID.eq(taskId))
                .orderBy(item.SLOT_ID, item.ITEM_ORDINAL)
                .fetch(row -> new EvidenceItemSummaryView(
                        row.get(item.EVIDENCE_ITEM_ID),
                        row.get(item.TASK_ID),
                        row.get(item.PROJECT_ID),
                        row.get(item.SLOT_ID),
                        row.get(item.ITEM_ORDINAL),
                        row.get(item.STATUS),
                        row.get(revisionCountOrZero),
                        row.get(latestRevisionNumber),
                        row.get(latestRevisionStatus)));
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

    @Override
    public int nextItemOrdinal(String tenantId, UUID slotId) {
        EvdEvidenceItem item = EVD_EVIDENCE_ITEM;
        Integer max = dsl.select(DSL.coalesce(DSL.max(item.ITEM_ORDINAL), 0))
                .from(item)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.SLOT_ID.eq(slotId))
                .fetchSingle()
                .value1();
        return max + 1;
    }

    @Override
    public int countItems(String tenantId, UUID slotId) {
        EvdEvidenceItem item = EVD_EVIDENCE_ITEM;
        return dsl.selectCount()
                .from(item)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.SLOT_ID.eq(slotId))
                .fetchSingle()
                .value1();
    }

    @Override
    public int countCountingItems(String tenantId, UUID slotId) {
        EvdEvidenceItem item = EVD_EVIDENCE_ITEM;
        EvdEvidenceRevision revision = EVD_EVIDENCE_REVISION;
        // 只统计存在"计数中"版本（STORED/VALIDATING/VALIDATED）的 item。
        return dsl.selectCount()
                .from(item)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.SLOT_ID.eq(slotId))
                .andExists(dsl.selectOne()
                        .from(revision)
                        .where(revision.TENANT_ID.eq(item.TENANT_ID))
                        .and(revision.EVIDENCE_ITEM_ID.eq(item.EVIDENCE_ITEM_ID))
                        .and(revision.STATUS.in("STORED", "VALIDATING", "VALIDATED")))
                .fetchSingle()
                .value1();
    }

    @Override
    public void insertItem(String tenantId, EvidenceItemView item) {
        EvdEvidenceItem evidenceItem = EVD_EVIDENCE_ITEM;
        dsl.insertInto(evidenceItem)
                .set(evidenceItem.EVIDENCE_ITEM_ID, item.evidenceItemId())
                .set(evidenceItem.TENANT_ID, tenantId)
                .set(evidenceItem.PROJECT_ID, item.projectId())
                .set(evidenceItem.TASK_ID, item.taskId())
                .set(evidenceItem.SLOT_ID, item.evidenceSlotId())
                .set(evidenceItem.ITEM_ORDINAL, item.itemOrdinal())
                .set(evidenceItem.STATUS, item.status())
                .set(evidenceItem.CREATED_BY, item.createdBy())
                .set(evidenceItem.CREATED_AT, item.createdAt())
                .execute();
    }

    @Override
    public int nextRevisionNumber(String tenantId, UUID evidenceItemId) {
        Integer max = dsl.select(DSL.coalesce(DSL.max(EVD_EVIDENCE_REVISION.REVISION_NUMBER), 0))
                .from(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.EVIDENCE_ITEM_ID.eq(evidenceItemId))
                .fetchSingle()
                .value1();
        return max + 1;
    }

    @Override
    public void insertRevision(String tenantId, EvidenceRevisionView revision) {
        dsl.insertInto(EVD_EVIDENCE_REVISION)
                .set(EVD_EVIDENCE_REVISION.EVIDENCE_REVISION_ID, revision.evidenceRevisionId())
                .set(EVD_EVIDENCE_REVISION.TENANT_ID, tenantId)
                .set(EVD_EVIDENCE_REVISION.PROJECT_ID, revision.projectId())
                .set(EVD_EVIDENCE_REVISION.TASK_ID, revision.taskId())
                .set(EVD_EVIDENCE_REVISION.SLOT_ID, revision.evidenceSlotId())
                .set(EVD_EVIDENCE_REVISION.EVIDENCE_ITEM_ID, revision.evidenceItemId())
                .set(EVD_EVIDENCE_REVISION.REVISION_NUMBER, revision.revisionNumber())
                .set(EVD_EVIDENCE_REVISION.FILE_OBJECT_ID, revision.fileObjectId())
                .set(EVD_EVIDENCE_REVISION.CONTENT_DIGEST, revision.contentDigest())
                .set(EVD_EVIDENCE_REVISION.MIME_TYPE, revision.mimeType())
                .set(EVD_EVIDENCE_REVISION.SIZE_BYTES, revision.sizeBytes())
                .set(EVD_EVIDENCE_REVISION.CAPTURE_METADATA, revision.captureMetadataJson())
                .set(EVD_EVIDENCE_REVISION.STATUS, revision.status())
                .set(EVD_EVIDENCE_REVISION.SOURCE_UPLOAD_SESSION_ID, revision.sourceUploadSessionId())
                .set(EVD_EVIDENCE_REVISION.FINALIZE_COMMAND_ID, revision.finalizeCommandId())
                .set(EVD_EVIDENCE_REVISION.CREATED_BY, revision.createdBy())
                .set(EVD_EVIDENCE_REVISION.CREATED_AT, revision.createdAt())
                .execute();
    }

    @Override
    public int updateRevisionStatus(String tenantId, UUID revisionId, String expectedStatus, String status) {
        // 状态迁移携带原状态条件；影响行数由调用方校验，0 即并发漂移或非法状态跃迁。
        return dsl.update(EVD_EVIDENCE_REVISION)
                .set(EVD_EVIDENCE_REVISION.STATUS, status)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.EVIDENCE_REVISION_ID.eq(revisionId))
                .and(EVD_EVIDENCE_REVISION.STATUS.eq(expectedStatus))
                .execute();
    }

    @Override
    public int invalidateRevision(
            String tenantId,
            UUID revisionId,
            String reasonCode,
            String approvalRef,
            String invalidatedBy,
            Instant invalidatedAt
    ) {
        // 仅 VALIDATED 允许作废，写一次作废事实；影响行数由调用方校验。
        return dsl.update(EVD_EVIDENCE_REVISION)
                .set(EVD_EVIDENCE_REVISION.STATUS, "INVALIDATED")
                .set(EVD_EVIDENCE_REVISION.INVALIDATED_BY, invalidatedBy)
                .set(EVD_EVIDENCE_REVISION.INVALIDATED_AT, invalidatedAt)
                .set(EVD_EVIDENCE_REVISION.INVALIDATION_REASON_CODE, reasonCode)
                .set(EVD_EVIDENCE_REVISION.INVALIDATION_APPROVAL_REF, approvalRef)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.EVIDENCE_REVISION_ID.eq(revisionId))
                .and(EVD_EVIDENCE_REVISION.STATUS.eq("VALIDATED"))
                .execute();
    }

    @Override
    public void updateSlotStatus(String tenantId, UUID slotId, String status) {
        dsl.update(EVD_EVIDENCE_SLOT)
                .set(EVD_EVIDENCE_SLOT.STATUS_PROJECTION, status)
                .where(EVD_EVIDENCE_SLOT.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_SLOT.SLOT_ID.eq(slotId))
                .execute();
    }

    @Override
    public Optional<EvidenceRevisionView> findRevision(String tenantId, UUID revisionId) {
        return dsl.selectFrom(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.EVIDENCE_REVISION_ID.eq(revisionId))
                .fetchOptional()
                .map(row -> revisionView(row, Map.of(revisionId, listValidations(tenantId, revisionId))));
    }

    @Override
    public Optional<EvidenceRevisionView> findRevisionByFileObjectId(String tenantId, UUID fileObjectId) {
        return dsl.selectFrom(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.FILE_OBJECT_ID.eq(fileObjectId))
                .fetchOptional()
                .map(row -> revisionView(row, Map.of(
                        row.getEvidenceRevisionId(),
                        listValidations(tenantId, row.getEvidenceRevisionId()))));
    }

    @Override
    public Optional<EvidenceRevisionView> findRevisionByUploadSession(String tenantId, UUID uploadSessionId) {
        return dsl.selectFrom(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.SOURCE_UPLOAD_SESSION_ID.eq(uploadSessionId))
                .fetchOptional()
                .map(row -> revisionView(row, Map.of(
                        row.getEvidenceRevisionId(),
                        listValidations(tenantId, row.getEvidenceRevisionId()))));
    }

    @Override
    public List<EvidenceRevisionView> findRevisionsByIds(
            String tenantId, UUID taskId, List<UUID> revisionIds
    ) {
        if (revisionIds == null || revisionIds.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.TASK_ID.eq(taskId))
                .and(EVD_EVIDENCE_REVISION.EVIDENCE_REVISION_ID.in(revisionIds))
                .fetch(row -> revisionView(row, Map.of(row.getEvidenceRevisionId(), List.of())));
    }

    @Override
    public List<EvidenceRevisionView> listCountingRevisionsForSlot(String tenantId, UUID slotId) {
        List<EvdEvidenceRevisionRecord> rows = dsl.selectFrom(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.SLOT_ID.eq(slotId))
                .and(EVD_EVIDENCE_REVISION.STATUS.in("STORED", "VALIDATING", "VALIDATED"))
                .orderBy(EVD_EVIDENCE_REVISION.EVIDENCE_ITEM_ID, EVD_EVIDENCE_REVISION.REVISION_NUMBER)
                .fetch();
        // 处置是低频高风险命令，按精确 revision 加载校验事实，避免跨 Task 的宽查询与虚构占位参数。
        return rows.stream().map(row -> revisionView(row, Map.of(
                row.getEvidenceRevisionId(), listValidations(tenantId, row.getEvidenceRevisionId()))))
                .toList();
    }

    @Override
    public boolean existsOtherCountingDigest(
            String tenantId, UUID projectId, String contentDigest, UUID excludeRevisionId
    ) {
        Integer count = dsl.selectCount()
                .from(EVD_EVIDENCE_REVISION)
                .where(EVD_EVIDENCE_REVISION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_REVISION.PROJECT_ID.eq(projectId))
                .and(EVD_EVIDENCE_REVISION.CONTENT_DIGEST.eq(contentDigest))
                .and(EVD_EVIDENCE_REVISION.EVIDENCE_REVISION_ID.ne(excludeRevisionId))
                .and(EVD_EVIDENCE_REVISION.STATUS.in("STORED", "VALIDATING", "VALIDATED"))
                .fetchSingle()
                .value1();
        return count > 0;
    }

    @Override
    public List<EvidenceValidationView> listValidations(String tenantId, UUID revisionId) {
        return dsl.selectFrom(EVD_EVIDENCE_VALIDATION)
                .where(EVD_EVIDENCE_VALIDATION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_VALIDATION.EVIDENCE_REVISION_ID.eq(revisionId))
                .orderBy(EVD_EVIDENCE_VALIDATION.CREATED_AT, EVD_EVIDENCE_VALIDATION.CHECK_TYPE)
                .fetch(this::validationView);
    }

    @Override
    public void insertValidation(
            String tenantId, UUID projectId, UUID taskId, UUID slotId,
            UUID evidenceItemId, EvidenceValidationView validation
    ) {
        dsl.insertInto(EVD_EVIDENCE_VALIDATION)
                .set(EVD_EVIDENCE_VALIDATION.VALIDATION_ID, validation.validationId())
                .set(EVD_EVIDENCE_VALIDATION.TENANT_ID, tenantId)
                .set(EVD_EVIDENCE_VALIDATION.PROJECT_ID, projectId)
                .set(EVD_EVIDENCE_VALIDATION.TASK_ID, taskId)
                .set(EVD_EVIDENCE_VALIDATION.SLOT_ID, slotId)
                .set(EVD_EVIDENCE_VALIDATION.EVIDENCE_ITEM_ID, evidenceItemId)
                .set(EVD_EVIDENCE_VALIDATION.EVIDENCE_REVISION_ID, validation.evidenceRevisionId())
                .set(EVD_EVIDENCE_VALIDATION.CHECK_TYPE, validation.checkType())
                .set(EVD_EVIDENCE_VALIDATION.SEVERITY, validation.severity())
                .set(EVD_EVIDENCE_VALIDATION.RESULT, validation.result())
                .set(EVD_EVIDENCE_VALIDATION.REASON_CODE, validation.reasonCode())
                .set(EVD_EVIDENCE_VALIDATION.MESSAGE, validation.message())
                .set(EVD_EVIDENCE_VALIDATION.DETAILS, validation.detailsJson())
                .set(EVD_EVIDENCE_VALIDATION.VALIDATOR_NAME, validation.validatorName())
                .set(EVD_EVIDENCE_VALIDATION.VALIDATOR_VERSION, validation.validatorVersion())
                .set(EVD_EVIDENCE_VALIDATION.CREATED_AT, validation.createdAt())
                // 同一 revision 同一 check_type 只保留首次校验事实，重放不覆盖。
                .onConflict(
                        EVD_EVIDENCE_VALIDATION.TENANT_ID,
                        EVD_EVIDENCE_VALIDATION.EVIDENCE_REVISION_ID,
                        EVD_EVIDENCE_VALIDATION.CHECK_TYPE)
                .doNothing()
                .execute();
    }

    private List<EvidenceValidationView> listValidationsForTask(String tenantId, UUID taskId) {
        return dsl.selectFrom(EVD_EVIDENCE_VALIDATION)
                .where(EVD_EVIDENCE_VALIDATION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_VALIDATION.TASK_ID.eq(taskId))
                .orderBy(
                        EVD_EVIDENCE_VALIDATION.EVIDENCE_REVISION_ID,
                        EVD_EVIDENCE_VALIDATION.CREATED_AT,
                        EVD_EVIDENCE_VALIDATION.CHECK_TYPE)
                .fetch(this::validationView);
    }

    private List<EvidenceValidationView> listValidationsForItem(String tenantId, UUID evidenceItemId) {
        return dsl.selectFrom(EVD_EVIDENCE_VALIDATION)
                .where(EVD_EVIDENCE_VALIDATION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_VALIDATION.EVIDENCE_ITEM_ID.eq(evidenceItemId))
                .orderBy(
                        EVD_EVIDENCE_VALIDATION.EVIDENCE_REVISION_ID,
                        EVD_EVIDENCE_VALIDATION.CREATED_AT,
                        EVD_EVIDENCE_VALIDATION.CHECK_TYPE)
                .fetch(this::validationView);
    }

    /**
     * Slot 视图投影列：condition_input_digest 与 resolution_explanation 取最新 resolution
     * member 的事实，current_resolution_id 为 member 所属 resolution；与原 XML slotColumns 一致。
     */
    private static List<SelectField<?>> slotViewFields(
            EvdEvidenceSlot slot,
            EvdEvidenceResolutionMember member,
            Field<Integer> resolutionGeneration
    ) {
        return List.of(
                slot.SLOT_ID,
                slot.RESOLUTION_ID,
                slot.TASK_ID,
                slot.PROJECT_ID,
                slot.TEMPLATE_VERSION_ID,
                slot.TEMPLATE_KEY,
                slot.TEMPLATE_VERSION,
                slot.TEMPLATE_DIGEST,
                slot.REQUIREMENT_CODE,
                slot.OCCURRENCE_KEY,
                slot.REQUIREMENT_NAME,
                slot.MEDIA_TYPE,
                slot.REQUIRED_FLAG,
                slot.MIN_COUNT,
                slot.MAX_COUNT,
                member.CONDITION_INPUT_DIGEST,
                member.RESOLUTION_EXPLANATION,
                slot.REQUIREMENT_DEFINITION,
                slot.REQUIREMENT_DIGEST,
                slot.STATUS_PROJECTION,
                slot.RESOLVED_AT,
                slot.SLOT_GENERATION,
                slot.SUPERSEDES_SLOT_ID,
                member.RESOLUTION_ID.as("current_resolution_id"),
                resolutionGeneration,
                member.TRANSITION,
                member.REQUIRED_DISPOSITION);
    }

    private EvidenceSlotView slotView(
            Record row,
            EvdEvidenceSlot slot,
            EvdEvidenceResolutionMember member,
            Field<Integer> resolutionGeneration
    ) {
        return new EvidenceSlotView(
                row.get(slot.SLOT_ID),
                row.get(slot.RESOLUTION_ID),
                row.get(slot.TASK_ID),
                row.get(slot.PROJECT_ID),
                row.get(slot.TEMPLATE_VERSION_ID),
                row.get(slot.TEMPLATE_KEY),
                row.get(slot.TEMPLATE_VERSION),
                row.get(slot.TEMPLATE_DIGEST),
                row.get(slot.REQUIREMENT_CODE),
                row.get(slot.OCCURRENCE_KEY),
                row.get(slot.REQUIREMENT_NAME),
                row.get(slot.MEDIA_TYPE),
                Boolean.TRUE.equals(row.get(slot.REQUIRED_FLAG)),
                row.get(slot.MIN_COUNT),
                row.get(slot.MAX_COUNT),
                row.get(member.CONDITION_INPUT_DIGEST),
                row.get(member.RESOLUTION_EXPLANATION),
                row.get(slot.REQUIREMENT_DEFINITION),
                row.get(slot.REQUIREMENT_DIGEST),
                row.get(slot.STATUS_PROJECTION),
                row.get(slot.RESOLVED_AT),
                row.get(slot.SLOT_GENERATION),
                row.get(slot.SUPERSEDES_SLOT_ID),
                row.get("current_resolution_id", UUID.class),
                row.get(resolutionGeneration),
                true,
                row.get(member.TRANSITION),
                row.get(member.REQUIRED_DISPOSITION));
    }

    private EvidenceUploadBinding binding(EvdEvidenceUploadSessionRecord row) {
        return new EvidenceUploadBinding(
                row.getUploadSessionId(), row.getTenantId(), row.getProjectId(), row.getTaskId(),
                row.getSlotId(), row.getFileId(), row.getEvidenceItemId(), row.getExpectedSha256(),
                row.getDeclaredMimeType(), row.getExpectedSizeBytes(), row.getOriginalFileName(),
                row.getCaptureMetadata(), row.getStatus(), row.getCreatedBy(), row.getCreatedAt());
    }

    private EvidenceItemView itemView(EvdEvidenceItemRecord row, List<EvidenceRevisionView> revisions) {
        return new EvidenceItemView(
                row.getEvidenceItemId(), row.getTaskId(), row.getProjectId(), row.getSlotId(),
                row.getItemOrdinal(), row.getStatus(), row.getCreatedBy(), row.getCreatedAt(), revisions);
    }

    private EvidenceRevisionView revisionView(
            EvdEvidenceRevisionRecord row, Map<UUID, List<EvidenceValidationView>> validations
    ) {
        UUID revisionId = row.getEvidenceRevisionId();
        return new EvidenceRevisionView(
                revisionId, row.getEvidenceItemId(), row.getSlotId(), row.getTaskId(), row.getProjectId(),
                row.getRevisionNumber(), row.getFileObjectId(), row.getContentDigest(), row.getMimeType(),
                row.getSizeBytes(), row.getCaptureMetadata(), row.getStatus(), row.getSourceUploadSessionId(),
                row.getFinalizeCommandId(), row.getCreatedBy(), row.getCreatedAt(),
                validations.getOrDefault(revisionId, List.of()),
                row.getInvalidatedBy(), row.getInvalidatedAt(),
                row.getInvalidationReasonCode(), row.getInvalidationApprovalRef());
    }

    private EvidenceValidationView validationView(EvdEvidenceValidationRecord row) {
        return new EvidenceValidationView(
                row.getValidationId(), row.getEvidenceRevisionId(), row.getCheckType(), row.getSeverity(),
                row.getResult(), row.getReasonCode(), row.getMessage(), row.getDetails(),
                row.getValidatorName(), row.getValidatorVersion(), row.getCreatedAt());
    }

    private Map<UUID, List<EvidenceValidationView>> validationsByRevision(List<EvidenceValidationView> views) {
        Map<UUID, List<EvidenceValidationView>> byRevision = new LinkedHashMap<>();
        for (EvidenceValidationView view : views) {
            byRevision.computeIfAbsent(view.evidenceRevisionId(), key -> new ArrayList<>()).add(view);
        }
        return byRevision;
    }
}
