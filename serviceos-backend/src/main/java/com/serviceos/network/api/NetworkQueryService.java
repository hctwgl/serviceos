package com.serviceos.network.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.time.Instant;
import java.util.UUID;

public interface NetworkQueryService {
    PartnerOrganizationPage listPartnerOrganizations(CurrentPrincipal actor, String correlationId);

    PartnerOrganizationView getPartnerOrganization(
            CurrentPrincipal actor, String correlationId, UUID partnerOrganizationId);

    ServiceNetworkPage listServiceNetworks(
            CurrentPrincipal actor, String correlationId, UUID partnerOrganizationId);

    ServiceNetworkView getServiceNetwork(CurrentPrincipal actor, String correlationId, UUID networkId);

    NetworkMembershipPage listNetworkMemberships(
            CurrentPrincipal actor, String correlationId, UUID networkId, UUID principalId);

    TechnicianProfilePage listTechnicianProfiles(CurrentPrincipal actor, String correlationId);

    TechnicianProfileView getTechnicianProfile(
            CurrentPrincipal actor, String correlationId, UUID profileId);

    NetworkTechnicianMembershipPage listNetworkTechnicianMemberships(
            CurrentPrincipal actor, String correlationId, UUID networkId, UUID technicianProfileId);

    TechnicianQualificationPage listTechnicianQualifications(
            CurrentPrincipal actor, String correlationId, UUID technicianProfileId);

    EligibilityView checkEligibility(
            CurrentPrincipal actor, String correlationId,
            UUID technicianProfileId, UUID networkId, Instant at);

    DeactivationImpactView getNetworkDeactivationImpact(
            CurrentPrincipal actor, String correlationId, UUID networkId);

    DeactivationImpactView getTechnicianDeactivationImpact(
            CurrentPrincipal actor, String correlationId, UUID technicianProfileId);

    ClearanceWorkItemPage listOpenClearanceWorkItems(CurrentPrincipal actor, String correlationId);
}
