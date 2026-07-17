package com.serviceos.network.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkAuthorizationPort;
import com.serviceos.network.api.NetworkCommandService;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkPortalManageTechnicianService;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.network.domain.NetworkTechnicianMembership;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * M204 Network Portal 师傅关系与资质提交适配器。
 * <p>
 * 事务边界：Portal 门禁与委托 M185 命令同事务；聚合修改、幂等、审计与目录事件由命令服务保证。
 * 幂等键：HTTP Idempotency-Key 原样下传。
 * 失败关闭：伪造上下文、非成员、跨网点 membership、非本网点师傅提交资质。
 */
@Service
final class DefaultNetworkPortalManageTechnicianService implements NetworkPortalManageTechnicianService {
    private static final String PORTAL_CAPABILITY = "networkPortal.manageTechnician";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final NetworkAuthorizationPort authorization;
    private final NetworkCommandService commands;
    private final NetworkDirectoryRepository directory;
    private final NetworkPortalTechnicianQuery technicians;
    private final Clock clock;

    DefaultNetworkPortalManageTechnicianService(
            PrincipalNetworkAffiliationQuery affiliations,
            NetworkAuthorizationPort authorization,
            NetworkCommandService commands,
            NetworkDirectoryRepository directory,
            NetworkPortalTechnicianQuery technicians,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.commands = commands;
        this.directory = directory;
        this.technicians = technicians;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NetworkTechnicianMembershipView createMembership(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID technicianProfileId,
            Instant validFrom
    ) {
        Objects.requireNonNull(technicianProfileId, "technicianProfileId");
        Objects.requireNonNull(validFrom, "validFrom");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        return NetworkScopedNetworkAuthorization.callWith(networkId.toString(), () ->
                commands.createNetworkTechnicianMembership(
                        principal, metadata, networkId, technicianProfileId, validFrom));
    }

    @Override
    @Transactional
    public NetworkTechnicianMembershipView terminateMembership(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID membershipId,
            long expectedVersion,
            String reason
    ) {
        Objects.requireNonNull(membershipId, "membershipId");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        NetworkTechnicianMembership membership = directory.findTechnicianMembership(
                        principal.tenantId(), membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "师傅服务关系不存在"));
        if (!membership.serviceNetworkId().equals(networkId)) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "不能终止其他网点的师傅服务关系");
        }
        return NetworkScopedNetworkAuthorization.callWith(networkId.toString(), () ->
                commands.terminateNetworkTechnicianMembership(
                        principal, metadata, membershipId, expectedVersion, reason));
    }

    @Override
    @Transactional
    public TechnicianQualificationView submitQualification(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID technicianProfileId,
            String qualificationCode,
            Instant validFrom,
            Instant validTo
    ) {
        Objects.requireNonNull(technicianProfileId, "technicianProfileId");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        boolean onNetwork = technicians.listActiveTechnicians(principal.tenantId(), networkId).stream()
                .map(NetworkPortalTechnicianView::technicianProfileId)
                .anyMatch(technicianProfileId::equals);
        if (!onNetwork) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "师傅对本网点没有 ACTIVE 服务关系，不能提交资质");
        }
        return NetworkScopedNetworkAuthorization.callWith(networkId.toString(), () ->
                commands.submitQualification(
                        principal, metadata, technicianProfileId, qualificationCode, validFrom, validTo));
    }

    private UUID requireAuthorizedNetwork(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = parseNetworkContext(networkContextHeader);
        UUID principalId = requirePrincipalUuid(actor);
        Instant at = clock.instant();
        boolean member = affiliations.listActiveNetworkMemberships(actor.tenantId(), principalId, at).stream()
                .map(NetworkMembershipView::serviceNetworkId)
                .anyMatch(networkId::equals);
        if (!member) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体不能使用请求的 Network Portal 上下文");
        }
        authorization.requireNetworkCapability(
                actor, PORTAL_CAPABILITY, networkId.toString(), networkId.toString(), correlationId);
        return networkId;
    }

    private static UUID parseNetworkContext(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "缺少 X-Network-Context");
        }
        String raw = header.trim();
        String uuidPart = raw;
        if (raw.startsWith(CONTEXT_PREFIX)) {
            uuidPart = raw.substring(CONTEXT_PREFIX.length());
        } else if (raw.contains("|")) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Network Portal 上下文形态无效");
        }
        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Network Portal 上下文形态无效");
        }
    }

    private static UUID requirePrincipalUuid(CurrentPrincipal actor) {
        try {
            return UUID.fromString(actor.principalId());
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "当前主体无法形成 Network Portal 上下文");
        }
    }
}
