package com.serviceos.network.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalStatusQuery;
import com.serviceos.network.api.NetworkAssignedWorkImpactPort;
import com.serviceos.network.api.NetworkAuthorizationEvidence;
import com.serviceos.network.api.NetworkAuthorizationPort;
import com.serviceos.network.api.NetworkCommandService;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.NetworkWorkImpact;
import com.serviceos.network.api.PartnerOrganizationView;
import com.serviceos.network.api.ServiceNetworkView;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.network.domain.NetworkMembership;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import com.serviceos.network.domain.PartnerOrganization;
import com.serviceos.network.domain.ServiceNetwork;
import com.serviceos.network.domain.TechnicianProfile;
import com.serviceos.network.domain.TechnicianQualification;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 网点目录命令编排：授权 → 幂等 → 行锁 → 版本迁移 → 目录事件 → 审计。
 * 清退/停用同事务写入 clearance 待办并汇总影响端口结果。
 */
@Service
final class DefaultNetworkCommandService implements NetworkCommandService {
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private final NetworkDirectoryRepository directory;
    private final NetworkAuthorizationPort authorization;
    private final PrincipalStatusQuery principalStatus;
    private final NetworkAssignedWorkImpactPort workImpact;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultNetworkCommandService(
            NetworkDirectoryRepository directory,
            NetworkAuthorizationPort authorization,
            PrincipalStatusQuery principalStatus,
            NetworkAssignedWorkImpactPort workImpact,
            IdempotencyService idempotency,
            AuditAppender audit,
            Clock clock
    ) {
        this.directory = directory;
        this.authorization = authorization;
        this.principalStatus = principalStatus;
        this.workImpact = workImpact;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public PartnerOrganizationView createPartnerOrganization(
            CurrentPrincipal actor, CommandMetadata metadata, String code, String name
    ) {
        code = requireText(code, "code", 64);
        name = requireText(name, "name", 200);
        var input = new CreatePartnerInput(code, name);
        CommandExecution execution = begin(actor, metadata, "network.createPartner", "network.managePartner",
                code, input);
        if (execution.replay()) {
            return findPartnerByCode(actor.tenantId(), code).toView();
        }
        Instant now = clock.instant();
        UUID partnerId = UUID.randomUUID();
        PartnerOrganization partner = new PartnerOrganization(partnerId, actor.tenantId(), code, name,
                PartnerOrganization.Status.ACTIVE, 1, now, now);
        directory.insertPartner(partner);
        complete(actor, metadata, execution, partnerId, 1, "PARTNER_CREATED", "PartnerOrganization",
                "network.managePartner", null, now);
        return partner.toView();
    }

    @Override
    @Transactional
    public ServiceNetworkView createServiceNetwork(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID partnerOrganizationId, String networkCode, String networkName
    ) {
        networkCode = requireText(networkCode, "networkCode", 64);
        networkName = requireText(networkName, "networkName", 200);
        var input = new CreateNetworkInput(partnerOrganizationId, networkCode, networkName);
        CommandExecution execution = begin(actor, metadata, "network.createNetwork", "network.manageNetwork",
                networkCode, input);
        if (execution.replay()) {
            return findNetworkByCode(actor.tenantId(), networkCode).toView();
        }
        PartnerOrganization partner = requirePartner(actor.tenantId(), partnerOrganizationId);
        partner.requireActive();
        Instant now = clock.instant();
        UUID networkId = UUID.randomUUID();
        ServiceNetwork network = new ServiceNetwork(networkId, actor.tenantId(), partnerOrganizationId,
                networkCode, networkName, ServiceNetwork.Status.ACTIVE, 1, now, now,
                null, null, null);
        directory.insertNetwork(network);
        complete(actor, metadata, execution, networkId, 1, "NETWORK_CREATED", "ServiceNetwork",
                "network.manageNetwork", null, now);
        return network.toView();
    }

    @Override
    @Transactional
    public NetworkMembershipView inviteNetworkMember(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID networkId, Long expectedNetworkVersion, UUID principalId,
            String role, Instant validFrom
    ) {
        NetworkMembership.Role membershipRole = requireRole(role);
        if (validFrom == null) throw new IllegalArgumentException("validFrom is invalid");
        var input = new InviteMemberInput(networkId, expectedNetworkVersion, principalId, membershipRole.name(), validFrom);
        NetworkAuthorizationEvidence authorizationEvidence = requireMembershipInvite(
                actor, networkId, membershipRole, metadata.correlationId());
        CommandExecution execution = beginAfterAuthorization(actor, metadata, "network.inviteMember",
                principalId.toString(), input, authorizationEvidence);
        if (execution.replay()) {
            UUID membershipId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return requireMembership(actor.tenantId(), membershipId).toView();
        }
        ServiceNetwork network = expectedNetworkVersion == null
                ? requireNetwork(actor.tenantId(), networkId)
                : lockedNetwork(actor.tenantId(), networkId, expectedNetworkVersion);
        if (!principalStatus.isActive(actor.tenantId(), principalId)) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Principal 不存在或未激活");
        }
        Instant now = clock.instant();
        UUID membershipId = UUID.randomUUID();
        NetworkMembership membership = new NetworkMembership(membershipId, actor.tenantId(), networkId,
                principalId, membershipRole, NetworkMembership.Status.ACTIVE, validFrom, null,
                actor.principalId(), now, null, null, null, 1);
        directory.insertMembership(membership);
        complete(actor, metadata, execution, membershipId, 1, "MEMBERSHIP_INVITED", "NetworkMembership",
                "network.manageMembership", null, now, membershipId.toString());
        return membership.toView();
    }

    @Override
    @Transactional
    public NetworkMembershipView terminateNetworkMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion, String reason
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        var input = new TerminateMemberInput(membershipId, expectedVersion, reason);
        CommandExecution execution = begin(actor, metadata, "network.terminateMember", "network.manageMembership",
                membershipId.toString(), input);
        if (execution.replay()) {
            return requireMembership(actor.tenantId(), membershipId).toView();
        }
        NetworkMembership current = lockedMembership(actor.tenantId(), membershipId, expectedVersion);
        if (current.status() != NetworkMembership.Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "成员关系已终止");
        }
        authorization.requireTenantCapability(actor, "network.manageMembership",
                current.serviceNetworkId().toString(), metadata.correlationId());
        Instant now = clock.instant();
        if (!directory.terminateMembership(actor.tenantId(), membershipId, expectedVersion,
                reason, actor.principalId(), now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, membershipId, expectedVersion + 1,
                "MEMBERSHIP_TERMINATED", "NetworkMembership", "network.manageMembership", reason, now);
        return requireMembership(actor.tenantId(), membershipId).toView();
    }

    @Override
    @Transactional
    public ServiceNetworkView deactivateServiceNetwork(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID networkId, long expectedVersion, String reason
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        var input = new DeactivateNetworkInput(networkId, expectedVersion, reason);
        CommandExecution execution = begin(actor, metadata, "network.deactivateNetwork", "network.manageNetwork",
                networkId.toString(), input);
        if (execution.replay()) {
            return requireNetworkAnyStatus(actor.tenantId(), networkId).toView();
        }
        ServiceNetwork network = lockedNetwork(actor.tenantId(), networkId, expectedVersion);
        NetworkWorkImpact impact = workImpact.summarizeForNetwork(actor.tenantId(), networkId.toString());
        Instant now = clock.instant();
        if (!directory.deactivateNetwork(actor.tenantId(), networkId, expectedVersion,
                reason, actor.principalId(), now)) {
            throw versionConflict();
        }
        directory.insertClearanceWorkItem(UUID.randomUUID(), actor.tenantId(), "SERVICE_NETWORK",
                networkId, null, reason, impact.openTasks(), impact.openAppointments(), impact.openVisits(),
                impact.activeAssignments(), impact.offlinePackages(), actor.principalId(),
                metadata.correlationId(), now);
        complete(actor, metadata, execution, networkId, expectedVersion + 1,
                "NETWORK_DEACTIVATED", "ServiceNetwork", "network.manageNetwork", reason, now);
        return requireNetworkAnyStatus(actor.tenantId(), networkId).toView();
    }

    @Override
    @Transactional
    public TechnicianProfileView createTechnicianProfile(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, String displayName
    ) {
        displayName = requireText(displayName, "displayName", 200);
        var input = new CreateTechnicianInput(principalId, displayName);
        CommandExecution execution = begin(actor, metadata, "network.createTechnician", "network.manageTechnician",
                principalId.toString(), input);
        if (execution.replay()) {
            return directory.findTechnicianProfileByPrincipal(actor.tenantId(), principalId)
                    .orElseThrow(() -> new IllegalStateException("幂等结果引用的师傅档案不存在"))
                    .toView();
        }
        if (!principalStatus.isActive(actor.tenantId(), principalId)) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Principal 不存在或未激活");
        }
        Instant now = clock.instant();
        UUID profileId = UUID.randomUUID();
        TechnicianProfile profile = new TechnicianProfile(profileId, actor.tenantId(), principalId,
                displayName, TechnicianProfile.Status.ACTIVE, 1, now, now, null, null, null);
        directory.insertTechnicianProfile(profile);
        complete(actor, metadata, execution, profileId, 1, "TECHNICIAN_CREATED", "TechnicianProfile",
                "network.manageTechnician", null, now);
        return profile.toView();
    }

    @Override
    @Transactional
    public TechnicianProfileView disableTechnicianProfile(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID profileId, long expectedVersion, String reason
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        var input = new DisableTechnicianInput(profileId, expectedVersion, reason);
        CommandExecution execution = begin(actor, metadata, "network.disableTechnician", "network.manageTechnician",
                profileId.toString(), input);
        if (execution.replay()) {
            return requireTechnician(actor.tenantId(), profileId).toView();
        }
        TechnicianProfile profile = lockedTechnician(actor.tenantId(), profileId, expectedVersion);
        NetworkWorkImpact impact = workImpact.summarizeForTechnician(
                actor.tenantId(), profile.principalId().toString());
        Instant now = clock.instant();
        if (!directory.disableTechnicianProfile(actor.tenantId(), profileId, expectedVersion,
                reason, actor.principalId(), now)) {
            throw versionConflict();
        }
        directory.insertClearanceWorkItem(UUID.randomUUID(), actor.tenantId(), "TECHNICIAN_PROFILE",
                null, profileId, reason, impact.openTasks(), impact.openAppointments(), impact.openVisits(),
                impact.activeAssignments(), impact.offlinePackages(), actor.principalId(),
                metadata.correlationId(), now);
        complete(actor, metadata, execution, profileId, expectedVersion + 1,
                "TECHNICIAN_DISABLED", "TechnicianProfile", "network.manageTechnician", reason, now);
        return requireTechnician(actor.tenantId(), profileId).toView();
    }

    @Override
    @Transactional
    public TechnicianProfileView enableTechnicianProfile(
            CurrentPrincipal actor, CommandMetadata metadata, UUID profileId, long expectedVersion
    ) {
        requireExpectedVersion(expectedVersion);
        var input = new EnableTechnicianInput(profileId, expectedVersion);
        CommandExecution execution = begin(actor, metadata, "network.enableTechnician", "network.manageTechnician",
                profileId.toString(), input);
        if (execution.replay()) {
            return requireTechnician(actor.tenantId(), profileId).toView();
        }
        lockedTechnician(actor.tenantId(), profileId, expectedVersion);
        Instant now = clock.instant();
        if (!directory.enableTechnicianProfile(actor.tenantId(), profileId, expectedVersion, now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, profileId, expectedVersion + 1,
                "TECHNICIAN_ENABLED", "TechnicianProfile", "network.manageTechnician", null, now);
        return requireTechnician(actor.tenantId(), profileId).toView();
    }

    @Override
    @Transactional
    public NetworkTechnicianMembershipView createNetworkTechnicianMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID networkId, UUID technicianProfileId, Instant validFrom
    ) {
        if (validFrom == null) throw new IllegalArgumentException("validFrom is invalid");
        var input = new CreateTechMembershipInput(networkId, technicianProfileId, validFrom);
        CommandExecution execution = begin(actor, metadata, "network.createTechnicianMembership",
                "network.manageTechnician", technicianProfileId.toString(), input);
        if (execution.replay()) {
            UUID membershipId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return requireTechMembership(actor.tenantId(), membershipId).toView();
        }
        requireNetwork(actor.tenantId(), networkId);
        requireTechnician(actor.tenantId(), technicianProfileId).requireActive();
        Instant now = clock.instant();
        UUID membershipId = UUID.randomUUID();
        NetworkTechnicianMembership membership = new NetworkTechnicianMembership(membershipId, actor.tenantId(),
                networkId, technicianProfileId, NetworkTechnicianMembership.Status.ACTIVE,
                validFrom, null, actor.principalId(), now, null, null, null, 1);
        directory.insertTechnicianMembership(membership);
        complete(actor, metadata, execution, membershipId, 1, "TECHNICIAN_MEMBERSHIP_CREATED",
                "NetworkTechnicianMembership", "network.manageTechnician", null, now, membershipId.toString());
        return membership.toView();
    }

    @Override
    @Transactional
    public NetworkTechnicianMembershipView terminateNetworkTechnicianMembership(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID membershipId, long expectedVersion, String reason
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        var input = new TerminateTechMembershipInput(membershipId, expectedVersion, reason);
        CommandExecution execution = begin(actor, metadata, "network.terminateTechnicianMembership",
                "network.manageTechnician", membershipId.toString(), input);
        if (execution.replay()) {
            return requireTechMembership(actor.tenantId(), membershipId).toView();
        }
        NetworkTechnicianMembership current = lockedTechMembership(actor.tenantId(), membershipId, expectedVersion);
        if (current.status() != NetworkTechnicianMembership.Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "师傅服务关系已终止");
        }
        Instant now = clock.instant();
        if (!directory.terminateTechnicianMembership(actor.tenantId(), membershipId, expectedVersion,
                reason, actor.principalId(), now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, membershipId, expectedVersion + 1,
                "TECHNICIAN_MEMBERSHIP_TERMINATED", "NetworkTechnicianMembership",
                "network.manageTechnician", reason, now);
        return requireTechMembership(actor.tenantId(), membershipId).toView();
    }

    @Override
    @Transactional
    public TechnicianQualificationView submitQualification(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID technicianProfileId, String qualificationCode, Instant validFrom, Instant validTo
    ) {
        qualificationCode = requireText(qualificationCode, "qualificationCode", 64);
        if (validFrom == null) throw new IllegalArgumentException("validFrom is invalid");
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo must be after validFrom");
        }
        var input = new SubmitQualificationInput(technicianProfileId, qualificationCode, validFrom, validTo);
        CommandExecution execution = begin(actor, metadata, "network.submitQualification",
                "network.manageTechnician", technicianProfileId.toString(), input);
        if (execution.replay()) {
            UUID qualificationId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return requireQualification(actor.tenantId(), qualificationId).toView();
        }
        requireTechnician(actor.tenantId(), technicianProfileId).requireActive();
        Instant now = clock.instant();
        UUID qualificationId = UUID.randomUUID();
        TechnicianQualification qualification = new TechnicianQualification(qualificationId, actor.tenantId(),
                technicianProfileId, qualificationCode, TechnicianQualification.Status.PENDING,
                validFrom, validTo, actor.principalId(), now, null, null, null, 1);
        directory.insertQualification(qualification);
        complete(actor, metadata, execution, qualificationId, 1, "QUALIFICATION_SUBMITTED",
                "TechnicianQualification", "network.manageTechnician", null, now, qualificationId.toString());
        return qualification.toView();
    }

    @Override
    @Transactional
    public TechnicianQualificationView decideQualification(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID qualificationId, long expectedVersion, String decision, String reason
    ) {
        requireExpectedVersion(expectedVersion);
        TechnicianQualification.Status decisionStatus = requireDecision(decision);
        reason = normalizeOptionalReason(reason);
        var input = new DecideQualificationInput(qualificationId, expectedVersion, decisionStatus.name(), reason);
        // 总部审核必须使用 reviewQualification，网点 manageTechnician 不能自批。
        CommandExecution execution = begin(actor, metadata, "network.decideQualification",
                "network.reviewQualification", qualificationId.toString(), input);
        if (execution.replay()) {
            return requireQualification(actor.tenantId(), qualificationId).toView();
        }
        TechnicianQualification current = lockedQualification(actor.tenantId(), qualificationId, expectedVersion);
        if (current.status() != TechnicianQualification.Status.PENDING) {
            throw new BusinessProblem(ProblemCode.NETWORK_QUALIFICATION_CONFLICT, "资质已裁决");
        }
        Instant now = clock.instant();
        if (!directory.decideQualification(actor.tenantId(), qualificationId, expectedVersion,
                decisionStatus.name(), reason, actor.principalId(), now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, qualificationId, expectedVersion + 1,
                "QUALIFICATION_DECIDED", "TechnicianQualification", "network.reviewQualification", reason, now);
        return requireQualification(actor.tenantId(), qualificationId).toView();
    }

    private NetworkAuthorizationEvidence requireMembershipInvite(
            CurrentPrincipal actor, UUID networkId, NetworkMembership.Role role, String correlationId
    ) {
        if (role == NetworkMembership.Role.MANAGER) {
            try {
                return authorization.requireTenantCapability(actor, "network.manageMembership",
                        networkId.toString(), correlationId);
            } catch (BusinessProblem denied) {
                if (denied.code() != ProblemCode.ACCESS_DENIED) {
                    throw denied;
                }
            }
            return authorization.requireTenantCapability(actor, "network.manageNetwork",
                    networkId.toString(), correlationId);
        }
        return authorization.requireTenantCapability(actor, "network.manageMembership",
                networkId.toString(), correlationId);
    }

    private CommandExecution begin(
            CurrentPrincipal actor, CommandMetadata metadata, String operation,
            String capability, String resourceId, Object input
    ) {
        // M204：Network Portal 委托期间按 NETWORK scope 鉴权；Admin TENANT 路径不变。
        String networkScope = NetworkScopedNetworkAuthorization.currentNetworkId();
        NetworkAuthorizationEvidence decision = networkScope != null
                ? authorization.requireNetworkCapability(
                        actor, capability, networkScope, resourceId, metadata.correlationId())
                : authorization.requireTenantCapability(
                        actor, capability, resourceId, metadata.correlationId());
        return beginAfterAuthorization(actor, metadata, operation, resourceId, input, decision);
    }

    private CommandExecution beginAfterAuthorization(
            CurrentPrincipal actor, CommandMetadata metadata, String operation,
            String resourceId, Object input, NetworkAuthorizationEvidence decision
    ) {
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        String requestDigest = Sha256.digest(canonicalJson(input));
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, operation, requestDigest);
        return new CommandExecution(operation, requestDigest, decision, idempotencyDecision);
    }

    private void complete(
            CurrentPrincipal actor, CommandMetadata metadata, CommandExecution execution,
            UUID resourceId, long resourceVersion, String eventType, String resourceType,
            String capability, String reason, Instant now
    ) {
        complete(actor, metadata, execution, resourceId, resourceVersion, eventType, resourceType,
                capability, reason, now, resourceId.toString());
    }

    private void complete(
            CurrentPrincipal actor, CommandMetadata metadata, CommandExecution execution,
            UUID resourceId, long resourceVersion, String eventType, String resourceType,
            String capability, String reason, Instant now, String idempotencyResourceId
    ) {
        directory.insertDirectoryEvent(UUID.randomUUID(), actor.tenantId(), eventType, resourceType,
                resourceId, resourceVersion, reason, actor.principalId(), execution.requestDigest(),
                metadata.correlationId(), now);
        audit.append(new AuditEntry(UUID.randomUUID(), actor.tenantId(), actor.principalId(),
                eventType, capability, resourceType, resourceId.toString(), "ALLOW",
                execution.authorization().matchedGrantIds(), execution.authorization().policyVersion(),
                "SUCCEEDED", null, execution.requestDigest(), metadata.correlationId(), now));
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        idempotency.complete(context, execution.operation(), idempotencyResourceId,
                Sha256.digest(idempotencyResourceId + "|" + resourceVersion));
    }

    private PartnerOrganization requirePartner(String tenantId, UUID partnerId) {
        return directory.findPartner(tenantId, partnerId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "合作组织不存在"));
    }

    private PartnerOrganization findPartnerByCode(String tenantId, String code) {
        return directory.listPartners(tenantId).stream()
                .filter(partner -> partner.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("幂等结果引用的合作组织不存在"));
    }

    private ServiceNetwork requireNetwork(String tenantId, UUID networkId) {
        ServiceNetwork network = directory.findNetwork(tenantId, networkId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "网点不存在"));
        network.requireActive();
        return network;
    }

    private ServiceNetwork requireNetworkAnyStatus(String tenantId, UUID networkId) {
        return directory.findNetwork(tenantId, networkId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "网点不存在"));
    }

    private ServiceNetwork findNetworkByCode(String tenantId, String code) {
        return directory.listNetworks(tenantId, null).stream()
                .filter(network -> network.networkCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("幂等结果引用的网点不存在"));
    }

    private ServiceNetwork lockedNetwork(String tenantId, UUID networkId, long expectedVersion) {
        ServiceNetwork network = directory.findNetworkForUpdate(tenantId, networkId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "网点不存在"));
        network.requireActive();
        if (network.version() != expectedVersion) throw versionConflict();
        return network;
    }

    private NetworkMembership requireMembership(String tenantId, UUID membershipId) {
        return directory.findMembership(tenantId, membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "成员关系不存在"));
    }

    private NetworkMembership lockedMembership(String tenantId, UUID membershipId, long expectedVersion) {
        NetworkMembership membership = directory.findMembershipForUpdate(tenantId, membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "成员关系不存在"));
        if (membership.version() != expectedVersion) throw versionConflict();
        return membership;
    }

    private TechnicianProfile requireTechnician(String tenantId, UUID profileId) {
        return directory.findTechnicianProfile(tenantId, profileId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "师傅档案不存在"));
    }

    private TechnicianProfile lockedTechnician(String tenantId, UUID profileId, long expectedVersion) {
        TechnicianProfile profile = directory.findTechnicianProfileForUpdate(tenantId, profileId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "师傅档案不存在"));
        if (profile.version() != expectedVersion) throw versionConflict();
        return profile;
    }

    private NetworkTechnicianMembership requireTechMembership(String tenantId, UUID membershipId) {
        return directory.findTechnicianMembership(tenantId, membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "师傅服务关系不存在"));
    }

    private NetworkTechnicianMembership lockedTechMembership(String tenantId, UUID membershipId, long expectedVersion) {
        NetworkTechnicianMembership membership = directory.findTechnicianMembershipForUpdate(tenantId, membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "师傅服务关系不存在"));
        if (membership.version() != expectedVersion) throw versionConflict();
        return membership;
    }

    private TechnicianQualification requireQualification(String tenantId, UUID qualificationId) {
        return directory.findQualification(tenantId, qualificationId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "资质记录不存在"));
    }

    private TechnicianQualification lockedQualification(String tenantId, UUID qualificationId, long expectedVersion) {
        TechnicianQualification qualification = directory.findQualificationForUpdate(tenantId, qualificationId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "资质记录不存在"));
        if (qualification.version() != expectedVersion) throw versionConflict();
        return qualification;
    }

    private static boolean isReplay(IdempotencyDecision decision) {
        return decision.kind() == IdempotencyDecision.Kind.REPLAY;
    }

    private static void requireExpectedVersion(long version) {
        if (version < 1) throw new IllegalArgumentException("expectedVersion must be positive");
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String normalizeOptionalReason(String reason) {
        if (reason == null) return null;
        return requireText(reason, "reason", 500);
    }

    private static NetworkMembership.Role requireRole(String value) {
        try {
            return NetworkMembership.Role.valueOf(requireText(value, "role", 40));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("role is invalid", exception);
        }
    }

    private static TechnicianQualification.Status requireDecision(String value) {
        String normalized = requireText(value, "decision", 24);
        if (!normalized.equals("APPROVED") && !normalized.equals("REJECTED")) {
            throw new IllegalArgumentException("decision is invalid");
        }
        return TechnicianQualification.Status.valueOf(normalized);
    }

    private static BusinessProblem versionConflict() {
        return new BusinessProblem(ProblemCode.VERSION_CONFLICT, "版本已被并发修改");
    }

    private static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("网点命令不能序列化", exception);
        }
    }

    private record CreatePartnerInput(String code, String name) {}
    private record CreateNetworkInput(UUID partnerOrganizationId, String networkCode, String networkName) {}
    private record InviteMemberInput(UUID networkId, Long expectedNetworkVersion, UUID principalId, String role, Instant validFrom) {}
    private record TerminateMemberInput(UUID membershipId, long expectedVersion, String reason) {}
    private record DeactivateNetworkInput(UUID networkId, long expectedVersion, String reason) {}
    private record CreateTechnicianInput(UUID principalId, String displayName) {}
    private record DisableTechnicianInput(UUID profileId, long expectedVersion, String reason) {}
    private record EnableTechnicianInput(UUID profileId, long expectedVersion) {}
    private record CreateTechMembershipInput(UUID networkId, UUID technicianProfileId, Instant validFrom) {}
    private record TerminateTechMembershipInput(UUID membershipId, long expectedVersion, String reason) {}
    private record SubmitQualificationInput(UUID technicianProfileId, String qualificationCode, Instant validFrom, Instant validTo) {}
    private record DecideQualificationInput(UUID qualificationId, long expectedVersion, String decision, String reason) {}

    private record CommandExecution(
            String operation,
            String requestDigest,
            NetworkAuthorizationEvidence authorization,
            IdempotencyDecision decision
    ) {
        boolean replay() { return isReplay(decision); }
    }
}
