package com.serviceos.dispatch.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentReceipt;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
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
 * Network Portal 网点接单适配器。
 * <p>
 * 事务边界：预检与 ManualAssignNetwork 同事务。
 * 幂等键：HTTP Idempotency-Key 原样下传。
 * 失败关闭：伪造上下文、非成员、其他网点已 ACTIVE NETWORK。
 */
@Service
final class JooqNetworkPortalAcceptAssignmentService implements NetworkPortalAcceptAssignmentService {
    private static final String CAPABILITY = "networkPortal.acceptAssignment";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final ManualServiceAssignmentService manualAssignments;
    private final DSLContext dsl;
    private final Clock clock;

    JooqNetworkPortalAcceptAssignmentService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            ManualServiceAssignmentService manualAssignments,
            DSLContext dsl,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.manualAssignments = manualAssignments;
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NetworkPortalAcceptAssignmentReceipt acceptAssignment(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            String businessType
    ) {
        Objects.requireNonNull(taskId, "taskId");
        String type = requireText(businessType, "businessType", 100);
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        String networkAssigneeId = networkId.toString();
        rejectForeignActiveNetwork(principal.tenantId(), taskId, networkAssigneeId);

        return NetworkScopedDispatchAuthorization.callWith(networkAssigneeId, () ->
                manualAssignments.manualAssignNetwork(
                        principal, metadata, taskId, networkAssigneeId, type));
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

    private void rejectForeignActiveNetwork(String tenantId, UUID taskId, String networkAssigneeId) {
        DspServiceAssignment assignment = DSP_SERVICE_ASSIGNMENT;
        Optional<String> activeNetwork = dsl.select(assignment.ASSIGNEE_ID)
                .from(assignment)
                .where(assignment.TENANT_ID.eq(tenantId))
                .and(assignment.TASK_ID.eq(taskId))
                .and(assignment.RESPONSIBILITY_LEVEL.eq("NETWORK"))
                .and(assignment.STATUS.eq("ACTIVE"))
                .orderBy(assignment.CREATED_AT)
                .limit(1)
                .fetchOptional(assignment.ASSIGNEE_ID);
        if (activeNetwork.isPresent() && !activeNetwork.get().equals(networkAssigneeId)) {
            throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "任务已有其他网点的 ACTIVE NETWORK 责任，Network Portal 不支持跨网点接单");
        }
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
