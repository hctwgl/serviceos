package com.serviceos.organization.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.organization.api.OrgMembershipPage;
import com.serviceos.organization.api.OrgMembershipView;
import com.serviceos.organization.api.OrgUnitView;
import com.serviceos.organization.api.OrganizationAuthorizationPort;
import com.serviceos.organization.api.OrganizationDetail;
import com.serviceos.organization.api.OrganizationPage;
import com.serviceos.organization.api.OrganizationQueryService;
import com.serviceos.organization.api.OrganizationView;
import com.serviceos.organization.api.DirectorySyncBatchView;
import com.serviceos.organization.api.ReassignmentWorkItemPage;
import com.serviceos.organization.domain.Organization;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** 组织目录只读查询；所有路径先做 tenant 能力校验再执行 tenant 约束 SQL。 */
@Service
final class DefaultOrganizationQueryService implements OrganizationQueryService {
    private final OrganizationDirectoryRepository directory;
    private final OrganizationAuthorizationPort authorization;
    private final Clock clock;

    DefaultOrganizationQueryService(
            OrganizationDirectoryRepository directory,
            OrganizationAuthorizationPort authorization,
            Clock clock
    ) {
        this.directory = directory;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationPage listOrganizations(CurrentPrincipal actor, String correlationId) {
        require(actor, correlationId, "organization.read", "directory");
        return new OrganizationPage(
                directory.listOrganizations(actor.tenantId()).stream().map(Organization::toView).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationDetail getOrganization(CurrentPrincipal actor, String correlationId, UUID organizationId) {
        require(actor, correlationId, "organization.read", organizationId.toString());
        Organization organization = requireOrganization(actor.tenantId(), organizationId);
        List<OrgUnitView> units = directory.listUnits(actor.tenantId(), organizationId).stream()
                .map(unit -> unit.toView()).toList();
        return new OrganizationDetail(organization.toView(), units, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrgUnitView> listUnits(
            CurrentPrincipal actor, String correlationId, UUID organizationId, boolean asTree
    ) {
        require(actor, correlationId, "organization.read", organizationId.toString());
        requireOrganization(actor.tenantId(), organizationId);
        return directory.listUnits(actor.tenantId(), organizationId).stream()
                .map(unit -> unit.toView()).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrgMembershipPage listMemberships(
            CurrentPrincipal actor, String correlationId, UUID organizationId,
            UUID unitId, UUID principalId
    ) {
        String resourceId = organizationId == null ? "memberships" : organizationId.toString();
        require(actor, correlationId, "organization.read", resourceId);
        if (organizationId != null) {
            requireOrganization(actor.tenantId(), organizationId);
        }
        List<OrgMembershipView> items = directory.listMemberships(actor.tenantId(), organizationId, unitId, principalId)
                .stream().map(membership -> membership.toView()).toList();
        return new OrgMembershipPage(items, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public DirectorySyncBatchView getSyncBatch(CurrentPrincipal actor, String correlationId, UUID batchId) {
        require(actor, correlationId, "organization.read", batchId.toString());
        return directory.findSyncBatch(actor.tenantId(), batchId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "同步批次不存在"));
    }

    @Override
    @Transactional(readOnly = true)
    public ReassignmentWorkItemPage listOpenReassignmentWorkItems(CurrentPrincipal actor, String correlationId) {
        require(actor, correlationId, "organization.read", "reassignment-work-items");
        return new ReassignmentWorkItemPage(
                directory.listOpenReassignmentWorkItems(actor.tenantId()), clock.instant());
    }

    private void require(CurrentPrincipal actor, String correlationId, String capability, String resourceId) {
        authorization.requireTenantCapability(actor, capability, resourceId, correlationId);
    }

    private Organization requireOrganization(String tenantId, UUID organizationId) {
        Organization organization = directory.findOrganization(tenantId, organizationId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "组织不存在"));
        organization.requireActive();
        return organization;
    }
}
