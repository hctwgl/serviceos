package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.application.EvidenceConditionDisposition;
import com.serviceos.evidence.application.EvidenceResolutionMember;
import com.serviceos.evidence.application.EvidenceResolutionMemberState;
import com.serviceos.evidence.application.EvidenceResolutionState;
import com.serviceos.evidence.application.EvidenceSlotRepository;
import com.serviceos.evidence.application.EvidenceTaskResolution;
import com.serviceos.evidence.application.PendingEvidenceConditionDisposition;
import com.serviceos.jooq.generated.tables.EvdEvidenceConditionDisposition;
import com.serviceos.jooq.generated.tables.EvdEvidenceResolutionMember;
import com.serviceos.jooq.generated.tables.EvdEvidenceSlot;
import com.serviceos.jooq.generated.tables.EvdTaskEvidenceResolution;
import com.serviceos.jooq.generated.tables.records.EvdEvidenceConditionDispositionRecord;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.EvdEvidenceConditionDisposition.EVD_EVIDENCE_CONDITION_DISPOSITION;
import static com.serviceos.jooq.generated.tables.EvdEvidenceResolutionMember.EVD_EVIDENCE_RESOLUTION_MEMBER;
import static com.serviceos.jooq.generated.tables.EvdEvidenceSlot.EVD_EVIDENCE_SLOT;
import static com.serviceos.jooq.generated.tables.EvdTaskEvidenceResolution.EVD_TASK_EVIDENCE_RESOLUTION;

/**
 * EvidenceSlot / TaskEvidenceResolution 持久化的 jOOQ 实现（取代 MyBatis Mapper + XML）。
 *
 * <p>语义等价要点：resolution 写入串行化由 pg_advisory_xact_lock(hashtextextended(...))
 * 事务级顾问锁保证；slot/member 批量写入保持原 XML 的多行 VALUES 形态；
 * 待处置判定保持 REVIEW_REQUIRED 且尚无 disposition 事实的 LEFT JOIN 语义。</p>
 */
@Repository
final class JooqEvidenceSlotRepository implements EvidenceSlotRepository {
    private final DSLContext dsl;

    JooqEvidenceSlotRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(EvidenceTaskResolution resolution) {
        EvdTaskEvidenceResolution resolutionTable = EVD_TASK_EVIDENCE_RESOLUTION;
        dsl.insertInto(resolutionTable)
                .set(resolutionTable.RESOLUTION_ID, resolution.resolutionId())
                .set(resolutionTable.TENANT_ID, resolution.tenantId())
                .set(resolutionTable.PROJECT_ID, resolution.projectId())
                .set(resolutionTable.TASK_ID, resolution.taskId())
                .set(resolutionTable.CONFIGURATION_BUNDLE_ID, resolution.configurationBundleId())
                .set(resolutionTable.CONFIGURATION_BUNDLE_DIGEST, resolution.configurationBundleDigest())
                .set(resolutionTable.STAGE_CODE, resolution.stageCode())
                .set(resolutionTable.SOURCE_EVENT_ID, resolution.sourceEventId())
                .set(resolutionTable.SOURCE_EVENT_DIGEST, resolution.sourceEventDigest())
                .set(resolutionTable.RESOLVER_VERSION, resolution.resolverVersion())
                .set(resolutionTable.CONDITION_INPUT_DIGEST, resolution.conditionInputDigest())
                .set(resolutionTable.RESOLUTION_EXPLANATION, resolution.resolutionExplanationJson())
                .set(resolutionTable.GENERATION_NO, resolution.generationNo())
                .set(resolutionTable.CONDITION_FACT_TYPE, resolution.conditionFactType())
                .set(resolutionTable.CONDITION_FACT_REF, resolution.conditionFactRef())
                .set(resolutionTable.CONDITION_FACT_REVISION, resolution.conditionFactRevision())
                .set(resolutionTable.PREVIOUS_RESOLUTION_ID, resolution.previousResolutionId())
                .set(resolutionTable.SLOT_COUNT, Math.toIntExact(resolution.members().stream()
                        .filter(EvidenceResolutionMember::conditionResult).count()))
                .set(resolutionTable.RESOLVED_AT, resolution.resolvedAt())
                .execute();

        if (!resolution.slots().isEmpty()) {
            EvdEvidenceSlot slot = EVD_EVIDENCE_SLOT;
            // 多行 VALUES 单语句写入，与原 MyBatis foreach 批量 INSERT 形态一致。
            var insert = dsl.insertInto(slot).columns(
                    slot.SLOT_ID, slot.TENANT_ID, slot.PROJECT_ID, slot.TASK_ID, slot.RESOLUTION_ID,
                    slot.TEMPLATE_VERSION_ID, slot.TEMPLATE_KEY, slot.TEMPLATE_VERSION,
                    slot.TEMPLATE_DIGEST, slot.REQUIREMENT_CODE, slot.OCCURRENCE_KEY,
                    slot.REQUIREMENT_NAME, slot.MEDIA_TYPE, slot.REQUIRED_FLAG,
                    slot.MIN_COUNT, slot.MAX_COUNT, slot.CONDITION_INPUT_DIGEST,
                    slot.RESOLUTION_EXPLANATION, slot.REQUIREMENT_DEFINITION,
                    slot.REQUIREMENT_DIGEST, slot.STATUS_PROJECTION, slot.RESOLVED_AT,
                    slot.SLOT_GENERATION, slot.SUPERSEDES_SLOT_ID);
            for (EvidenceSlotView view : resolution.slots()) {
                insert = insert.values(
                        view.slotId(), resolution.tenantId(), view.projectId(), view.taskId(),
                        view.resolutionId(), view.templateVersionId(), view.templateKey(),
                        view.templateVersion(), view.templateDigest(), view.requirementCode(),
                        view.occurrenceKey(), view.requirementName(), view.mediaType(),
                        view.required(), view.minCount(), view.maxCount(), view.conditionInputDigest(),
                        view.resolutionExplanationJson(), view.requirementDefinitionJson(),
                        view.requirementDigest(), view.status(), view.resolvedAt(),
                        view.slotGeneration(), view.supersedesSlotId());
            }
            insert.execute();
        }
        if (!resolution.members().isEmpty()) {
            EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
            var insert = dsl.insertInto(member).columns(
                    member.MEMBER_ID, member.TENANT_ID, member.PROJECT_ID, member.TASK_ID,
                    member.RESOLUTION_ID, member.TEMPLATE_VERSION_ID, member.REQUIREMENT_CODE,
                    member.OCCURRENCE_KEY, member.CONDITION_RESULT, member.ACTIVE_SLOT_ID,
                    member.PREVIOUS_SLOT_ID, member.TRANSITION, member.REQUIRED_DISPOSITION,
                    member.COUNTING_ITEM_COUNT, member.CONDITION_INPUT_DIGEST,
                    member.RESOLUTION_EXPLANATION, member.CREATED_AT);
            for (EvidenceResolutionMember view : resolution.members()) {
                insert = insert.values(
                        view.memberId(), resolution.tenantId(), view.projectId(), view.taskId(),
                        view.resolutionId(), view.templateVersionId(), view.requirementCode(),
                        view.occurrenceKey(), view.conditionResult(), view.activeSlotId(),
                        view.previousSlotId(), view.transition(), view.requiredDisposition(),
                        view.countingItemCount(), view.conditionInputDigest(),
                        view.resolutionExplanationJson(), view.createdAt());
            }
            insert.execute();
        }
    }

    @Override
    public void lockResolutionStream(String tenantId, UUID taskId) {
        // 同一 (tenant, task) 解析流的串行化点：事务级顾问锁随事务结束自动释放。
        Field<Object> lock = DSL.function("pg_advisory_xact_lock", Object.class,
                DSL.function("hashtextextended", Long.class,
                        DSL.val(tenantId + "|evidence-resolution|" + taskId), DSL.val(0L)));
        dsl.select(lock).fetchSingle();
    }

    @Override
    public Optional<EvidenceResolutionState> latestResolution(String tenantId, UUID taskId) {
        Optional<LatestResolution> latest = findLatestResolution(tenantId, taskId);
        if (latest.isEmpty()) {
            return Optional.empty();
        }
        LatestResolution row = latest.get();
        return Optional.of(new EvidenceResolutionState(
                row.resolutionId(), row.generationNo(), row.conditionFactRevision(),
                listResolutionMembers(tenantId, row.resolutionId())));
    }

    @Override
    public boolean resolutionExists(String tenantId, UUID taskId) {
        EvdTaskEvidenceResolution resolution = EVD_TASK_EVIDENCE_RESOLUTION;
        Integer count = dsl.selectCount()
                .from(resolution)
                .where(resolution.TENANT_ID.eq(tenantId))
                .and(resolution.TASK_ID.eq(taskId))
                .fetchSingle()
                .value1();
        return count > 0;
    }

    @Override
    public List<EvidenceSlotView> listSlots(String tenantId, UUID taskId) {
        EvdTaskEvidenceResolution resolution = EVD_TASK_EVIDENCE_RESOLUTION;
        EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
        EvdEvidenceSlot slot = EVD_EVIDENCE_SLOT;
        // latest CTE 只作"该 task 已存在 resolution"的存在性守卫（至多一行，未参与连接条件），
        // 与原 XML 的 FROM latest 交叉连接语义一致；resolutionGeneration 取各 member 所属 resolution。
        CommonTableExpression<?> latest = DSL.name("latest")
                .fields("resolution_id", "generation_no")
                .as(dsl.select(resolution.RESOLUTION_ID, resolution.GENERATION_NO)
                        .from(resolution)
                        .where(resolution.TENANT_ID.eq(tenantId))
                        .and(resolution.TASK_ID.eq(taskId))
                        .orderBy(resolution.GENERATION_NO.desc())
                        .limit(1));
        Field<String> statusProjection = slot.STATUS_PROJECTION;
        Field<Boolean> active = DSL.inline(true).as("active");
        return dsl.with(latest)
                .select(slotViewFields(slot, member, statusProjection, active, resolution.GENERATION_NO))
                .from(latest)
                .join(member).on(member.TENANT_ID.eq(tenantId))
                .and(member.TASK_ID.eq(taskId))
                .join(resolution).on(resolution.TENANT_ID.eq(member.TENANT_ID))
                .and(resolution.RESOLUTION_ID.eq(member.RESOLUTION_ID))
                .and(member.CONDITION_RESULT.isTrue())
                .join(slot).on(slot.TENANT_ID.eq(member.TENANT_ID))
                .and(slot.SLOT_ID.eq(member.ACTIVE_SLOT_ID))
                .orderBy(slot.TEMPLATE_KEY, slot.TEMPLATE_VERSION, slot.REQUIREMENT_CODE,
                        slot.OCCURRENCE_KEY, slot.SLOT_ID)
                .fetch(row -> slotView(row, slot, member,
                        statusProjection, active, resolution.GENERATION_NO));
    }

    @Override
    public List<EvidenceSlotView> listCurrentSlots(String tenantId, UUID taskId) {
        EvdTaskEvidenceResolution resolution = EVD_TASK_EVIDENCE_RESOLUTION;
        EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
        EvdEvidenceConditionDisposition disposition = EVD_EVIDENCE_CONDITION_DISPOSITION;
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
        // 条件不满足且等待人工处置的槽位以 REVIEW_REQUIRED 投影暴露给工作台。
        Field<String> statusProjection = DSL.when(member.CONDITION_RESULT.isTrue(), slot.STATUS_PROJECTION)
                .otherwise("REVIEW_REQUIRED")
                .as("status_projection");
        Field<Boolean> active = member.CONDITION_RESULT;
        Condition currentSlot = member.RESOLUTION_ID.eq(latestResolutionId)
                .and(member.CONDITION_RESULT.isTrue());
        Condition pendingDisposition = member.REQUIRED_DISPOSITION.eq("REVIEW_REQUIRED")
                .and(disposition.MEMBER_ID.isNull());
        return dsl.with(latest)
                .select(slotViewFields(slot, member, statusProjection, active, resolutionGeneration))
                .from(latest)
                .join(member).on(member.TENANT_ID.eq(tenantId))
                .and(member.RESOLUTION_ID.eq(latestResolutionId))
                .leftJoin(disposition).on(disposition.TENANT_ID.eq(member.TENANT_ID))
                .and(disposition.MEMBER_ID.eq(member.MEMBER_ID))
                .join(slot).on(slot.TENANT_ID.eq(member.TENANT_ID))
                .and(slot.SLOT_ID.eq(DSL.coalesce(member.ACTIVE_SLOT_ID, member.PREVIOUS_SLOT_ID)))
                .where(currentSlot.or(pendingDisposition))
                .orderBy(slot.TEMPLATE_KEY, slot.TEMPLATE_VERSION, slot.REQUIREMENT_CODE,
                        slot.OCCURRENCE_KEY, slot.SLOT_ID)
                .fetch(row -> slotView(row, slot, member,
                        statusProjection, active, resolutionGeneration));
    }

    @Override
    public Optional<UUID> latestResolutionId(String tenantId, UUID taskId) {
        return findLatestResolution(tenantId, taskId).map(LatestResolution::resolutionId);
    }

    @Override
    public boolean hasPendingDisposition(String tenantId, UUID taskId) {
        EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
        EvdEvidenceConditionDisposition disposition = EVD_EVIDENCE_CONDITION_DISPOSITION;
        Integer count = dsl.selectCount()
                .from(member)
                .leftJoin(disposition).on(disposition.TENANT_ID.eq(member.TENANT_ID))
                .and(disposition.MEMBER_ID.eq(member.MEMBER_ID))
                .where(member.TENANT_ID.eq(tenantId))
                .and(member.TASK_ID.eq(taskId))
                .and(member.REQUIRED_DISPOSITION.eq("REVIEW_REQUIRED"))
                .and(disposition.MEMBER_ID.isNull())
                .fetchSingle()
                .value1();
        return count > 0;
    }

    @Override
    public Optional<PendingEvidenceConditionDisposition> findPendingDisposition(
            String tenantId, UUID resolutionId, UUID slotId
    ) {
        EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
        EvdEvidenceConditionDisposition disposition = EVD_EVIDENCE_CONDITION_DISPOSITION;
        return dsl.select(
                        member.MEMBER_ID,
                        member.RESOLUTION_ID,
                        member.TASK_ID,
                        member.PROJECT_ID,
                        member.PREVIOUS_SLOT_ID)
                .from(member)
                .leftJoin(disposition).on(disposition.TENANT_ID.eq(member.TENANT_ID))
                .and(disposition.MEMBER_ID.eq(member.MEMBER_ID))
                .where(member.TENANT_ID.eq(tenantId))
                .and(member.RESOLUTION_ID.eq(resolutionId))
                .and(member.PREVIOUS_SLOT_ID.eq(slotId))
                .and(member.REQUIRED_DISPOSITION.eq("REVIEW_REQUIRED"))
                .and(disposition.MEMBER_ID.isNull())
                .fetchOptional(row -> new PendingEvidenceConditionDisposition(
                        row.get(member.MEMBER_ID), row.get(member.RESOLUTION_ID),
                        row.get(member.TASK_ID), row.get(member.PROJECT_ID),
                        row.get(member.PREVIOUS_SLOT_ID)));
    }

    @Override
    public void insertDisposition(EvidenceConditionDisposition disposition) {
        EvdEvidenceConditionDisposition table = EVD_EVIDENCE_CONDITION_DISPOSITION;
        dsl.insertInto(table)
                .set(table.DISPOSITION_ID, disposition.dispositionId())
                .set(table.TENANT_ID, disposition.tenantId())
                .set(table.PROJECT_ID, disposition.projectId())
                .set(table.TASK_ID, disposition.taskId())
                .set(table.RESOLUTION_ID, disposition.resolutionId())
                .set(table.MEMBER_ID, disposition.memberId())
                .set(table.SLOT_ID, disposition.slotId())
                .set(table.DECISION, disposition.decision())
                .set(table.REASON_CODE, disposition.reasonCode())
                .set(table.REVIEW_REF, disposition.reviewRef())
                .set(table.DECIDED_BY, disposition.decidedBy())
                .set(table.DECIDED_AT, disposition.decidedAt())
                .set(table.REQUEST_DIGEST, disposition.requestDigest())
                .execute();
    }

    @Override
    public Optional<EvidenceConditionDisposition> findDisposition(String tenantId, UUID memberId) {
        return dsl.selectFrom(EVD_EVIDENCE_CONDITION_DISPOSITION)
                .where(EVD_EVIDENCE_CONDITION_DISPOSITION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_CONDITION_DISPOSITION.MEMBER_ID.eq(memberId))
                .fetchOptional()
                .map(this::disposition);
    }

    @Override
    public Optional<EvidenceConditionDisposition> findDispositionById(
            String tenantId, UUID dispositionId
    ) {
        return dsl.selectFrom(EVD_EVIDENCE_CONDITION_DISPOSITION)
                .where(EVD_EVIDENCE_CONDITION_DISPOSITION.TENANT_ID.eq(tenantId))
                .and(EVD_EVIDENCE_CONDITION_DISPOSITION.DISPOSITION_ID.eq(dispositionId))
                .fetchOptional()
                .map(this::disposition);
    }

    private Optional<LatestResolution> findLatestResolution(String tenantId, UUID taskId) {
        EvdTaskEvidenceResolution resolution = EVD_TASK_EVIDENCE_RESOLUTION;
        return dsl.select(resolution.RESOLUTION_ID, resolution.GENERATION_NO, resolution.CONDITION_FACT_REVISION)
                .from(resolution)
                .where(resolution.TENANT_ID.eq(tenantId))
                .and(resolution.TASK_ID.eq(taskId))
                .orderBy(resolution.GENERATION_NO.desc())
                .limit(1)
                .fetchOptional(row -> new LatestResolution(row.value1(), row.value2(), row.value3()));
    }

    private List<EvidenceResolutionMemberState> listResolutionMembers(String tenantId, UUID resolutionId) {
        EvdEvidenceResolutionMember member = EVD_EVIDENCE_RESOLUTION_MEMBER;
        EvdEvidenceSlot slot = EVD_EVIDENCE_SLOT.as("slot");
        EvdEvidenceSlot previous = EVD_EVIDENCE_SLOT.as("previous");
        EvdEvidenceConditionDisposition disposition = EVD_EVIDENCE_CONDITION_DISPOSITION;
        Field<Integer> slotGeneration =
                DSL.coalesce(slot.SLOT_GENERATION, previous.SLOT_GENERATION, DSL.inline(0));
        return dsl.select(
                        member.MEMBER_ID,
                        member.TEMPLATE_VERSION_ID,
                        member.REQUIREMENT_CODE,
                        member.OCCURRENCE_KEY,
                        member.CONDITION_RESULT,
                        member.ACTIVE_SLOT_ID,
                        member.PREVIOUS_SLOT_ID,
                        slotGeneration,
                        member.REQUIRED_DISPOSITION,
                        disposition.DECISION)
                .from(member)
                .leftJoin(slot).on(slot.TENANT_ID.eq(member.TENANT_ID))
                .and(slot.SLOT_ID.eq(member.ACTIVE_SLOT_ID))
                .leftJoin(previous).on(previous.TENANT_ID.eq(member.TENANT_ID))
                .and(previous.SLOT_ID.eq(member.PREVIOUS_SLOT_ID))
                .leftJoin(disposition).on(disposition.TENANT_ID.eq(member.TENANT_ID))
                .and(disposition.MEMBER_ID.eq(member.MEMBER_ID))
                .where(member.TENANT_ID.eq(tenantId))
                .and(member.RESOLUTION_ID.eq(resolutionId))
                .orderBy(member.TEMPLATE_VERSION_ID, member.REQUIREMENT_CODE, member.OCCURRENCE_KEY)
                .fetch(row -> new EvidenceResolutionMemberState(
                        row.get(member.MEMBER_ID),
                        row.get(member.TEMPLATE_VERSION_ID),
                        row.get(member.REQUIREMENT_CODE),
                        row.get(member.OCCURRENCE_KEY),
                        Boolean.TRUE.equals(row.get(member.CONDITION_RESULT)),
                        row.get(member.ACTIVE_SLOT_ID),
                        row.get(member.PREVIOUS_SLOT_ID),
                        row.get(slotGeneration),
                        row.get(member.REQUIRED_DISPOSITION),
                        row.get(disposition.DECISION)));
    }

    /**
     * Slot 视图投影列：condition_input_digest 与 resolution_explanation 取 member 事实，
     * current_resolution_id 为 member 所属 resolution；status/active/resolutionGeneration
     * 由调用方按查询场景给定（computed 字段须在调用方预先取好别名并保持同一引用读取）。
     */
    private static List<SelectField<?>> slotViewFields(
            EvdEvidenceSlot slot,
            EvdEvidenceResolutionMember member,
            SelectField<?> statusProjection,
            SelectField<?> active,
            SelectField<?> resolutionGeneration
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
                statusProjection,
                slot.RESOLVED_AT,
                slot.SLOT_GENERATION,
                slot.SUPERSEDES_SLOT_ID,
                member.RESOLUTION_ID.as("current_resolution_id"),
                resolutionGeneration,
                active,
                member.TRANSITION,
                member.REQUIRED_DISPOSITION);
    }

    private EvidenceSlotView slotView(
            Record row,
            EvdEvidenceSlot slot,
            EvdEvidenceResolutionMember member,
            Field<String> statusProjection,
            Field<Boolean> active,
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
                row.get(statusProjection),
                row.get(slot.RESOLVED_AT),
                row.get(slot.SLOT_GENERATION),
                row.get(slot.SUPERSEDES_SLOT_ID),
                row.get("current_resolution_id", UUID.class),
                row.get(resolutionGeneration),
                Boolean.TRUE.equals(row.get(active)),
                row.get(member.TRANSITION),
                row.get(member.REQUIRED_DISPOSITION));
    }

    private EvidenceConditionDisposition disposition(EvdEvidenceConditionDispositionRecord row) {
        return new EvidenceConditionDisposition(
                row.getDispositionId(), row.getTenantId(), row.getProjectId(), row.getTaskId(),
                row.getResolutionId(), row.getMemberId(), row.getSlotId(), row.getDecision(),
                row.getReasonCode(), row.getReviewRef(), row.getDecidedBy(), row.getDecidedAt(),
                row.getRequestDigest());
    }

    private record LatestResolution(UUID resolutionId, int generationNo, int conditionFactRevision) {
    }
}
