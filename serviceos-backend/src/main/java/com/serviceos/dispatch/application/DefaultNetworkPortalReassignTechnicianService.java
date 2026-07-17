package com.serviceos.dispatch.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ManualReassignTechnicianCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
import com.serviceos.dispatch.api.NetworkPortalReassignTechnicianService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianEligibilityQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * M200 Network Portal 改派师傅适配器。
 * <p>
 * 事务边界：预检与 ManualReassign 同事务；聚合修改、容量、幂等与 Outbox 由 ManualReassign 保证。
 * 幂等键：HTTP Idempotency-Key 原样下传。
 * 失败关闭：伪造上下文、非成员、师傅不在网点、跨网点 ACTIVE、无 ACTIVE TECHNICIAN。
 */
@Service
final class DefaultNetworkPortalReassignTechnicianService implements NetworkPortalReassignTechnicianService {
    private static final String CAPABILITY = "networkPortal.reassignTechnician";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final NetworkPortalTechnicianQuery technicians;
    private final TechnicianEligibilityQuery eligibility;
    private final ManualServiceAssignmentService manualAssignments;
    private final JdbcClient jdbc;
    private final Clock clock;

    DefaultNetworkPortalReassignTechnicianService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            NetworkPortalTechnicianQuery technicians,
            TechnicianEligibilityQuery eligibility,
            ManualServiceAssignmentService manualAssignments,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.technicians = technicians;
        this.eligibility = eligibility;
        this.manualAssignments = manualAssignments;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ManualServiceAssignmentReceipt reassignTechnician(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            String technicianAssigneeId,
            String businessType,
            String reasonCode
    ) {
        Objects.requireNonNull(taskId, "taskId");
        String techAssignee = requireText(technicianAssigneeId, "technicianAssigneeId", 128);
        String type = requireText(businessType, "businessType", 100);
        String reason = requireReason(reasonCode);

        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        NetworkPortalTechnicianView technician = requireTechnicianOnNetwork(
                principal.tenantId(), networkId, techAssignee);
        Instant at = clock.instant();
        if (!eligibility.canAcceptAssignment(
                principal.tenantId(), technician.principalId(), networkId, at)) {
            String explain = eligibility.explainIneligibility(
                    principal.tenantId(), technician.principalId(), networkId, at);
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    explain == null ? "师傅当前不可接单" : "师傅当前不可接单：" + explain);
        }

        String networkAssigneeId = networkId.toString();
        String normalizedTechAssignee = technician.technicianProfileId().toString();
        requireNetworkOwnedTask(principal.tenantId(), taskId, networkAssigneeId);

        return NetworkScopedDispatchAuthorization.callWith(networkAssigneeId, () ->
                manualAssignments.reassignTechnician(
                        principal,
                        metadata,
                        new ManualReassignTechnicianCommand(
                                taskId, networkAssigneeId, normalizedTechAssignee, type, reason)));
    }

    private void requireNetworkOwnedTask(String tenantId, UUID taskId, String networkAssigneeId) {
        Optional<String> activeNetwork = activeAssignee(tenantId, taskId, "NETWORK");
        if (activeNetwork.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "任务对本网点没有 ACTIVE NETWORK 责任");
        }
        if (!activeNetwork.get().equals(networkAssigneeId)) {
            throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "任务已有其他网点的 ACTIVE NETWORK 责任，Network Portal 不支持跨网点改派");
        }
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
        authorization.require(actor, AuthorizationRequest.networkCapability(
                CAPABILITY, actor.tenantId(), "ServiceNetwork", networkId.toString(), networkId.toString()),
                correlationId);
        return networkId;
    }

    private NetworkPortalTechnicianView requireTechnicianOnNetwork(
            String tenantId, UUID networkId, String technicianAssigneeId
    ) {
        Optional<NetworkPortalTechnicianView> match = technicians.listActiveTechnicians(tenantId, networkId)
                .stream()
                .filter(row -> row.technicianProfileId().toString().equals(technicianAssigneeId)
                        || row.principalId().toString().equals(technicianAssigneeId))
                .findFirst();
        if (match.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "师傅对本网点没有 ACTIVE 服务关系");
        }
        return match.get();
    }

    private Optional<String> activeAssignee(String tenantId, UUID taskId, String level) {
        return jdbc.sql("""
                        SELECT assignee_id
                          FROM dsp_service_assignment
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND responsibility_level = :level AND status = 'ACTIVE'
                         ORDER BY created_at
                         LIMIT 1
                        """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .param("level", level)
                .query(String.class)
                .optional();
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

    private static String requireText(String value, String name, int max) {
        String normalized = Objects.requireNonNull(value, name).trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, name + " 无效");
        }
        return normalized;
    }

    private static String requireReason(String reasonCode) {
        String normalized = Objects.requireNonNull(reasonCode, "reasonCode").trim();
        if (!normalized.matches("^[A-Z][A-Z0-9_]{1,99}$")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "reasonCode 无效");
        }
        return normalized;
    }
}
