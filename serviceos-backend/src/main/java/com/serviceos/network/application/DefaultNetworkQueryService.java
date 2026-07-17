package com.serviceos.network.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.ClearanceWorkItemPage;
import com.serviceos.network.api.DeactivationImpactView;
import com.serviceos.network.api.EligibilityView;
import com.serviceos.network.api.NetworkAssignedWorkImpactPort;
import com.serviceos.network.api.NetworkAuthorizationPort;
import com.serviceos.network.api.NetworkMembershipPage;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.NetworkTechnicianMembershipPage;
import com.serviceos.network.api.NetworkWorkImpact;
import com.serviceos.network.api.PartnerOrganizationPage;
import com.serviceos.network.api.PartnerOrganizationView;
import com.serviceos.network.api.ServiceNetworkPage;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.network.api.TechnicianEligibilityQuery;
import com.serviceos.network.api.TechnicianProfilePage;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.network.api.TechnicianQualificationPage;
import com.serviceos.network.domain.PartnerOrganization;
import com.serviceos.network.domain.ServiceNetwork;
import com.serviceos.network.domain.TechnicianProfile;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** 网点目录只读查询；所有路径先做 tenant 能力校验再执行 tenant 约束 SQL。 */
@Service
final class DefaultNetworkQueryService implements NetworkQueryService {
    private final NetworkDirectoryRepository directory;
    private final NetworkAuthorizationPort authorization;
    private final TechnicianEligibilityQuery eligibility;
    private final NetworkAssignedWorkImpactPort workImpact;
    private final Clock clock;

    DefaultNetworkQueryService(
            NetworkDirectoryRepository directory,
            NetworkAuthorizationPort authorization,
            TechnicianEligibilityQuery eligibility,
            NetworkAssignedWorkImpactPort workImpact,
            Clock clock
    ) {
        this.directory = directory;
        this.authorization = authorization;
        this.eligibility = eligibility;
        this.workImpact = workImpact;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerOrganizationPage listPartnerOrganizations(CurrentPrincipal actor, String correlationId) {
        require(actor, correlationId, "network.read", "partners");
        return new PartnerOrganizationPage(
                directory.listPartners(actor.tenantId()).stream().map(PartnerOrganization::toView).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerOrganizationView getPartnerOrganization(
            CurrentPrincipal actor, String correlationId, UUID partnerOrganizationId
    ) {
        require(actor, correlationId, "network.read", partnerOrganizationId.toString());
        return requirePartner(actor.tenantId(), partnerOrganizationId).toView();
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceNetworkPage listServiceNetworks(
            CurrentPrincipal actor, String correlationId, UUID partnerOrganizationId
    ) {
        String resourceId = partnerOrganizationId == null ? "networks" : partnerOrganizationId.toString();
        require(actor, correlationId, "network.read", resourceId);
        if (partnerOrganizationId != null) {
            requirePartner(actor.tenantId(), partnerOrganizationId);
        }
        return new ServiceNetworkPage(
                directory.listNetworks(actor.tenantId(), partnerOrganizationId).stream()
                        .map(ServiceNetwork::toView).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ServiceNetworkView getServiceNetwork(
            CurrentPrincipal actor, String correlationId, UUID networkId
    ) {
        require(actor, correlationId, "network.read", networkId.toString());
        return requireNetworkAnyStatus(actor.tenantId(), networkId).toView();
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkMembershipPage listNetworkMemberships(
            CurrentPrincipal actor, String correlationId, UUID networkId, UUID principalId
    ) {
        String resourceId = networkId == null ? "memberships" : networkId.toString();
        require(actor, correlationId, "network.read", resourceId);
        if (networkId != null) {
            requireNetworkAnyStatus(actor.tenantId(), networkId);
        }
        return new NetworkMembershipPage(
                directory.listMemberships(actor.tenantId(), networkId, principalId).stream()
                        .map(membership -> membership.toView()).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianProfilePage listTechnicianProfiles(CurrentPrincipal actor, String correlationId) {
        require(actor, correlationId, "network.read", "technicians");
        return new TechnicianProfilePage(
                directory.listTechnicianProfiles(actor.tenantId()).stream()
                        .map(TechnicianProfile::toView).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianProfileView getTechnicianProfile(
            CurrentPrincipal actor, String correlationId, UUID profileId
    ) {
        require(actor, correlationId, "network.read", profileId.toString());
        return requireTechnician(actor.tenantId(), profileId).toView();
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkTechnicianMembershipPage listNetworkTechnicianMemberships(
            CurrentPrincipal actor, String correlationId, UUID networkId, UUID technicianProfileId
    ) {
        require(actor, correlationId, "network.read", "technician-memberships");
        if (networkId != null) {
            requireNetworkAnyStatus(actor.tenantId(), networkId);
        }
        if (technicianProfileId != null) {
            requireTechnician(actor.tenantId(), technicianProfileId);
        }
        return new NetworkTechnicianMembershipPage(
                directory.listTechnicianMemberships(actor.tenantId(), networkId, technicianProfileId).stream()
                        .map(membership -> membership.toView()).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public TechnicianQualificationPage listTechnicianQualifications(
            CurrentPrincipal actor, String correlationId, UUID technicianProfileId
    ) {
        require(actor, correlationId, "network.read", technicianProfileId.toString());
        requireTechnician(actor.tenantId(), technicianProfileId);
        return new TechnicianQualificationPage(
                directory.listQualifications(actor.tenantId(), technicianProfileId).stream()
                        .map(qualification -> qualification.toView()).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public EligibilityView checkEligibility(
            CurrentPrincipal actor, String correlationId,
            UUID technicianProfileId, UUID networkId, Instant at
    ) {
        require(actor, correlationId, "network.read", technicianProfileId.toString());
        TechnicianProfile profile = requireTechnician(actor.tenantId(), technicianProfileId);
        Instant evaluatedAt = at == null ? clock.instant() : at;
        boolean eligible = eligibility.canAcceptAssignment(
                actor.tenantId(), profile.principalId(), networkId, evaluatedAt);
        String reason = eligible ? null : eligibility.explainIneligibility(
                actor.tenantId(), profile.principalId(), networkId, evaluatedAt);
        return new EligibilityView(technicianProfileId, networkId, evaluatedAt, eligible, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public DeactivationImpactView getNetworkDeactivationImpact(
            CurrentPrincipal actor, String correlationId, UUID networkId
    ) {
        require(actor, correlationId, "network.read", networkId.toString());
        requireNetworkAnyStatus(actor.tenantId(), networkId);
        NetworkWorkImpact impact = workImpact.summarizeForNetwork(actor.tenantId(), networkId.toString());
        return new DeactivationImpactView("SERVICE_NETWORK", networkId, impact, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public DeactivationImpactView getTechnicianDeactivationImpact(
            CurrentPrincipal actor, String correlationId, UUID technicianProfileId
    ) {
        require(actor, correlationId, "network.read", technicianProfileId.toString());
        TechnicianProfile profile = requireTechnician(actor.tenantId(), technicianProfileId);
        NetworkWorkImpact impact = workImpact.summarizeForTechnician(
                actor.tenantId(), profile.principalId().toString());
        return new DeactivationImpactView("TECHNICIAN_PROFILE", technicianProfileId, impact, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ClearanceWorkItemPage listOpenClearanceWorkItems(CurrentPrincipal actor, String correlationId) {
        require(actor, correlationId, "network.read", "clearance-work-items");
        return new ClearanceWorkItemPage(
                directory.listOpenClearanceWorkItems(actor.tenantId()), clock.instant());
    }

    private void require(CurrentPrincipal actor, String correlationId, String capability, String resourceId) {
        authorization.requireTenantCapability(actor, capability, resourceId, correlationId);
    }

    private PartnerOrganization requirePartner(String tenantId, UUID partnerId) {
        return directory.findPartner(tenantId, partnerId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "合作组织不存在"));
    }

    private ServiceNetwork requireNetworkAnyStatus(String tenantId, UUID networkId) {
        return directory.findNetwork(tenantId, networkId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "网点不存在"));
    }

    private TechnicianProfile requireTechnician(String tenantId, UUID profileId) {
        return directory.findTechnicianProfile(tenantId, profileId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "师傅档案不存在"));
    }
}
