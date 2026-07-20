package com.serviceos.network.application;

import com.serviceos.network.api.ClearanceWorkItemView;
import com.serviceos.network.domain.NetworkMembership;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import com.serviceos.network.domain.PartnerOrganization;
import com.serviceos.network.domain.ServiceNetwork;
import com.serviceos.network.domain.TechnicianProfile;
import com.serviceos.network.domain.TechnicianQualification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 网点目录持久化端口。 */
public interface NetworkDirectoryRepository {
    Optional<PartnerOrganization> findPartner(String tenantId, UUID partnerOrganizationId);
    List<PartnerOrganization> listPartners(String tenantId);
    void insertPartner(PartnerOrganization partner);

    Optional<ServiceNetwork> findNetwork(String tenantId, UUID serviceNetworkId);
    Optional<ServiceNetwork> findNetworkForUpdate(String tenantId, UUID serviceNetworkId);
    List<ServiceNetwork> listNetworks(String tenantId, UUID partnerOrganizationId);
    void insertNetwork(ServiceNetwork network);
    boolean deactivateNetwork(String tenantId, UUID serviceNetworkId, long expectedVersion,
            String reason, String actorId, Instant now);

    Optional<NetworkMembership> findMembership(String tenantId, UUID membershipId);
    Optional<NetworkMembership> findMembershipForUpdate(String tenantId, UUID membershipId);
    List<NetworkMembership> listMemberships(String tenantId, UUID serviceNetworkId, UUID principalId);
    void insertMembership(NetworkMembership membership);
    boolean terminateMembership(String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt);

    Optional<TechnicianProfile> findTechnicianProfile(String tenantId, UUID profileId);
    Optional<TechnicianProfile> findTechnicianProfileForUpdate(String tenantId, UUID profileId);
    Optional<TechnicianProfile> findTechnicianProfileByPrincipal(String tenantId, UUID principalId);
    List<TechnicianProfile> listTechnicianProfiles(String tenantId);
    void insertTechnicianProfile(TechnicianProfile profile);
    boolean declareTechnicianSupportedClientKinds(
            String tenantId, UUID profileId, long expectedVersion,
            List<String> supportedClientKinds, Instant now);
    boolean disableTechnicianProfile(String tenantId, UUID profileId, long expectedVersion,
            String reason, String actorId, Instant now);
    boolean enableTechnicianProfile(String tenantId, UUID profileId, long expectedVersion, Instant now);

    Optional<NetworkTechnicianMembership> findTechnicianMembership(String tenantId, UUID membershipId);
    Optional<NetworkTechnicianMembership> findTechnicianMembershipForUpdate(String tenantId, UUID membershipId);
    List<NetworkTechnicianMembership> listTechnicianMemberships(
            String tenantId, UUID serviceNetworkId, UUID technicianProfileId);
    Optional<NetworkTechnicianMembership> findActiveTechnicianMembership(
            String tenantId, UUID serviceNetworkId, UUID technicianProfileId, Instant at);
    void insertTechnicianMembership(NetworkTechnicianMembership membership);
    boolean terminateTechnicianMembership(String tenantId, UUID membershipId, long expectedVersion,
            String reason, String actorId, Instant terminatedAt);

    Optional<TechnicianQualification> findQualification(String tenantId, UUID qualificationId);
    Optional<TechnicianQualification> findQualificationForUpdate(String tenantId, UUID qualificationId);
    List<TechnicianQualification> listQualifications(String tenantId, UUID technicianProfileId);
    boolean hasApprovedQualification(String tenantId, UUID technicianProfileId, Instant at);
    void insertQualification(TechnicianQualification qualification);
    boolean decideQualification(String tenantId, UUID qualificationId, long expectedVersion,
            String status, String reason, String actorId, Instant now);

    void insertDirectoryEvent(UUID eventId, String tenantId, String eventType, String resourceType,
            UUID resourceId, long resourceVersion, String reason, String actorId,
            String requestDigest, String correlationId, Instant occurredAt);

    void insertClearanceWorkItem(UUID workItemId, String tenantId, String subjectType,
            UUID serviceNetworkId, UUID technicianProfileId, String reason,
            int openTasks, int openAppointments, int openVisits, int activeAssignments, int offlinePackages,
            String createdBy, String correlationId, Instant createdAt);

    List<ClearanceWorkItemView> listOpenClearanceWorkItems(String tenantId);
}
