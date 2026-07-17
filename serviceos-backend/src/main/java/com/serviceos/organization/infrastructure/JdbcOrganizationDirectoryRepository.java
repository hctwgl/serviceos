package com.serviceos.organization.infrastructure;

import com.serviceos.organization.api.DirectorySyncBatchView;
import com.serviceos.organization.api.DirectorySyncItemView;
import com.serviceos.organization.api.ReassignmentWorkItemView;
import com.serviceos.organization.application.OrganizationDirectoryRepository;
import com.serviceos.organization.domain.OrgMembership;
import com.serviceos.organization.domain.OrgUnit;
import com.serviceos.organization.domain.Organization;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 组织目录 JDBC 适配器：closure 移动、同步批次 advisory lock 与任职唯一约束依赖
 * PostgreSQL 精确语义，必须由真实 PostgreSQL IT 证明。
 */
@Repository
final class JdbcOrganizationDirectoryRepository implements OrganizationDirectoryRepository {
    private static final String ORG_SELECT = """
            SELECT organization_id, tenant_id, organization_code, organization_name,
                   authority_mode, organization_status, source_system, source_key,
                   aggregate_version, created_at, updated_at
              FROM org_organization
            """;
    private static final String UNIT_SELECT = """
            SELECT org_unit_id, tenant_id, organization_id, parent_unit_id,
                   unit_code, unit_name, unit_status, source_system, source_key,
                   source_version, aggregate_version, created_at, updated_at
              FROM org_unit
            """;
    private static final String MEMBERSHIP_SELECT = """
            SELECT membership_id, tenant_id, organization_id, org_unit_id, principal_id,
                   membership_type, membership_status, valid_from, valid_to,
                   source_system, source_key, source_version, aggregate_version,
                   created_by, created_at, terminated_by, terminated_at, terminate_reason
              FROM org_membership
            """;

    private final JdbcClient jdbc;

    JdbcOrganizationDirectoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
        return jdbc.sql(ORG_SELECT + " WHERE tenant_id=:tenant AND organization_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", organizationId)
                .query(this::mapOrganization).optional();
    }

    @Override
    public List<Organization> listOrganizations(String tenantId) {
        return jdbc.sql(ORG_SELECT + " WHERE tenant_id=:tenant ORDER BY organization_code")
                .param("tenant", tenantId).query(this::mapOrganization).list();
    }

    @Override
    public void insertOrganization(Organization organization) {
        jdbc.sql("""
                INSERT INTO org_organization (
                    organization_id, tenant_id, organization_code, organization_name,
                    authority_mode, organization_status, source_system, source_key,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :code, :name, :authority, :status, :sourceSystem, :sourceKey,
                    :version, :createdAt, :updatedAt
                )
                """)
                .param("id", organization.id()).param("tenant", organization.tenantId())
                .param("code", organization.code()).param("name", organization.name())
                .param("authority", organization.authorityMode().name())
                .param("status", organization.status().name())
                .param("sourceSystem", organization.sourceSystem())
                .param("sourceKey", organization.sourceKey())
                .param("version", organization.version())
                .param("createdAt", dbTime(organization.createdAt()))
                .param("updatedAt", dbTime(organization.updatedAt()))
                .update();
    }

    @Override
    public boolean advanceOrganizationVersion(String tenantId, UUID organizationId, long expectedVersion, Instant now) {
        return jdbc.sql("""
                UPDATE org_organization
                   SET aggregate_version=aggregate_version+1, updated_at=:now
                 WHERE tenant_id=:tenant AND organization_id=:id
                   AND aggregate_version=:expected
                """)
                .param("now", dbTime(now)).param("tenant", tenantId).param("id", organizationId)
                .param("expected", expectedVersion).update() == 1;
    }

    @Override
    public Optional<OrgUnit> findUnit(String tenantId, UUID unitId) {
        return unitQuery(tenantId, unitId, false);
    }

    @Override
    public Optional<OrgUnit> findUnitForUpdate(String tenantId, UUID unitId) {
        return unitQuery(tenantId, unitId, true);
    }

    private Optional<OrgUnit> unitQuery(String tenantId, UUID unitId, boolean forUpdate) {
        return jdbc.sql(UNIT_SELECT + " WHERE tenant_id=:tenant AND org_unit_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", unitId)
                .query(this::mapUnit).optional();
    }

    @Override
    public Optional<OrgUnit> findUnitBySource(String tenantId, String sourceSystem, String sourceKey) {
        return jdbc.sql(UNIT_SELECT + """
                 WHERE tenant_id=:tenant AND source_system=:sourceSystem AND source_key=:sourceKey
                """)
                .param("tenant", tenantId).param("sourceSystem", sourceSystem).param("sourceKey", sourceKey)
                .query(this::mapUnit).optional();
    }

    @Override
    public List<OrgUnit> listUnits(String tenantId, UUID organizationId) {
        return jdbc.sql(UNIT_SELECT + """
                 WHERE tenant_id=:tenant AND organization_id=:orgId
                 ORDER BY unit_code
                """)
                .param("tenant", tenantId).param("orgId", organizationId)
                .query(this::mapUnit).list();
    }

    @Override
    public void insertUnit(OrgUnit unit) {
        jdbc.sql("""
                INSERT INTO org_unit (
                    org_unit_id, tenant_id, organization_id, parent_unit_id,
                    unit_code, unit_name, unit_status, source_system, source_key,
                    source_version, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :orgId, :parentId, :code, :name, :status,
                    :sourceSystem, :sourceKey, :sourceVersion, :version, :createdAt, :updatedAt
                )
                """)
                .param("id", unit.id()).param("tenant", unit.tenantId()).param("orgId", unit.organizationId())
                .param("parentId", unit.parentUnitId()).param("code", unit.unitCode()).param("name", unit.unitName())
                .param("status", unit.status().name()).param("sourceSystem", unit.sourceSystem())
                .param("sourceKey", unit.sourceKey()).param("sourceVersion", unit.sourceVersion())
                .param("version", unit.version()).param("createdAt", dbTime(unit.createdAt()))
                .param("updatedAt", dbTime(unit.updatedAt())).update();
    }

    @Override
    public void insertUnitClosure(String tenantId, UUID organizationId, UUID unitId, UUID parentUnitId) {
        jdbc.sql("""
                INSERT INTO org_unit_closure (tenant_id, organization_id, ancestor_id, descendant_id, depth)
                VALUES (:tenant, :orgId, :unitId, :unitId, 0)
                """)
                .param("tenant", tenantId).param("orgId", organizationId).param("unitId", unitId).update();
        if (parentUnitId != null) {
            jdbc.sql("""
                    INSERT INTO org_unit_closure (tenant_id, organization_id, ancestor_id, descendant_id, depth)
                    SELECT :tenant, :orgId, ancestor_id, :unitId, depth + 1
                      FROM org_unit_closure
                     WHERE tenant_id=:tenant AND descendant_id=:parentId
                    """)
                    .param("tenant", tenantId).param("orgId", organizationId)
                    .param("unitId", unitId).param("parentId", parentUnitId).update();
        }
    }

    @Override
    public boolean updateUnitParent(
            String tenantId, UUID unitId, long expectedVersion, UUID newParentUnitId, Instant now
    ) {
        return jdbc.sql("""
                UPDATE org_unit
                   SET parent_unit_id=:parentId, aggregate_version=aggregate_version+1, updated_at=:now
                 WHERE tenant_id=:tenant AND org_unit_id=:id AND aggregate_version=:expected
                """)
                .param("parentId", newParentUnitId).param("now", dbTime(now))
                .param("tenant", tenantId).param("id", unitId).param("expected", expectedVersion)
                .update() == 1;
    }

    @Override
    public boolean isDescendant(String tenantId, UUID ancestorId, UUID descendantId) {
        if (ancestorId.equals(descendantId)) return true;
        return jdbc.sql("""
                SELECT 1 FROM org_unit_closure
                 WHERE tenant_id=:tenant AND ancestor_id=:ancestor AND descendant_id=:descendant
                   AND depth > 0
                """)
                .param("tenant", tenantId).param("ancestor", ancestorId).param("descendant", descendantId)
                .query(Integer.class).optional().isPresent();
    }

    @Override
    public void rebuildClosureForMove(
            String tenantId, UUID organizationId, UUID unitId, UUID newParentUnitId
    ) {
        // 先断开子树与旧祖先之间的闭包边，保留子树内部闭包。
        jdbc.sql("""
                DELETE FROM org_unit_closure c
                 USING org_unit_closure sub, org_unit_closure sup
                 WHERE c.tenant_id=:tenant
                   AND sub.ancestor_id=:unitId
                   AND sup.descendant_id=:unitId
                   AND c.descendant_id=sub.descendant_id
                   AND c.ancestor_id=sup.ancestor_id
                   AND sup.ancestor_id <> sub.descendant_id
                """)
                .param("tenant", tenantId).param("unitId", unitId).update();
        if (newParentUnitId != null) {
            jdbc.sql("""
                    INSERT INTO org_unit_closure (tenant_id, organization_id, ancestor_id, descendant_id, depth)
                    SELECT :tenant, :orgId, super.ancestor_id, sub.descendant_id, super.depth + sub.depth + 1
                      FROM org_unit_closure super
                      CROSS JOIN org_unit_closure sub
                     WHERE super.tenant_id=:tenant AND sub.tenant_id=:tenant
                       AND super.descendant_id=:newParent
                       AND sub.ancestor_id=:unitId
                    """)
                    .param("tenant", tenantId).param("orgId", organizationId)
                    .param("newParent", newParentUnitId).param("unitId", unitId).update();
        }
    }

    @Override
    public Optional<OrgMembership> findMembership(String tenantId, UUID membershipId) {
        return membershipQuery(tenantId, membershipId, false);
    }

    @Override
    public Optional<OrgMembership> findMembershipForUpdate(String tenantId, UUID membershipId) {
        return membershipQuery(tenantId, membershipId, true);
    }

    private Optional<OrgMembership> membershipQuery(String tenantId, UUID membershipId, boolean forUpdate) {
        return jdbc.sql(MEMBERSHIP_SELECT + " WHERE tenant_id=:tenant AND membership_id=:id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId).param("id", membershipId)
                .query(this::mapMembership).optional();
    }

    @Override
    public Optional<OrgMembership> findMembershipBySource(String tenantId, String sourceSystem, String sourceKey) {
        return jdbc.sql(MEMBERSHIP_SELECT + """
                 WHERE tenant_id=:tenant AND source_system=:sourceSystem AND source_key=:sourceKey
                """)
                .param("tenant", tenantId).param("sourceSystem", sourceSystem).param("sourceKey", sourceKey)
                .query(this::mapMembership).optional();
    }

    @Override
    public List<OrgMembership> listMemberships(
            String tenantId, UUID organizationId, UUID unitId, UUID principalId
    ) {
        StringBuilder sql = new StringBuilder(MEMBERSHIP_SELECT + " WHERE tenant_id=:tenant");
        if (organizationId != null) sql.append(" AND organization_id=:orgId");
        if (unitId != null) sql.append(" AND org_unit_id=:unitId");
        if (principalId != null) sql.append(" AND principal_id=:principalId");
        sql.append(" ORDER BY valid_from DESC, membership_id");
        var spec = jdbc.sql(sql.toString()).param("tenant", tenantId);
        if (organizationId != null) spec = spec.param("orgId", organizationId);
        if (unitId != null) spec = spec.param("unitId", unitId);
        if (principalId != null) spec = spec.param("principalId", principalId);
        return spec.query(this::mapMembership).list();
    }

    @Override
    public void insertMembership(OrgMembership membership) {
        try {
            jdbc.sql("""
                    INSERT INTO org_membership (
                        membership_id, tenant_id, organization_id, org_unit_id, principal_id,
                        membership_type, membership_status, valid_from, valid_to,
                        source_system, source_key, source_version, aggregate_version,
                        created_by, created_at
                    ) VALUES (
                        :id, :tenant, :orgId, :unitId, :principalId, :type, :status,
                        :validFrom, NULL, :sourceSystem, :sourceKey, :sourceVersion,
                        :version, :createdBy, :createdAt
                    )
                    """)
                    .param("id", membership.id()).param("tenant", membership.tenantId())
                    .param("orgId", membership.organizationId()).param("unitId", membership.orgUnitId())
                    .param("principalId", membership.principalId())
                    .param("type", membership.membershipType().name())
                    .param("status", membership.status().name())
                    .param("validFrom", dbTime(membership.validFrom()))
                    .param("sourceSystem", membership.sourceSystem())
                    .param("sourceKey", membership.sourceKey())
                    .param("sourceVersion", membership.sourceVersion())
                    .param("version", membership.version())
                    .param("createdBy", membership.createdBy())
                    .param("createdAt", dbTime(membership.createdAt())).update();
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.ORGANIZATION_MEMBERSHIP_CONFLICT, "同一主体已存在有效主职");
        }
    }

    @Override
    public boolean terminateMembership(
            String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt
    ) {
        return jdbc.sql("""
                UPDATE org_membership
                   SET membership_status='TERMINATED',
                       valid_to=:terminatedAt,
                       terminated_by=:actor,
                       terminated_at=:terminatedAt,
                       terminate_reason=:reason,
                       aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND membership_id=:id
                   AND aggregate_version=:expected AND membership_status='ACTIVE'
                """)
                .param("terminatedAt", dbTime(terminatedAt)).param("actor", actorId).param("reason", reason)
                .param("tenant", tenantId).param("id", membershipId).param("expected", expectedVersion)
                .update() == 1;
    }

    @Override
    public boolean transferMembership(
            String tenantId, UUID membershipId, long expectedVersion,
            UUID targetUnitId, String membershipType, Instant validFrom, String actorId, Instant now
    ) {
        try {
            var spec = jdbc.sql("""
                    UPDATE org_membership
                       SET org_unit_id=:targetUnitId,
                           membership_type=COALESCE(:membershipType, membership_type),
                           valid_from=COALESCE(:validFrom, valid_from),
                           aggregate_version=aggregate_version+1
                     WHERE tenant_id=:tenant AND membership_id=:id
                       AND aggregate_version=:expected AND membership_status='ACTIVE'
                    """)
                    .param("targetUnitId", targetUnitId).param("membershipType", membershipType)
                    .param("validFrom", validFrom == null ? null : dbTime(validFrom))
                    .param("tenant", tenantId).param("id", membershipId).param("expected", expectedVersion);
            return spec.update() == 1;
        } catch (DuplicateKeyException exception) {
            throw new BusinessProblem(ProblemCode.ORGANIZATION_MEMBERSHIP_CONFLICT, "同一主体已存在有效主职");
        }
    }

    @Override
    public void lockSyncBatchKey(String tenantId, String sourceSystem, String externalBatchKey) {
        jdbc.sql("""
                SELECT 1
                  FROM (SELECT pg_advisory_xact_lock(hashtextextended(:batchKey, 0))) locked
                """)
                .param("batchKey", tenantId + "\u001f" + sourceSystem + "\u001f" + externalBatchKey)
                .query(Integer.class).single();
    }

    @Override
    public Optional<UUID> findSyncBatchId(String tenantId, String sourceSystem, String externalBatchKey) {
        return jdbc.sql("""
                SELECT batch_id FROM org_directory_sync_batch
                 WHERE tenant_id=:tenant AND source_system=:sourceSystem
                   AND external_batch_key=:batchKey
                """)
                .param("tenant", tenantId).param("sourceSystem", sourceSystem).param("batchKey", externalBatchKey)
                .query(UUID.class).optional();
    }

    @Override
    public UUID insertSyncBatch(
            UUID batchId, String tenantId, UUID organizationId, String sourceSystem,
            String externalBatchKey, String actorId, String correlationId, String requestDigest,
            Instant receivedAt
    ) {
        jdbc.sql("""
                INSERT INTO org_directory_sync_batch (
                    batch_id, tenant_id, organization_id, source_system, external_batch_key,
                    batch_status, received_at, actor_id, correlation_id, request_digest
                ) VALUES (
                    :batchId, :tenant, :orgId, :sourceSystem, :batchKey,
                    'RECEIVED', :receivedAt, :actor, :correlationId, :digest
                )
                """)
                .param("batchId", batchId).param("tenant", tenantId).param("orgId", organizationId)
                .param("sourceSystem", sourceSystem).param("batchKey", externalBatchKey)
                .param("receivedAt", dbTime(receivedAt)).param("actor", actorId)
                .param("correlationId", correlationId).param("digest", requestDigest).update();
        return batchId;
    }

    @Override
    public void insertSyncItem(
            UUID itemId, UUID batchId, String tenantId, int itemIndex, String operationType,
            String sourceKey, long externalVersion, String itemStatus, String resultCode,
            String resultMessage, String resourceType, UUID resourceId, Instant processedAt
    ) {
        jdbc.sql("""
                INSERT INTO org_directory_sync_item (
                    item_id, batch_id, tenant_id, item_index, operation_type, source_key,
                    external_version, item_status, result_code, result_message,
                    resource_type, resource_id, processed_at
                ) VALUES (
                    :itemId, :batchId, :tenant, :itemIndex, :operationType, :sourceKey,
                    :externalVersion, :itemStatus, :resultCode, :resultMessage,
                    :resourceType, :resourceId, :processedAt
                )
                """)
                .param("itemId", itemId).param("batchId", batchId).param("tenant", tenantId)
                .param("itemIndex", itemIndex).param("operationType", operationType)
                .param("sourceKey", sourceKey).param("externalVersion", externalVersion)
                .param("itemStatus", itemStatus).param("resultCode", resultCode)
                .param("resultMessage", resultMessage).param("resourceType", resourceType)
                .param("resourceId", resourceId).param("processedAt", dbTime(processedAt)).update();
    }

    @Override
    public void completeSyncBatch(
            UUID batchId, String batchStatus, int successCount, int failedCount,
            int skippedCount, Instant completedAt
    ) {
        jdbc.sql("""
                UPDATE org_directory_sync_batch
                   SET batch_status=:status, completed_at=:completedAt,
                       success_count=:success, failed_count=:failed, skipped_count=:skipped
                 WHERE batch_id=:batchId
                """)
                .param("status", batchStatus).param("completedAt", dbTime(completedAt))
                .param("success", successCount).param("failed", failedCount).param("skipped", skippedCount)
                .param("batchId", batchId).update();
    }

    @Override
    public Optional<DirectorySyncBatchView> findSyncBatch(String tenantId, UUID batchId) {
        return jdbc.sql("""
                SELECT batch_id, organization_id, source_system, external_batch_key,
                       batch_status, received_at, completed_at,
                       success_count, failed_count, skipped_count
                  FROM org_directory_sync_batch
                 WHERE tenant_id=:tenant AND batch_id=:batchId
                """)
                .param("tenant", tenantId).param("batchId", batchId)
                .query((rs, rowNum) -> new DirectorySyncBatchView(
                        rs.getObject("batch_id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("source_system"),
                        rs.getString("external_batch_key"),
                        rs.getString("batch_status"),
                        toInstant(rs.getTimestamp("received_at")),
                        toInstant(rs.getTimestamp("completed_at")),
                        rs.getInt("success_count"),
                        rs.getInt("failed_count"),
                        rs.getInt("skipped_count"),
                        listSyncItems(batchId)))
                .optional();
    }

    private List<DirectorySyncItemView> listSyncItems(UUID batchId) {
        return jdbc.sql("""
                SELECT item_id, item_index, operation_type, source_key, external_version,
                       item_status, result_code, result_message, resource_type, resource_id, processed_at
                  FROM org_directory_sync_item
                 WHERE batch_id=:batchId
                 ORDER BY item_index
                """)
                .param("batchId", batchId)
                .query((rs, rowNum) -> new DirectorySyncItemView(
                        rs.getObject("item_id", UUID.class),
                        rs.getInt("item_index"),
                        rs.getString("operation_type"),
                        rs.getString("source_key"),
                        rs.getLong("external_version"),
                        rs.getString("item_status"),
                        rs.getString("result_code"),
                        rs.getString("result_message"),
                        rs.getString("resource_type"),
                        rs.getObject("resource_id", UUID.class),
                        toInstant(rs.getTimestamp("processed_at"))))
                .list();
    }

    @Override
    public Long findUnitSourceVersion(String tenantId, String sourceSystem, String sourceKey) {
        return jdbc.sql("""
                SELECT source_version FROM org_unit
                 WHERE tenant_id=:tenant AND source_system=:sourceSystem AND source_key=:sourceKey
                """)
                .param("tenant", tenantId).param("sourceSystem", sourceSystem).param("sourceKey", sourceKey)
                .query(Long.class).optional().orElse(null);
    }

    @Override
    public Long findMembershipSourceVersion(String tenantId, String sourceSystem, String sourceKey) {
        return jdbc.sql("""
                SELECT source_version FROM org_membership
                 WHERE tenant_id=:tenant AND source_system=:sourceSystem AND source_key=:sourceKey
                """)
                .param("tenant", tenantId).param("sourceSystem", sourceSystem).param("sourceKey", sourceKey)
                .query(Long.class).optional().orElse(null);
    }

    @Override
    public void upsertUnitFromSync(
            UUID unitId, String tenantId, UUID organizationId, UUID parentUnitId,
            String unitCode, String unitName, String sourceSystem, String sourceKey,
            long sourceVersion, Instant now
    ) {
        Optional<OrgUnit> existing = findUnitBySource(tenantId, sourceSystem, sourceKey);
        if (existing.isPresent()) {
            jdbc.sql("""
                    UPDATE org_unit
                       SET unit_code=:code, unit_name=:name, parent_unit_id=:parentId,
                           source_version=:sourceVersion, updated_at=:now,
                           aggregate_version=aggregate_version+1
                     WHERE tenant_id=:tenant AND org_unit_id=:id
                    """)
                    .param("code", unitCode).param("name", unitName).param("parentId", parentUnitId)
                    .param("sourceVersion", sourceVersion).param("now", dbTime(now))
                    .param("tenant", tenantId).param("id", existing.get().id()).update();
        } else {
            OrgUnit unit = new OrgUnit(unitId, tenantId, organizationId, parentUnitId, unitCode, unitName,
                    OrgUnit.Status.ACTIVE, sourceSystem, sourceKey, sourceVersion, 1, now, now);
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
        Optional<OrgMembership> existing = findMembershipBySource(tenantId, sourceSystem, sourceKey);
        if (existing.isPresent()) {
            try {
                jdbc.sql("""
                        UPDATE org_membership
                           SET org_unit_id=:unitId, membership_type=:type, valid_from=:validFrom,
                               source_version=:sourceVersion, aggregate_version=aggregate_version+1
                         WHERE tenant_id=:tenant AND membership_id=:id AND membership_status='ACTIVE'
                        """)
                        .param("unitId", orgUnitId).param("type", membershipType)
                        .param("validFrom", dbTime(validFrom)).param("sourceVersion", sourceVersion)
                        .param("tenant", tenantId).param("id", existing.get().id()).update();
            } catch (DuplicateKeyException exception) {
                throw new BusinessProblem(ProblemCode.ORGANIZATION_MEMBERSHIP_CONFLICT, "同一主体已存在有效主职");
            }
        } else {
            OrgMembership membership = new OrgMembership(membershipId, tenantId, organizationId, orgUnitId,
                    principalId, OrgMembership.MembershipType.valueOf(membershipType),
                    OrgMembership.Status.ACTIVE, validFrom, null, sourceSystem, sourceKey, sourceVersion,
                    1, actorId, now, null, null, null);
            insertMembership(membership);
        }
    }

    @Override
    public void insertReassignmentWorkItem(
            UUID workItemId, String tenantId, UUID organizationId, UUID membershipId,
            UUID principalId, String reason, String actorId, String correlationId, Instant now
    ) {
        jdbc.sql("""
                INSERT INTO org_reassignment_work_item (
                    work_item_id, tenant_id, organization_id, membership_id, principal_id,
                    work_item_status, reason, created_by, created_at, correlation_id
                ) VALUES (
                    :id, :tenant, :orgId, :membershipId, :principalId,
                    'OPEN', :reason, :actor, :now, :correlationId
                )
                """)
                .param("id", workItemId).param("tenant", tenantId).param("orgId", organizationId)
                .param("membershipId", membershipId).param("principalId", principalId)
                .param("reason", reason).param("actor", actorId).param("now", dbTime(now))
                .param("correlationId", correlationId).update();
    }

    @Override
    public List<ReassignmentWorkItemView> listOpenReassignmentWorkItems(String tenantId) {
        return jdbc.sql("""
                SELECT work_item_id, organization_id, membership_id, principal_id,
                       work_item_status, reason, created_by, created_at, correlation_id
                  FROM org_reassignment_work_item
                 WHERE tenant_id=:tenant AND work_item_status='OPEN'
                 ORDER BY created_at, work_item_id
                """)
                .param("tenant", tenantId)
                .query((rs, rowNum) -> new ReassignmentWorkItemView(
                        rs.getObject("work_item_id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("membership_id", UUID.class),
                        rs.getObject("principal_id", UUID.class),
                        rs.getString("work_item_status"),
                        rs.getString("reason"),
                        rs.getString("created_by"),
                        toInstant(rs.getTimestamp("created_at")),
                        rs.getString("correlation_id")))
                .list();
    }

    @Override
    public void insertStructureEvent(
            UUID eventId, String tenantId, UUID organizationId, String eventType,
            String resourceType, UUID resourceId, long resourceVersion, String reason,
            String actorId, String requestDigest, String correlationId, Instant occurredAt
    ) {
        jdbc.sql("""
                INSERT INTO org_structure_event (
                    structure_event_id, tenant_id, organization_id, event_type,
                    resource_type, resource_id, resource_version, reason,
                    actor_id, request_digest, correlation_id, occurred_at
                ) VALUES (
                    :eventId, :tenant, :orgId, :eventType, :resourceType, :resourceId,
                    :resourceVersion, :reason, :actor, :digest, :correlationId, :occurredAt
                )
                """)
                .param("eventId", eventId).param("tenant", tenantId).param("orgId", organizationId)
                .param("eventType", eventType).param("resourceType", resourceType).param("resourceId", resourceId)
                .param("resourceVersion", resourceVersion).param("reason", reason).param("actor", actorId)
                .param("digest", requestDigest).param("correlationId", correlationId)
                .param("occurredAt", dbTime(occurredAt)).update();
    }

    private Organization mapOrganization(ResultSet rs, int rowNum) throws SQLException {
        return new Organization(
                rs.getObject("organization_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getString("organization_code"),
                rs.getString("organization_name"),
                Organization.AuthorityMode.valueOf(rs.getString("authority_mode")),
                Organization.Status.valueOf(rs.getString("organization_status")),
                rs.getString("source_system"),
                rs.getString("source_key"),
                rs.getLong("aggregate_version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private OrgUnit mapUnit(ResultSet rs, int rowNum) throws SQLException {
        return new OrgUnit(
                rs.getObject("org_unit_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("parent_unit_id", UUID.class),
                rs.getString("unit_code"),
                rs.getString("unit_name"),
                OrgUnit.Status.valueOf(rs.getString("unit_status")),
                rs.getString("source_system"),
                rs.getString("source_key"),
                rs.getObject("source_version") == null ? null : rs.getLong("source_version"),
                rs.getLong("aggregate_version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")));
    }

    private OrgMembership mapMembership(ResultSet rs, int rowNum) throws SQLException {
        return new OrgMembership(
                rs.getObject("membership_id", UUID.class),
                rs.getString("tenant_id"),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("org_unit_id", UUID.class),
                rs.getObject("principal_id", UUID.class),
                OrgMembership.MembershipType.valueOf(rs.getString("membership_type")),
                OrgMembership.Status.valueOf(rs.getString("membership_status")),
                toInstant(rs.getTimestamp("valid_from")),
                toInstant(rs.getTimestamp("valid_to")),
                rs.getString("source_system"),
                rs.getString("source_key"),
                rs.getObject("source_version") == null ? null : rs.getLong("source_version"),
                rs.getLong("aggregate_version"),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("terminated_by"),
                toInstant(rs.getTimestamp("terminated_at")),
                rs.getString("terminate_reason"));
    }

    private static Timestamp dbTime(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) return null;
        return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC).toInstant();
    }
}
