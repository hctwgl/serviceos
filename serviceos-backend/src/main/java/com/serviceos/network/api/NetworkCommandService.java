package com.serviceos.network.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.time.Instant;
import java.util.UUID;

public interface NetworkCommandService {
    PartnerOrganizationView createPartnerOrganization(
            CurrentPrincipal actor, CommandMetadata metadata, String code, String name);

    ServiceNetworkView createServiceNetwork(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID partnerOrganizationId, String networkCode, String networkName);

    NetworkMembershipView inviteNetworkMember(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID networkId, Long expectedNetworkVersion, UUID principalId,
            String role, Instant validFrom);

    NetworkMembershipView terminateNetworkMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion, String reason);

    ServiceNetworkView deactivateServiceNetwork(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID networkId, long expectedVersion, String reason);

    TechnicianProfileView createTechnicianProfile(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID principalId, String displayName);

    TechnicianProfileView disableTechnicianProfile(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID profileId, long expectedVersion, String reason);

    TechnicianProfileView enableTechnicianProfile(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID profileId, long expectedVersion);

    NetworkTechnicianMembershipView createNetworkTechnicianMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID networkId, UUID technicianProfileId, Instant validFrom);

    NetworkTechnicianMembershipView terminateNetworkTechnicianMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion, String reason);

    TechnicianQualificationView submitQualification(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID technicianProfileId, String qualificationCode,
            Instant validFrom, Instant validTo);

    TechnicianQualificationView decideQualification(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID qualificationId, long expectedVersion, String decision, String reason);
}
