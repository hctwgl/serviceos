package com.serviceos.organization.application;

import com.serviceos.organization.api.DirectorySyncBatchView;
import com.serviceos.organization.api.ReassignmentWorkItemView;
import com.serviceos.organization.domain.OrgMembership;
import com.serviceos.organization.domain.OrgUnit;
import com.serviceos.organization.domain.Organization;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 组织目录命令/查询持久化端口。closure 重建、同步批次 advisory lock 与 FOR UPDATE
 * 语义必须使用 Spring JDBC 精确控制，不能交给通用 ORM。
 */
public interface OrganizationDirectoryRepository {
    Optional<Organization> findOrganization(String tenantId, UUID organizationId);

    Optional<Organization> findOrganizationForUpdate(String tenantId, UUID organizationId);

    List<Organization> listOrganizations(String tenantId);

    void insertOrganization(Organization organization);

    boolean advanceOrganizationVersion(String tenantId, UUID organizationId, long expectedVersion, Instant now);

    Optional<OrgUnit> findUnit(String tenantId, UUID unitId);

    Optional<OrgUnit> findUnitForUpdate(String tenantId, UUID unitId);

    Optional<OrgUnit> findUnitBySource(String tenantId, String sourceSystem, String sourceKey);

    List<OrgUnit> listUnits(String tenantId, UUID organizationId);

    void insertUnit(OrgUnit unit);

    void insertUnitClosure(String tenantId, UUID organizationId, UUID unitId, UUID parentUnitId);

    boolean updateUnitParent(String tenantId, UUID unitId, long expectedVersion, UUID newParentUnitId, Instant now);

    boolean isDescendant(String tenantId, UUID ancestorId, UUID descendantId);

    void rebuildClosureForMove(String tenantId, UUID organizationId, UUID unitId, UUID newParentUnitId);

    Optional<OrgMembership> findMembership(String tenantId, UUID membershipId);

    Optional<OrgMembership> findMembershipForUpdate(String tenantId, UUID membershipId);

    Optional<OrgMembership> findMembershipBySource(String tenantId, String sourceSystem, String sourceKey);

    List<OrgMembership> listMemberships(String tenantId, UUID organizationId, UUID unitId, UUID principalId);

    void insertMembership(OrgMembership membership);

    boolean terminateMembership(
            String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt);

    boolean transferMembership(
            String tenantId, UUID membershipId, long expectedVersion,
            UUID targetUnitId, String membershipType, Instant validFrom, String actorId, Instant now);

    void lockSyncBatchKey(String tenantId, String sourceSystem, String externalBatchKey);

    Optional<UUID> findSyncBatchId(String tenantId, String sourceSystem, String externalBatchKey);

    UUID insertSyncBatch(
            UUID batchId, String tenantId, UUID organizationId, String sourceSystem,
            String externalBatchKey, String actorId, String correlationId, String requestDigest,
            Instant receivedAt);

    void insertSyncItem(
            UUID itemId, UUID batchId, String tenantId, int itemIndex, String operationType,
            String sourceKey, long externalVersion, String itemStatus, String resultCode,
            String resultMessage, String resourceType, UUID resourceId, Instant processedAt);

    void completeSyncBatch(
            UUID batchId, String batchStatus, int successCount, int failedCount,
            int skippedCount, Instant completedAt);

    Optional<DirectorySyncBatchView> findSyncBatch(String tenantId, UUID batchId);

    Long findUnitSourceVersion(String tenantId, String sourceSystem, String sourceKey);

    Long findMembershipSourceVersion(String tenantId, String sourceSystem, String sourceKey);

    void upsertUnitFromSync(
            UUID unitId, String tenantId, UUID organizationId, UUID parentUnitId,
            String unitCode, String unitName, String sourceSystem, String sourceKey,
            long sourceVersion, Instant now);

    void upsertMembershipFromSync(
            UUID membershipId, String tenantId, UUID organizationId, UUID orgUnitId,
            UUID principalId, String membershipType, Instant validFrom,
            String sourceSystem, String sourceKey, long sourceVersion,
            String actorId, Instant now);

    void insertReassignmentWorkItem(
            UUID workItemId, String tenantId, UUID organizationId, UUID membershipId,
            UUID principalId, String reason, String actorId, String correlationId, Instant now);

    List<ReassignmentWorkItemView> listOpenReassignmentWorkItems(String tenantId);

    void insertStructureEvent(
            UUID eventId, String tenantId, UUID organizationId, String eventType,
            String resourceType, UUID resourceId, long resourceVersion, String reason,
            String actorId, String requestDigest, String correlationId, Instant occurredAt);
}
