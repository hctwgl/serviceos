package com.serviceos.organization.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

public interface OrganizationQueryService {
    OrganizationPage listOrganizations(CurrentPrincipal actor, String correlationId);

    OrganizationDetail getOrganization(CurrentPrincipal actor, String correlationId, UUID organizationId);

    List<OrgUnitView> listUnits(
            CurrentPrincipal actor, String correlationId, UUID organizationId, boolean asTree);

    OrgMembershipPage listMemberships(
            CurrentPrincipal actor, String correlationId, UUID organizationId,
            UUID unitId, UUID principalId);

    DirectorySyncBatchView getSyncBatch(CurrentPrincipal actor, String correlationId, UUID batchId);

    ReassignmentWorkItemPage listOpenReassignmentWorkItems(CurrentPrincipal actor, String correlationId);
}
