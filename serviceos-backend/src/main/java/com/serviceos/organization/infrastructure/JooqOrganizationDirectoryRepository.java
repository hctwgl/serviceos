package com.serviceos.organization.infrastructure;

import com.serviceos.jooq.generated.tables.OrgDirectorySyncBatch;
import com.serviceos.jooq.generated.tables.OrgDirectorySyncItem;
import com.serviceos.jooq.generated.tables.OrgMembership;
import com.serviceos.jooq.generated.tables.OrgOrganization;
import com.serviceos.jooq.generated.tables.OrgReassignmentWorkItem;
import com.serviceos.jooq.generated.tables.OrgStructureEvent;
import com.serviceos.jooq.generated.tables.OrgUnit;
import com.serviceos.jooq.generated.tables.OrgUnitClosure;
import com.serviceos.organization.api.DirectorySyncBatchView;
import com.serviceos.organization.api.DirectorySyncItemView;
import com.serviceos.organization.api.ReassignmentWorkItemView;
import com.serviceos.organization.application.OrganizationDirectoryRepository;
import com.serviceos.organization.domain.Organization;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.OrgDirectorySyncBatch.ORG_DIRECTORY_SYNC_BATCH;
import static com.serviceos.jooq.generated.tables.OrgDirectorySyncItem.ORG_DIRECTORY_SYNC_ITEM;
import static com.serviceos.jooq.generated.tables.OrgMembership.ORG_MEMBERSHIP;
import static com.serviceos.jooq.generated.tables.OrgOrganization.ORG_ORGANIZATION;
import static com.serviceos.jooq.generated.tables.OrgReassignmentWorkItem.ORG_REASSIGNMENT_WORK_ITEM;
import static com.serviceos.jooq.generated.tables.OrgStructureEvent.ORG_STRUCTURE_EVENT;
import static com.serviceos.jooq.generated.tables.OrgUnit.ORG_UNIT;
import static com.serviceos.jooq.generated.tables.OrgUnitClosure.ORG_UNIT_CLOSURE;

/**
 * 组织目录 jOOQ 适配器：closure 移动、同步批次 advisory lock 与任职唯一约束依赖
 * PostgreSQL 精确语义，必须由真实 PostgreSQL IT 证明。
 */
@Repository
final class JooqOrganizationDirectoryRepository implements OrganizationDirectoryRepository {
    private final DSLContext dsl;

    JooqOrganizationDirectoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<Organization> findOrganization(String tenantId, UUID organizationId) {
        return orgQuery(tenantId, organizationId, false);
    }

    @Override
    public Optional<Organization> findOrganizationForUpdate(String tenantId, UUID organizationId) {
        return orgQuery(tenantId, organizationId, true);
    }

    private Optional<Organization> orgQuery(String tenantId, UUID organizationId, boolean forUpdate) {
        OrgOrganization o = ORG_ORGANIZATION;
        var query = dsl.select(o.ORGANIZATION_ID, o.TENANT_ID, o.ORGANIZATION_CODE, o.ORGANIZATION_NAME,
                        o.AUTHORITY_MODE, o.ORGANIZATION_STATUS, o.SOURCE_SYSTEM, o.SOURCE_KEY,
                        o.AGGREGATE_VERSION, o.CREATED_AT, o.UPDATED_AT)
                .from(o)
                .where(o.TENANT_ID.eq(tenantId))
                .and(o.ORGANIZATION_ID.eq(organizationId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(this::mapOrganization);
        }
        return query.fetchOptional(this::mapOrganization);
    }

    @Override
    public List<Organization> listOrganizations(String tenantId) {
        OrgOrganization o = ORG_ORGANIZATION;
        return dsl.select(o.ORGANIZATION_ID, o.TENANT_ID, o.ORGANIZATION_CODE, o.ORGANIZATION_NAME,
                        o.AUTHORITY_MODE, o.ORGANIZATION_STATUS, o.SOURCE_SYSTEM, o.SOURCE_KEY,
                        o.AGGREGATE_VERSION, o.CREATED_AT, o.UPDATED_AT)
                .from(o)
                .where(o.TENANT_ID.eq(tenantId))
                .orderBy(o.ORGANIZATION_CODE)
                .fetch(this::mapOrganization);
    }

    @Override
    public void insertOrganization(Organization organization) {
        OrgOrganization o = ORG_ORGANIZATION;
        dsl.insertInto(o)
                .set(o.ORGANIZATION_ID, organization.id())
                .set(o.TENANT_ID, organization.tenantId())
                .set(o.ORGANIZATION_CODE, organization.code())
                .set(o.ORGANIZATION_NAME, organization.name())
                .set(o.AUTHORITY_MODE, organization.authorityMode().name())
                .set(o.ORGANIZATION_STATUS, organization.status().name())
                .set(o.SOURCE_SYSTEM, organization.sourceSystem())
                .set(o.SOURCE_KEY, organization.sourceKey())
                .set(o.AGGREGATE_VERSION, organization.version())
                .set(o.CREATED_AT, organization.createdAt())
                .set(o.UPDATED_AT, organization.updatedAt())
                .execute();
    }

    @Override
    public boolean advanceOrganizationVersion(String tenantId, UUID organizationId, long expectedVersion, Instant now) {
        OrgOrganization o = ORG_ORGANIZATION;
        return dsl.update(o)
                .set(o.AGGREGATE_VERSION, o.AGGREGATE_VERSION.plus(1))
                .set(o.UPDATED_AT, now)
                .where(o.TENANT_ID.eq(tenantId))
                .and(o.ORGANIZATION_ID.eq(organizationId))
                .and(o.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public Optional<com.serviceos.organization.domain.OrgUnit> findUnit(String tenantId, UUID unitId) {
        return unitQuery(tenantId, unitId, false);
    }

    @Override
    public Optional<com.serviceos.organization.domain.OrgUnit> findUnitForUpdate(String tenantId, UUID unitId) {
        return unitQuery(tenantId, unitId, true);
    }

    private Optional<com.serviceos.organization.domain.OrgUnit> unitQuery(String tenantId, UUID unitId, boolean forUpdate) {
        OrgUnit u = ORG_UNIT;
        var query = dsl.select(u.ORG_UNIT_ID, u.TENANT_ID, u.ORGANIZATION_ID, u.PARENT_UNIT_ID,
                        u.UNIT_CODE, u.UNIT_NAME, u.UNIT_STATUS, u.SOURCE_SYSTEM, u.SOURCE_KEY,
                        u.SOURCE_VERSION, u.AGGREGATE_VERSION, u.CREATED_AT, u.UPDATED_AT)
                .from(u)
                .where(u.TENANT_ID.eq(tenantId))
                .and(u.ORG_UNIT_ID.eq(unitId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(this::mapUnit);
        }
        return query.fetchOptional(this::mapUnit);
    }

    @Override
    public Optional<com.serviceos.organization.domain.OrgUnit> findUnitBySource(
            String tenantId, String sourceSystem, String sourceKey
    ) {
        OrgUnit u = ORG_UNIT;
        return dsl.select(u.ORG_UNIT_ID, u.TENANT_ID, u.ORGANIZATION_ID, u.PARENT_UNIT_ID,
                        u.UNIT_CODE, u.UNIT_NAME, u.UNIT_STATUS, u.SOURCE_SYSTEM, u.SOURCE_KEY,
                        u.SOURCE_VERSION, u.AGGREGATE_VERSION, u.CREATED_AT, u.UPDATED_AT)
                .from(u)
                .where(u.TENANT_ID.eq(tenantId))
                .and(u.SOURCE_SYSTEM.eq(sourceSystem))
                .and(u.SOURCE_KEY.eq(sourceKey))
                .fetchOptional(this::mapUnit);
    }

    @Override
    public List<com.serviceos.organization.domain.OrgUnit> listUnits(String tenantId, UUID organizationId) {
        OrgUnit u = ORG_UNIT;
        return dsl.select(u.ORG_UNIT_ID, u.TENANT_ID, u.ORGANIZATION_ID, u.PARENT_UNIT_ID,
                        u.UNIT_CODE, u.UNIT_NAME, u.UNIT_STATUS, u.SOURCE_SYSTEM, u.SOURCE_KEY,
                        u.SOURCE_VERSION, u.AGGREGATE_VERSION, u.CREATED_AT, u.UPDATED_AT)
                .from(u)
                .where(u.TENANT_ID.eq(tenantId))
                .and(u.ORGANIZATION_ID.eq(organizationId))
                .orderBy(u.UNIT_CODE)
                .fetch(this::mapUnit);
    }

    @Override
    public void insertUnit(com.serviceos.organization.domain.OrgUnit unit) {
        OrgUnit u = ORG_UNIT;
        dsl.insertInto(u)
                .set(u.ORG_UNIT_ID, unit.id())
                .set(u.TENANT_ID, unit.tenantId())
                .set(u.ORGANIZATION_ID, unit.organizationId())
                .set(u.PARENT_UNIT_ID, unit.parentUnitId())
                .set(u.UNIT_CODE, unit.unitCode())
                .set(u.UNIT_NAME, unit.unitName())
                .set(u.UNIT_STATUS, unit.status().name())
                .set(u.SOURCE_SYSTEM, unit.sourceSystem())
                .set(u.SOURCE_KEY, unit.sourceKey())
                .set(u.SOURCE_VERSION, unit.sourceVersion())
                .set(u.AGGREGATE_VERSION, unit.version())
                .set(u.CREATED_AT, unit.createdAt())
                .set(u.UPDATED_AT, unit.updatedAt())
                .execute();
    }

    @Override
    public void insertUnitClosure(String tenantId, UUID organizationId, UUID unitId, UUID parentUnitId) {
        OrgUnitClosure c = ORG_UNIT_CLOSURE;
        dsl.insertInto(c)
                .set(c.TENANT_ID, tenantId)
                .set(c.ORGANIZATION_ID, organizationId)
                .set(c.ANCESTOR_ID, unitId)
                .set(c.DESCENDANT_ID, unitId)
                .set(c.DEPTH, 0)
                .execute();
        if (parentUnitId != null) {
            dsl.insertInto(c, c.TENANT_ID, c.ORGANIZATION_ID, c.ANCESTOR_ID, c.DESCENDANT_ID, c.DEPTH)
                    .select(dsl.select(DSL.val(tenantId, c.TENANT_ID),
                                    DSL.val(organizationId, c.ORGANIZATION_ID),
                                    c.ANCESTOR_ID,
                                    DSL.val(unitId, c.DESCENDANT_ID),
                                    c.DEPTH.plus(1))
                            .from(c)
                            .where(c.TENANT_ID.eq(tenantId))
                            .and(c.DESCENDANT_ID.eq(parentUnitId)))
                    .execute();
        }
    }

    @Override
    public boolean updateUnitParent(
            String tenantId, UUID unitId, long expectedVersion, UUID newParentUnitId, Instant now
    ) {
        OrgUnit u = ORG_UNIT;
        return dsl.update(u)
                .set(u.PARENT_UNIT_ID, newParentUnitId)
                .set(u.AGGREGATE_VERSION, u.AGGREGATE_VERSION.plus(1))
                .set(u.UPDATED_AT, now)
                .where(u.TENANT_ID.eq(tenantId))
                .and(u.ORG_UNIT_ID.eq(unitId))
                .and(u.AGGREGATE_VERSION.eq(expectedVersion))
                .execute() == 1;
    }

    @Override
    public boolean isDescendant(String tenantId, UUID ancestorId, UUID descendantId) {
        if (ancestorId.equals(descendantId)) return true;
        OrgUnitClosure c = ORG_UNIT_CLOSURE;
        return dsl.fetchExists(c,
                c.TENANT_ID.eq(tenantId),
                c.ANCESTOR_ID.eq(ancestorId),
                c.DESCENDANT_ID.eq(descendantId),
                c.DEPTH.gt(0));
    }

    @Override
    public void rebuildClosureForMove(
            String tenantId, UUID organizationId, UUID unitId, UUID newParentUnitId
    ) {
        // 先断开子树与旧祖先之间的闭包边，保留子树内部闭包。
        OrgUnitClosure c = ORG_UNIT_CLOSURE.as("c");
        OrgUnitClosure sub = ORG_UNIT_CLOSURE.as("sub");
        OrgUnitClosure sup = ORG_UNIT_CLOSURE.as("sup");
        dsl.deleteFrom(c)
                .using(sub, sup)
                .where(c.TENANT_ID.eq(tenantId))
                .and(sub.ANCESTOR_ID.eq(unitId))
                .and(sup.DESCENDANT_ID.eq(unitId))
                .and(c.DESCENDANT_ID.eq(sub.DESCENDANT_ID))
                .and(c.ANCESTOR_ID.eq(sup.ANCESTOR_ID))
                .and(sup.ANCESTOR_ID.ne(sub.DESCENDANT_ID))
                .execute();
        if (newParentUnitId != null) {
            dsl.insertInto(ORG_UNIT_CLOSURE, ORG_UNIT_CLOSURE.TENANT_ID, ORG_UNIT_CLOSURE.ORGANIZATION_ID,
                            ORG_UNIT_CLOSURE.ANCESTOR_ID, ORG_UNIT_CLOSURE.DESCENDANT_ID, ORG_UNIT_CLOSURE.DEPTH)
                    .select(dsl.select(DSL.val(tenantId, ORG_UNIT_CLOSURE.TENANT_ID),
                                    DSL.val(organizationId, ORG_UNIT_CLOSURE.ORGANIZATION_ID),
                                    sup.ANCESTOR_ID,
                                    sub.DESCENDANT_ID,
                                    sup.DEPTH.plus(sub.DEPTH).plus(1))
                            .from(sup)
                            .crossJoin(sub)
                            .where(sup.TENANT_ID.eq(tenantId))
                            .and(sub.TENANT_ID.eq(tenantId))
                            .and(sup.DESCENDANT_ID.eq(newParentUnitId))
                            .and(sub.ANCESTOR_ID.eq(unitId)))
                    .execute();
        }
    }

    @Override
    public Optional<com.serviceos.organization.domain.OrgMembership> findMembership(String tenantId, UUID membershipId) {
        return membershipQuery(tenantId, membershipId, false);
    }

    @Override
    public Optional<com.serviceos.organization.domain.OrgMembership> findMembershipForUpdate(
            String tenantId, UUID membershipId
    ) {
        return membershipQuery(tenantId, membershipId, true);
    }

    private Optional<com.serviceos.organization.domain.OrgMembership> membershipQuery(
            String tenantId, UUID membershipId, boolean forUpdate
    ) {
        OrgMembership m = ORG_MEMBERSHIP;
        var query = membershipSelect()
                .where(m.TENANT_ID.eq(tenantId))
                .and(m.MEMBERSHIP_ID.eq(membershipId));
        if (forUpdate) {
            return query.forUpdate().fetchOptional(this::mapMembership);
        }
        return query.fetchOptional(this::mapMembership);
    }

    @Override
    public Optional<com.serviceos.organization.domain.OrgMembership> findMembershipBySource(
            String tenantId, String sourceSystem, String sourceKey
    ) {
        OrgMembership m = ORG_MEMBERSHIP;
        return membershipSelect()
                .where(m.TENANT_ID.eq(tenantId))
                .and(m.SOURCE_SYSTEM.eq(sourceSystem))
                .and(m.SOURCE_KEY.eq(sourceKey))
                .fetchOptional(this::mapMembership);
    }

    @Override
    public List<com.serviceos.organization.domain.OrgMembership> listMemberships(
            String tenantId, UUID organizationId, UUID unitId, UUID principalId
    ) {
        OrgMembership m = ORG_MEMBERSHIP;
        Condition condition = m.TENANT_ID.eq(tenantId);
        if (organizationId != null) condition = condition.and(m.ORGANIZATION_ID.eq(organizationId));
        if (unitId != null) condition = condition.and(m.ORG_UNIT_ID.eq(unitId));
        if (principalId != null) condition = condition.and(m.PRINCIPAL_ID.eq(principalId));
        return membershipSelect()
                .where(condition)
                .orderBy(m.VALID_FROM.desc(), m.MEMBERSHIP_ID)
                .fetch(this::mapMembership);
    }

    private org.jooq.SelectWhereStep<org.jooq.Record18<UUID, String, UUID, UUID, UUID, String, String,
            Instant, Instant, String, String, Long, Long, String, Instant, String, Instant, String>> membershipSelect() {
        OrgMembership m = ORG_MEMBERSHIP;
        return dsl.select(m.MEMBERSHIP_ID, m.TENANT_ID, m.ORGANIZATION_ID, m.ORG_UNIT_ID, m.PRINCIPAL_ID,
                        m.MEMBERSHIP_TYPE, m.MEMBERSHIP_STATUS, m.VALID_FROM, m.VALID_TO,
                        m.SOURCE_SYSTEM, m.SOURCE_KEY, m.SOURCE_VERSION, m.AGGREGATE_VERSION,
                        m.CREATED_BY, m.CREATED_AT, m.TERMINATED_BY, m.TERMINATED_AT, m.TERMINATE_REASON)
                .from(m);
    }

    @Override
    public void insertMembership(com.serviceos.organization.domain.OrgMembership membership) {
        OrgMembership m = ORG_MEMBERSHIP;
        try {
            dsl.insertInto(m)
                    .set(m.MEMBERSHIP_ID, membership.id())
                    .set(m.TENANT_ID, membership.tenantId())
                    .set(m.ORGANIZATION_ID, membership.organizationId())
                    .set(m.ORG_UNIT_ID, membership.orgUnitId())
                    .set(m.PRINCIPAL_ID, membership.principalId())
                    .set(m.MEMBERSHIP_TYPE, membership.membershipType().name())
                    .set(m.MEMBERSHIP_STATUS, membership.status().name())
                    .set(m.VALID_FROM, membership.validFrom())
                    .set(m.SOURCE_SYSTEM, membership.sourceSystem())
                    .set(m.SOURCE_KEY, membership.sourceKey())
                    .set(m.SOURCE_VERSION, membership.sourceVersion())
                    .set(m.AGGREGATE_VERSION, membership.version())
                    .set(m.CREATED_BY, membership.createdBy())
                    .set(m.CREATED_AT, membership.createdAt())
                    .execute();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.ORGANIZATION_MEMBERSHIP_CONFLICT, "同一主体已存在有效主职");
        }
    }

    @Override
    public boolean terminateMembership(
            String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt
    ) {
        OrgMembership m = ORG_MEMBERSHIP;
        return dsl.update(m)
                .set(m.MEMBERSHIP_STATUS, "TERMINATED")
                .set(m.VALID_TO, terminatedAt)
                .set(m.TERMINATED_BY, actorId)
                .set(m.TERMINATED_AT, terminatedAt)
                .set(m.TERMINATE_REASON, reason)
                .set(m.AGGREGATE_VERSION, m.AGGREGATE_VERSION.plus(1))
                .where(m.TENANT_ID.eq(tenantId))
                .and(m.MEMBERSHIP_ID.eq(membershipId))
                .and(m.AGGREGATE_VERSION.eq(expectedVersion))
                .and(m.MEMBERSHIP_STATUS.eq("ACTIVE"))
                .execute() == 1;
    }

    @Override
    public void lockSyncBatchKey(String tenantId, String sourceSystem, String externalBatchKey) {
        // 锁键包含 tenant，避免不同租户相同来源批次键相互阻塞；hash 冲突只会造成短暂串行，不破坏正确性。
        dsl.select(DSL.function("pg_advisory_xact_lock", Object.class,
                        DSL.function("hashtextextended", Long.class,
                                DSL.val(tenantId + "\u001f" + sourceSystem + "\u001f" + externalBatchKey), DSL.val(0L))))
                .fetchSingle();
    }

    @Override
    public Optional<UUID> findSyncBatchId(String tenantId, String sourceSystem, String externalBatchKey) {
        OrgDirectorySyncBatch b = ORG_DIRECTORY_SYNC_BATCH;
        return dsl.select(b.BATCH_ID)
                .from(b)
                .where(b.TENANT_ID.eq(tenantId))
                .and(b.SOURCE_SYSTEM.eq(sourceSystem))
                .and(b.EXTERNAL_BATCH_KEY.eq(externalBatchKey))
                .fetchOptional(b.BATCH_ID);
    }

    @Override
    public UUID insertSyncBatch(
            UUID batchId, String tenantId, UUID organizationId, String sourceSystem,
            String externalBatchKey, String actorId, String correlationId, String requestDigest,
            Instant receivedAt
    ) {
        OrgDirectorySyncBatch b = ORG_DIRECTORY_SYNC_BATCH;
        dsl.insertInto(b)
                .set(b.BATCH_ID, batchId)
                .set(b.TENANT_ID, tenantId)
                .set(b.ORGANIZATION_ID, organizationId)
                .set(b.SOURCE_SYSTEM, sourceSystem)
                .set(b.EXTERNAL_BATCH_KEY, externalBatchKey)
                .set(b.BATCH_STATUS, "RECEIVED")
                .set(b.RECEIVED_AT, receivedAt)
                .set(b.ACTOR_ID, actorId)
                .set(b.CORRELATION_ID, correlationId)
                .set(b.REQUEST_DIGEST, requestDigest)
                .execute();
        return batchId;
    }

    @Override
    public void insertSyncItem(
            UUID itemId, UUID batchId, String tenantId, int itemIndex, String operationType,
            String sourceKey, long externalVersion, String itemStatus, String resultCode,
            String resultMessage, String resourceType, UUID resourceId, Instant processedAt
    ) {
        OrgDirectorySyncItem i = ORG_DIRECTORY_SYNC_ITEM;
        dsl.insertInto(i)
                .set(i.ITEM_ID, itemId)
                .set(i.BATCH_ID, batchId)
                .set(i.TENANT_ID, tenantId)
                .set(i.ITEM_INDEX, itemIndex)
                .set(i.OPERATION_TYPE, operationType)
                .set(i.SOURCE_KEY, sourceKey)
                .set(i.EXTERNAL_VERSION, externalVersion)
                .set(i.ITEM_STATUS, itemStatus)
                .set(i.RESULT_CODE, resultCode)
                .set(i.RESULT_MESSAGE, resultMessage)
                .set(i.RESOURCE_TYPE, resourceType)
                .set(i.RESOURCE_ID, resourceId)
                .set(i.PROCESSED_AT, processedAt)
                .execute();
    }

    @Override
    public void completeSyncBatch(
            UUID batchId, String batchStatus, int successCount, int failedCount,
            int skippedCount, Instant completedAt
    ) {
        OrgDirectorySyncBatch b = ORG_DIRECTORY_SYNC_BATCH;
        dsl.update(b)
                .set(b.BATCH_STATUS, batchStatus)
                .set(b.COMPLETED_AT, completedAt)
                .set(b.SUCCESS_COUNT, successCount)
                .set(b.FAILED_COUNT, failedCount)
                .set(b.SKIPPED_COUNT, skippedCount)
                .where(b.BATCH_ID.eq(batchId))
                .execute();
    }

    @Override
    public Optional<DirectorySyncBatchView> findSyncBatch(String tenantId, UUID batchId) {
        OrgDirectorySyncBatch b = ORG_DIRECTORY_SYNC_BATCH;
        return dsl.select(b.BATCH_ID, b.ORGANIZATION_ID, b.SOURCE_SYSTEM, b.EXTERNAL_BATCH_KEY,
                        b.BATCH_STATUS, b.RECEIVED_AT, b.COMPLETED_AT,
                        b.SUCCESS_COUNT, b.FAILED_COUNT, b.SKIPPED_COUNT)
                .from(b)
                .where(b.TENANT_ID.eq(tenantId))
                .and(b.BATCH_ID.eq(batchId))
                .fetchOptional(record -> new DirectorySyncBatchView(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5(),
                        record.value6(), record.value7(), record.value8(), record.value9(), record.value10(),
                        listSyncItems(batchId)));
    }

    private List<DirectorySyncItemView> listSyncItems(UUID batchId) {
        OrgDirectorySyncItem i = ORG_DIRECTORY_SYNC_ITEM;
        return dsl.select(i.ITEM_ID, i.ITEM_INDEX, i.OPERATION_TYPE, i.SOURCE_KEY, i.EXTERNAL_VERSION,
                        i.ITEM_STATUS, i.RESULT_CODE, i.RESULT_MESSAGE, i.RESOURCE_TYPE, i.RESOURCE_ID,
                        i.PROCESSED_AT)
                .from(i)
                .where(i.BATCH_ID.eq(batchId))
                .orderBy(i.ITEM_INDEX)
                .fetch(record -> new DirectorySyncItemView(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5(),
                        record.value6(), record.value7(), record.value8(), record.value9(), record.value10(),
                        record.value11()));
    }

    @Override
    public Long findUnitSourceVersion(String tenantId, String sourceSystem, String sourceKey) {
        OrgUnit u = ORG_UNIT;
        return dsl.select(u.SOURCE_VERSION)
                .from(u)
                .where(u.TENANT_ID.eq(tenantId))
                .and(u.SOURCE_SYSTEM.eq(sourceSystem))
                .and(u.SOURCE_KEY.eq(sourceKey))
                .fetchOptional(u.SOURCE_VERSION)
                .orElse(null);
    }

    @Override
    public Long findMembershipSourceVersion(String tenantId, String sourceSystem, String sourceKey) {
        OrgMembership m = ORG_MEMBERSHIP;
        return dsl.select(m.SOURCE_VERSION)
                .from(m)
                .where(m.TENANT_ID.eq(tenantId))
                .and(m.SOURCE_SYSTEM.eq(sourceSystem))
                .and(m.SOURCE_KEY.eq(sourceKey))
                .fetchOptional(m.SOURCE_VERSION)
                .orElse(null);
    }

    @Override
    public void upsertUnitFromSync(
            UUID unitId, String tenantId, UUID organizationId, UUID parentUnitId,
            String unitCode, String unitName, String sourceSystem, String sourceKey,
            long sourceVersion, Instant now
    ) {
        Optional<com.serviceos.organization.domain.OrgUnit> existing = findUnitBySource(tenantId, sourceSystem, sourceKey);
        if (existing.isPresent()) {
            com.serviceos.organization.domain.OrgUnit current = existing.get();
            UUID currentParent = current.parentUnitId();
            boolean parentChanged = (currentParent == null) != (parentUnitId == null)
                    || (currentParent != null && !currentParent.equals(parentUnitId));
            if (parentChanged && parentUnitId != null && isDescendant(tenantId, current.id(), parentUnitId)) {
                throw new BusinessProblem(ProblemCode.ORGANIZATION_UNIT_CYCLE, "同步不能把单元移动到其子孙节点下");
            }
            OrgUnit u = ORG_UNIT;
            dsl.update(u)
                    .set(u.UNIT_CODE, unitCode)
                    .set(u.UNIT_NAME, unitName)
                    .set(u.PARENT_UNIT_ID, parentUnitId)
                    .set(u.SOURCE_VERSION, sourceVersion)
                    .set(u.UPDATED_AT, now)
                    .set(u.AGGREGATE_VERSION, u.AGGREGATE_VERSION.plus(1))
                    .where(u.TENANT_ID.eq(tenantId))
                    .and(u.ORG_UNIT_ID.eq(current.id()))
                    .execute();
            // 同步路径也可能改父节点；必须同步重建 closure，避免下级授权依据陈旧。
            if (parentChanged) {
                rebuildClosureForMove(tenantId, organizationId, current.id(), parentUnitId);
            }
        } else {
            com.serviceos.organization.domain.OrgUnit unit = new com.serviceos.organization.domain.OrgUnit(
                    unitId, tenantId, organizationId, parentUnitId, unitCode, unitName,
                    com.serviceos.organization.domain.OrgUnit.Status.ACTIVE, sourceSystem, sourceKey,
                    sourceVersion, 1, now, now);
            insertUnit(unit);
            insertUnitClosure(tenantId, organizationId, unitId, parentUnitId);
        }
    }

    @Override
    public void upsertMembershipFromSync(
            UUID membershipId, String tenantId, UUID organizationId, UUID orgUnitId,
            UUID principalId, String membershipType, Instant validFrom,
            String sourceSystem, String sourceKey, long sourceVersion,
            String actorId, Instant now
    ) {
        Optional<com.serviceos.organization.domain.OrgMembership> existing =
                findMembershipBySource(tenantId, sourceSystem, sourceKey);
        if (existing.isPresent()) {
            OrgMembership m = ORG_MEMBERSHIP;
            try {
                dsl.update(m)
                        .set(m.ORG_UNIT_ID, orgUnitId)
                        .set(m.MEMBERSHIP_TYPE, membershipType)
                        .set(m.VALID_FROM, validFrom)
                        .set(m.SOURCE_VERSION, sourceVersion)
                        .set(m.AGGREGATE_VERSION, m.AGGREGATE_VERSION.plus(1))
                        .where(m.TENANT_ID.eq(tenantId))
                        .and(m.MEMBERSHIP_ID.eq(existing.get().id()))
                        .and(m.MEMBERSHIP_STATUS.eq("ACTIVE"))
                        .execute();
            } catch (DuplicateKeyException exception) {
                throw new BusinessProblem(ProblemCode.ORGANIZATION_MEMBERSHIP_CONFLICT, "同一主体已存在有效主职");
            }
        } else {
            com.serviceos.organization.domain.OrgMembership membership =
                    new com.serviceos.organization.domain.OrgMembership(membershipId, tenantId, organizationId,
                            orgUnitId, principalId,
                            com.serviceos.organization.domain.OrgMembership.MembershipType.valueOf(membershipType),
                            com.serviceos.organization.domain.OrgMembership.Status.ACTIVE, validFrom, null,
                            sourceSystem, sourceKey, sourceVersion, 1, actorId, now, null, null, null);
            insertMembership(membership);
        }
    }

    @Override
    public void insertReassignmentWorkItem(
            UUID workItemId, String tenantId, UUID organizationId, UUID membershipId,
            UUID principalId, String reason, String actorId, String correlationId, Instant now
    ) {
        OrgReassignmentWorkItem w = ORG_REASSIGNMENT_WORK_ITEM;
        dsl.insertInto(w)
                .set(w.WORK_ITEM_ID, workItemId)
                .set(w.TENANT_ID, tenantId)
                .set(w.ORGANIZATION_ID, organizationId)
                .set(w.MEMBERSHIP_ID, membershipId)
                .set(w.PRINCIPAL_ID, principalId)
                .set(w.WORK_ITEM_STATUS, "OPEN")
                .set(w.REASON, reason)
                .set(w.CREATED_BY, actorId)
                .set(w.CREATED_AT, now)
                .set(w.CORRELATION_ID, correlationId)
                .execute();
    }

    @Override
    public List<ReassignmentWorkItemView> listOpenReassignmentWorkItems(String tenantId) {
        OrgReassignmentWorkItem w = ORG_REASSIGNMENT_WORK_ITEM;
        return dsl.select(w.WORK_ITEM_ID, w.ORGANIZATION_ID, w.MEMBERSHIP_ID, w.PRINCIPAL_ID,
                        w.WORK_ITEM_STATUS, w.REASON, w.CREATED_BY, w.CREATED_AT, w.CORRELATION_ID)
                .from(w)
                .where(w.TENANT_ID.eq(tenantId))
                .and(w.WORK_ITEM_STATUS.eq("OPEN"))
                .orderBy(w.CREATED_AT, w.WORK_ITEM_ID)
                .fetch(record -> new ReassignmentWorkItemView(
                        record.value1(), record.value2(), record.value3(), record.value4(), record.value5(),
                        record.value6(), record.value7(), record.value8(), record.value9()));
    }

    @Override
    public void insertStructureEvent(
            UUID eventId, String tenantId, UUID organizationId, String eventType,
            String resourceType, UUID resourceId, long resourceVersion, String reason,
            String actorId, String requestDigest, String correlationId, Instant occurredAt
    ) {
        OrgStructureEvent e = ORG_STRUCTURE_EVENT;
        dsl.insertInto(e)
                .set(e.STRUCTURE_EVENT_ID, eventId)
                .set(e.TENANT_ID, tenantId)
                .set(e.ORGANIZATION_ID, organizationId)
                .set(e.EVENT_TYPE, eventType)
                .set(e.RESOURCE_TYPE, resourceType)
                .set(e.RESOURCE_ID, resourceId)
                .set(e.RESOURCE_VERSION, resourceVersion)
                .set(e.REASON, reason)
                .set(e.ACTOR_ID, actorId)
                .set(e.REQUEST_DIGEST, requestDigest)
                .set(e.CORRELATION_ID, correlationId)
                .set(e.OCCURRED_AT, occurredAt)
                .execute();
    }

    private Organization mapOrganization(Record record) {
        OrgOrganization o = ORG_ORGANIZATION;
        return new Organization(
                record.get(o.ORGANIZATION_ID),
                record.get(o.TENANT_ID),
                record.get(o.ORGANIZATION_CODE),
                record.get(o.ORGANIZATION_NAME),
                Organization.AuthorityMode.valueOf(record.get(o.AUTHORITY_MODE)),
                Organization.Status.valueOf(record.get(o.ORGANIZATION_STATUS)),
                record.get(o.SOURCE_SYSTEM),
                record.get(o.SOURCE_KEY),
                record.get(o.AGGREGATE_VERSION),
                record.get(o.CREATED_AT),
                record.get(o.UPDATED_AT));
    }

    private com.serviceos.organization.domain.OrgUnit mapUnit(Record record) {
        OrgUnit u = ORG_UNIT;
        return new com.serviceos.organization.domain.OrgUnit(
                record.get(u.ORG_UNIT_ID),
                record.get(u.TENANT_ID),
                record.get(u.ORGANIZATION_ID),
                record.get(u.PARENT_UNIT_ID),
                record.get(u.UNIT_CODE),
                record.get(u.UNIT_NAME),
                com.serviceos.organization.domain.OrgUnit.Status.valueOf(record.get(u.UNIT_STATUS)),
                record.get(u.SOURCE_SYSTEM),
                record.get(u.SOURCE_KEY),
                record.get(u.SOURCE_VERSION),
                record.get(u.AGGREGATE_VERSION),
                record.get(u.CREATED_AT),
                record.get(u.UPDATED_AT));
    }

    private com.serviceos.organization.domain.OrgMembership mapMembership(Record record) {
        OrgMembership m = ORG_MEMBERSHIP;
        return new com.serviceos.organization.domain.OrgMembership(
                record.get(m.MEMBERSHIP_ID),
                record.get(m.TENANT_ID),
                record.get(m.ORGANIZATION_ID),
                record.get(m.ORG_UNIT_ID),
                record.get(m.PRINCIPAL_ID),
                com.serviceos.organization.domain.OrgMembership.MembershipType.valueOf(record.get(m.MEMBERSHIP_TYPE)),
                com.serviceos.organization.domain.OrgMembership.Status.valueOf(record.get(m.MEMBERSHIP_STATUS)),
                record.get(m.VALID_FROM),
                record.get(m.VALID_TO),
                record.get(m.SOURCE_SYSTEM),
                record.get(m.SOURCE_KEY),
                record.get(m.SOURCE_VERSION),
                record.get(m.AGGREGATE_VERSION),
                record.get(m.CREATED_BY),
                record.get(m.CREATED_AT),
                record.get(m.TERMINATED_BY),
                record.get(m.TERMINATED_AT),
                record.get(m.TERMINATE_REASON));
    }
}
