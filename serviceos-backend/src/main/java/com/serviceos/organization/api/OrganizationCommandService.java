package com.serviceos.organization.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrganizationCommandService {
    OrganizationView createOrganization(
            CurrentPrincipal actor, CommandMetadata metadata,
            String code, String name, String authorityMode,
            String sourceSystem, String sourceKey);

    OrgUnitView createUnit(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, long expectedOrgVersion,
            UUID parentUnitId, String unitCode, String unitName);

    OrgUnitView moveUnit(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, UUID unitId, long expectedUnitVersion,
            UUID newParentUnitId);

    OrgMembershipView createMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, UUID unitId, UUID principalId,
            String membershipType, Instant validFrom);

    OrgMembershipView transferMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion,
            UUID targetUnitId, String membershipType, Instant validFrom);

    OrgMembershipView terminateMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion, String reason,
            boolean disablePrincipal);

    DirectorySyncBatchView submitSyncBatch(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID organizationId, String sourceSystem, String externalBatchKey,
            List<SyncItemInput> items);

    record SyncItemInput(
            String operationType,
            String sourceKey,
            long externalVersion,
            String unitCode,
            String unitName,
            String parentSourceKey,
            UUID principalId,
            String membershipType,
            Instant validFrom
    ) {
    }
}
