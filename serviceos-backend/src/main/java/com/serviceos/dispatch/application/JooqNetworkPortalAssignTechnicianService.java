package com.serviceos.dispatch.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ManualAssignServiceAssignmentCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
import com.serviceos.dispatch.api.NetworkPortalAssignTechnicianService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianEligibilityQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;

/**
 * M196 Network Portal 指派师傅适配器。
 * <p>
 * 事务边界：预检与 ManualAssign 同事务；聚合修改、容量、幂等与 Outbox 由 ManualAssign 保证。
 * 幂等键：HTTP Idempotency-Key 原样下传。
 * 失败关闭：伪造上下文、非成员、师傅不在网点、跨网点 ACTIVE、不同师傅 ACTIVE。
 */
@Service
final class JooqNetworkPortalAssignTechnicianService implements NetworkPortalAssignTechnicianService {
    private static final String CAPABILITY = "networkPortal.assignTechnician";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final NetworkPortalTechnicianQuery technicians;
    private final TechnicianEligibilityQuery eligibility;
    private final ManualServiceAssignmentService manualAssignments;
    private final DSLContext dsl;
    private final Clock clock;

    JooqNetworkPortalAssignTechnicianService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            NetworkPortalTechnicianQuery technicians,
            TechnicianEligibilityQuery eligibility,
            ManualServiceAssignmentService manualAssignments,
            DSLContext dsl,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.technicians = technicians;
        this.eligibility = eligibility;
        this.manualAssignments = manualAssignments;
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ManualServiceAssignmentReceipt assignTechnician(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            String technicianAssigneeId,
            String businessType
    ) {
        Objects.requireNonNull(taskId, "taskId");
        String techAssignee = requireText(technicianAssigneeId, "technicianAssigneeId", 128);
        String type = requireText(businessType, "businessType", 100);

        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        NetworkPortalTechnicianView technician = requireTechnicianOnNetwork(
                principal.tenantId(), networkId, techAssignee);
        Instant at = clock.instant();
        if (!eligibility.canAcceptAssignment(
                principal.tenantId(), technician.principalId(), networkId, at)) {
            String reason = eligibility.explainIneligibility(
                    principal.tenantId(), technician.principalId(), networkId, at);
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    reason == null ? "师傅当前不可接单" : "师傅当前不可接单：" + reason);
        }

        String networkAssigneeId = networkId.toString();
        // 以档案 ID 作为 TECHNICIAN assignee，与网点师傅目录稳定标识对齐
        String normalizedTechAssignee = technician.technicianProfileId().toString();
        rejectConflictingActiveAssignments(
                principal.tenantId(), taskId, networkAssigneeId, normalizedTechAssignee);

        return NetworkScopedDispatchAuthorization.callWith(networkAssigneeId, () ->
                manualAssignments.manualAssign(
                        principal,
                        metadata,
                        new ManualAssignServiceAssignmentCommand(
                                taskId, networkAssigneeId, normalizedTechAssignee, type)));
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

    /**
     * 预检 ACTIVE 责任冲突。不同网点 NETWORK / 不同师傅 TECHNICIAN 失败关闭；
     * 改派 saga 明确不在本切片。
     */
    private void rejectConflictingActiveAssignments(
            String tenantId, UUID taskId, String networkAssigneeId, String technicianAssigneeId
    ) {
        Optional<String> activeNetwork = activeAssignee(tenantId, taskId, "NETWORK");
        if (activeNetwork.isPresent() && !activeNetwork.get().equals(networkAssigneeId)) {
            throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "任务已有其他网点的 ACTIVE NETWORK 责任，Network Portal 不支持跨网点改派");
        }
        Optional<String> activeTech = activeAssignee(tenantId, taskId, "TECHNICIAN");
        if (activeTech.isPresent() && !activeTech.get().equals(technicianAssigneeId)) {
            throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "任务已有其他师傅的 ACTIVE TECHNICIAN 责任，改派不在本切片范围");
        }
    }

    private Optional<String> activeAssignee(String tenantId, UUID taskId, String level) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        return dsl.select(assignment.ASSIGNEE_ID)
                .from(assignment)
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.TASK_ID.eq(taskId))
                .and(assignment.RESPONSIBILITY_LEVEL.eq(level))
                .and(assignment.STATUS.eq("ACTIVE"))
                .orderBy(assignment.CREATED_AT)
                .limit(1)
                .fetchOptional(assignment.ASSIGNEE_ID);
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
}
