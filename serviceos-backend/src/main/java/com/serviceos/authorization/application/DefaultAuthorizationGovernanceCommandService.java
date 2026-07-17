package com.serviceos.authorization.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationExplainResult;
import com.serviceos.authorization.api.AuthorizationGovernanceCommandService;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.DelegationView;
import com.serviceos.authorization.api.RoleGrantView;
import com.serviceos.authorization.api.RoleView;
import com.serviceos.authorization.domain.AuthRole;
import com.serviceos.authorization.domain.DelegationRecord;
import com.serviceos.authorization.domain.RoleGrantRecord;
import com.serviceos.identity.api.CurrentPrincipal;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 授权治理命令：授权 → 幂等 → 行锁/版本 → 追加事件 → 审计；
 * 撤销/批准生效时推进租户 grant generation，使依赖策略版本的上下文失败关闭。
 */
@Service
final class DefaultAuthorizationGovernanceCommandService implements AuthorizationGovernanceCommandService {
    private static final Set<String> SCOPE_TYPES = Set.of("TENANT", "PROJECT", "REGION", "NETWORK");
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private final AuthorizationGovernanceRepository directory;
    private final AuthorizationService authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultAuthorizationGovernanceCommandService(
            AuthorizationGovernanceRepository directory,
            AuthorizationService authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            Clock clock
    ) {
        this.directory = directory;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RoleView createRole(
            CurrentPrincipal actor, CommandMetadata metadata,
            String roleCode, String roleName, String description, List<String> capabilityCodes
    ) {
        roleCode = requireText(roleCode, "roleCode", 120);
        roleName = requireText(roleName, "roleName", 200);
        description = normalizeOptional(description, "description", 500);
        capabilityCodes = requireCapabilityCodes(capabilityCodes);
        var input = new CreateRoleInput(roleCode, roleName, description, capabilityCodes);
        CommandExecution execution = begin(actor, metadata, "authorization.createRole",
                "authorization.manageRoles", roleCode, input);
        if (execution.replay()) {
            UUID roleId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return directory.findRole(actor.tenantId(), roleId).orElseThrow().toView();
        }
        Instant now = clock.instant();
        UUID roleId = UUID.randomUUID();
        AuthRole role = new AuthRole(roleId, actor.tenantId(), roleCode, roleName,
                AuthRole.RoleKind.TENANT, "ACTIVE", description, capabilityCodes, 1, now, now);
        directory.insertRole(role);
        directory.insertRoleCapabilities(roleId, capabilityCodes, now);
        complete(actor, metadata, execution, roleId, 1, "ROLE_CREATED", "AuthRole",
                "authorization.manageRoles", null, now, false);
        return role.toView();
    }

    @Override
    @Transactional
    public RoleGrantView requestRoleGrant(
            CurrentPrincipal actor, CommandMetadata metadata,
            String principalId, UUID roleId, String scopeType, String scopeRef,
            String grantEffect, Instant validFrom, Instant validTo, String requestReason
    ) {
        principalId = requireText(principalId, "principalId", 128);
        scopeType = requireScopeType(scopeType);
        scopeRef = requireText(scopeRef, "scopeRef", 128);
        RoleGrantRecord.GrantEffect effect = requireEffect(grantEffect);
        requestReason = requireText(requestReason, "requestReason", 500);
        if (validFrom == null) {
            throw new IllegalArgumentException("validFrom is invalid");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo is invalid");
        }
        validateScopeRef(actor.tenantId(), scopeType, scopeRef);
        var input = new RequestGrantInput(principalId, roleId, scopeType, scopeRef, effect.name(),
                validFrom, validTo, requestReason);
        CommandExecution execution = begin(actor, metadata, "authorization.requestRoleGrant",
                "authorization.requestGrant", principalId, input);
        if (execution.replay()) {
            UUID grantId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return directory.findRoleGrant(actor.tenantId(), grantId).orElseThrow().toView();
        }
        AuthRole role = directory.findRole(actor.tenantId(), roleId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "角色不存在"));
        if (!"ACTIVE".equals(role.roleStatus())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "角色未启用");
        }
        Instant now = clock.instant();
        UUID grantId = UUID.randomUUID();
        RoleGrantRecord grant = new RoleGrantRecord(
                grantId, actor.tenantId(), principalId, roleId, role.roleCode(),
                scopeType, scopeRef, RoleGrantRecord.GrantStatus.PENDING_APPROVAL, effect,
                validFrom, validTo, "GOVERNANCE_REQUEST", null,
                actor.principalId(), requestReason, null, null, null, null, null,
                null, null, null, 1, now, now);
        directory.insertRoleGrant(grant);
        complete(actor, metadata, execution, grantId, 1, "ROLE_GRANT_REQUESTED", "RoleGrant",
                "authorization.requestGrant", requestReason, now, false);
        return grant.toView();
    }

    @Override
    @Transactional
    public RoleGrantView decideRoleGrant(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID grantId, long expectedVersion, String decision, String note
    ) {
        requireExpectedVersion(expectedVersion);
        String normalized = requireText(decision, "decision", 16).toUpperCase(Locale.ROOT);
        if (!normalized.equals("APPROVE") && !normalized.equals("REJECT")) {
            throw new IllegalArgumentException("decision is invalid");
        }
        note = normalizeOptional(note, "note", 500);
        var input = new DecideGrantInput(grantId, expectedVersion, normalized, note);
        CommandExecution execution = begin(actor, metadata, "authorization.decideRoleGrant",
                "authorization.approveGrant", grantId.toString(), input);
        if (execution.replay()) {
            return directory.findRoleGrant(actor.tenantId(), grantId).orElseThrow().toView();
        }
        RoleGrantRecord locked = directory.findRoleGrantForUpdate(actor.tenantId(), grantId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "授权不存在"));
        if (locked.version() != expectedVersion) {
            throw versionConflict();
        }
        if (locked.grantStatus() != RoleGrantRecord.GrantStatus.PENDING_APPROVAL) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "授权不在待审批状态");
        }
        Instant now = clock.instant();
        if (normalized.equals("APPROVE")) {
            enforceSoD(locked, actor.principalId());
            List<String> capabilities = directory.findRoleCapabilityCodes(locked.roleId());
            if (!directory.approverCoversCapabilities(actor.tenantId(), actor.principalId(),
                    capabilities, locked.scopeType(), locked.scopeRef(), now)) {
                throw new BusinessProblem(ProblemCode.ROLE_GRANT_ESCALATION_FORBIDDEN,
                        "审批者可授予范围不足以覆盖目标授权");
            }
            RoleGrantRecord approved = new RoleGrantRecord(
                    locked.id(), locked.tenantId(), locked.principalId(), locked.roleId(),
                    locked.roleCode(), locked.scopeType(), locked.scopeRef(),
                    RoleGrantRecord.GrantStatus.ACTIVE, locked.grantEffect(),
                    locked.validFrom(), locked.validTo(), locked.sourceCode(),
                    metadata.correlationId(), locked.requestedBy(), locked.requestReason(),
                    actor.principalId(), now, null, null, null,
                    null, null, null, locked.version() + 1, locked.createdAt(), now);
            if (!directory.updateRoleGrant(approved, expectedVersion)) {
                throw versionConflict();
            }
            complete(actor, metadata, execution, grantId, approved.version(), "ROLE_GRANT_APPROVED",
                    "RoleGrant", "authorization.approveGrant", note, now, true);
            return approved.toView();
        }
        String rejectReason = note == null ? "拒绝" : note;
        RoleGrantRecord rejected = new RoleGrantRecord(
                locked.id(), locked.tenantId(), locked.principalId(), locked.roleId(),
                locked.roleCode(), locked.scopeType(), locked.scopeRef(),
                RoleGrantRecord.GrantStatus.REJECTED, locked.grantEffect(),
                locked.validFrom(), locked.validTo(), locked.sourceCode(), locked.approvalRef(),
                locked.requestedBy(), locked.requestReason(), null, null,
                actor.principalId(), now, rejectReason,
                null, null, null, locked.version() + 1, locked.createdAt(), now);
        if (!directory.updateRoleGrant(rejected, expectedVersion)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, grantId, rejected.version(), "ROLE_GRANT_REJECTED",
                "RoleGrant", "authorization.approveGrant", rejectReason, now, false);
        return rejected.toView();
    }

    @Override
    @Transactional
    public RoleGrantView revokeRoleGrant(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID grantId, long expectedVersion, String reason
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        var input = new RevokeGrantInput(grantId, expectedVersion, reason);
        CommandExecution execution = begin(actor, metadata, "authorization.revokeRoleGrant",
                "authorization.revokeGrant", grantId.toString(), input);
        if (execution.replay()) {
            return directory.findRoleGrant(actor.tenantId(), grantId).orElseThrow().toView();
        }
        RoleGrantRecord locked = directory.findRoleGrantForUpdate(actor.tenantId(), grantId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "授权不存在"));
        if (locked.version() != expectedVersion) {
            throw versionConflict();
        }
        if (locked.grantStatus() != RoleGrantRecord.GrantStatus.ACTIVE
                && locked.grantStatus() != RoleGrantRecord.GrantStatus.PENDING_APPROVAL) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "授权不可撤销");
        }
        Instant now = clock.instant();
        RoleGrantRecord revoked = new RoleGrantRecord(
                locked.id(), locked.tenantId(), locked.principalId(), locked.roleId(),
                locked.roleCode(), locked.scopeType(), locked.scopeRef(),
                RoleGrantRecord.GrantStatus.REVOKED, locked.grantEffect(),
                locked.validFrom(), locked.validTo(), locked.sourceCode(), locked.approvalRef(),
                locked.requestedBy(), locked.requestReason(), locked.approvedBy(), locked.approvedAt(),
                locked.rejectedBy(), locked.rejectedAt(), locked.rejectReason(),
                now, actor.principalId(), reason, locked.version() + 1, locked.createdAt(), now);
        if (!directory.updateRoleGrant(revoked, expectedVersion)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, grantId, revoked.version(), "ROLE_GRANT_REVOKED",
                "RoleGrant", "authorization.revokeGrant", reason, now, true);
        return revoked.toView();
    }

    @Override
    @Transactional
    public DelegationView createDelegation(
            CurrentPrincipal actor, CommandMetadata metadata,
            String delegatePrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant validFrom, Instant validTo, String reason
    ) {
        delegatePrincipalId = requireText(delegatePrincipalId, "delegatePrincipalId", 128);
        capabilityCodes = requireCapabilityCodes(capabilityCodes);
        scopeType = requireScopeType(scopeType);
        scopeRef = requireText(scopeRef, "scopeRef", 128);
        reason = requireText(reason, "reason", 500);
        if (validFrom == null) {
            throw new IllegalArgumentException("validFrom is invalid");
        }
        if (validTo != null && !validTo.isAfter(validFrom)) {
            throw new IllegalArgumentException("validTo is invalid");
        }
        validateScopeRef(actor.tenantId(), scopeType, scopeRef);
        if (delegatePrincipalId.equals(actor.principalId())) {
            throw new IllegalArgumentException("delegatePrincipalId is invalid");
        }
        var input = new CreateDelegationInput(delegatePrincipalId, capabilityCodes, scopeType, scopeRef,
                validFrom, validTo, reason);
        CommandExecution execution = begin(actor, metadata, "authorization.createDelegation",
                "authorization.delegate", delegatePrincipalId, input);
        if (execution.replay()) {
            UUID delegationId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return directory.findDelegation(actor.tenantId(), delegationId).orElseThrow().toView();
        }
        Instant now = clock.instant();
        if (!directory.delegatorCoversDelegation(actor.tenantId(), actor.principalId(), capabilityCodes,
                scopeType, scopeRef, validFrom, validTo, now)) {
            throw new BusinessProblem(ProblemCode.DELEGATION_SCOPE_TOO_BROAD,
                    "委托能力、范围或期限超过委托人授权");
        }
        UUID delegationId = UUID.randomUUID();
        DelegationRecord delegation = new DelegationRecord(
                delegationId, actor.tenantId(), actor.principalId(), delegatePrincipalId,
                capabilityCodes, scopeType, scopeRef, validFrom, validTo, reason,
                DelegationRecord.Status.ACTIVE, 1, now, now, null, null, null);
        directory.insertDelegation(delegation);
        complete(actor, metadata, execution, delegationId, 1, "DELEGATION_CREATED", "Delegation",
                "authorization.delegate", reason, now, true);
        return delegation.toView();
    }

    @Override
    @Transactional
    public DelegationView revokeDelegation(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID delegationId, long expectedVersion, String reason
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        var input = new RevokeDelegationInput(delegationId, expectedVersion, reason);
        CommandExecution execution = begin(actor, metadata, "authorization.revokeDelegation",
                "authorization.delegate", delegationId.toString(), input);
        if (execution.replay()) {
            return directory.findDelegation(actor.tenantId(), delegationId).orElseThrow().toView();
        }
        DelegationRecord locked = directory.findDelegationForUpdate(actor.tenantId(), delegationId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "委托不存在"));
        if (locked.version() != expectedVersion) {
            throw versionConflict();
        }
        if (locked.status() != DelegationRecord.Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "委托不可撤销");
        }
        Instant now = clock.instant();
        DelegationRecord revoked = new DelegationRecord(
                locked.id(), locked.tenantId(), locked.delegatorPrincipalId(), locked.delegatePrincipalId(),
                locked.capabilityCodes(), locked.scopeType(), locked.scopeRef(),
                locked.validFrom(), locked.validTo(), locked.reason(),
                DelegationRecord.Status.REVOKED, locked.version() + 1, locked.createdAt(), now,
                now, actor.principalId(), reason);
        if (!directory.updateDelegation(revoked, expectedVersion)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, delegationId, revoked.version(), "DELEGATION_REVOKED",
                "Delegation", "authorization.delegate", reason, now, true);
        return revoked.toView();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthorizationExplainResult explain(
            CurrentPrincipal actor, String correlationId,
            String subjectPrincipalId, AuthorizationRequest request
    ) {
        subjectPrincipalId = requireText(subjectPrincipalId, "subjectPrincipalId", 128);
        AuthorizationDecision gate = authorization.require(actor, AuthorizationRequest.tenantCapability(
                "authorization.explain", actor.tenantId(), "AuthorizationExplain", subjectPrincipalId),
                correlationId);
        CurrentPrincipal subject = new CurrentPrincipal(
                subjectPrincipalId, actor.tenantId(), CurrentPrincipal.PrincipalType.USER,
                "authorization-explain", java.util.Set.of());
        AuthorizationRequest scoped = new AuthorizationRequest(
                request.capability(), actor.tenantId(), request.resourceType(), request.resourceId(),
                request.projectId(), request.organizationId(), request.regionCode(), request.networkId());
        AuthorizationDecision decision = authorization.authorize(subject, scoped, correlationId);
        String digest = Sha256.digest("explain|" + subjectPrincipalId + "|" + request.capability());
        audit.append(new AuditEntry(UUID.randomUUID(), actor.tenantId(), actor.principalId(),
                "AUTHORIZATION_EXPLAINED", "authorization.explain", "AuthorizationExplain",
                subjectPrincipalId, "ALLOW", gate.matchedGrantIds(), gate.policyVersion(),
                "SUCCEEDED", decision.effect().name(), digest, correlationId, clock.instant()));
        return new AuthorizationExplainResult(
                decision.effect().name(),
                decision.reasonCodes(),
                decision.matchedGrantIds(),
                decision.dataScopeExplanations(),
                decision.obligations(),
                decision.policyVersion());
    }

    private void enforceSoD(RoleGrantRecord grant, String approverPrincipalId) {
        if (directory.roleHasHighOrCriticalCapability(grant.roleId())
                && approverPrincipalId.equals(grant.requestedBy())) {
            throw new BusinessProblem(ProblemCode.ROLE_GRANT_DUTY_CONFLICT,
                    "高风险授权申请人不能自批");
        }
    }

    private CommandExecution begin(
            CurrentPrincipal actor, CommandMetadata metadata, String operation,
            String capability, String resourceId, Object input
    ) {
        AuthorizationDecision decision = authorization.require(actor, AuthorizationRequest.tenantCapability(
                capability, actor.tenantId(), "AuthorizationGovernance", resourceId),
                metadata.correlationId());
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        String requestDigest = Sha256.digest(canonicalJson(input));
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, operation, requestDigest);
        return new CommandExecution(operation, requestDigest, decision, idempotencyDecision);
    }

    private void complete(
            CurrentPrincipal actor, CommandMetadata metadata, CommandExecution execution,
            UUID resourceId, long resourceVersion, String eventType, String resourceType,
            String capability, String reason, Instant now, boolean bumpGeneration
    ) {
        directory.insertEvent(UUID.randomUUID(), actor.tenantId(), eventType, resourceType, resourceId,
                resourceVersion, reason, actor.principalId(), execution.requestDigest(),
                metadata.correlationId(), now);
        if (bumpGeneration) {
            directory.bumpGrantGeneration(actor.tenantId(), now);
        }
        audit.append(new AuditEntry(UUID.randomUUID(), actor.tenantId(), actor.principalId(),
                eventType, capability, resourceType, resourceId.toString(), "ALLOW",
                execution.authorization().matchedGrantIds(), execution.authorization().policyVersion(),
                "SUCCEEDED", null, execution.requestDigest(), metadata.correlationId(), now));
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        idempotency.complete(context, execution.operation(), resourceId.toString(),
                Sha256.digest(resourceId + "|" + resourceVersion));
    }

    private List<String> requireCapabilityCodes(List<String> capabilityCodes) {
        if (capabilityCodes == null || capabilityCodes.isEmpty()) {
            throw new IllegalArgumentException("capabilityCodes is invalid");
        }
        List<String> normalized = capabilityCodes.stream()
                .map(code -> requireText(code, "capabilityCode", 120))
                .distinct()
                .sorted()
                .toList();
        Set<String> existing = directory.findExistingCapabilityCodes(normalized);
        if (!existing.containsAll(new HashSet<>(normalized))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "只能组合稳定 Capability 目录中的能力");
        }
        return normalized;
    }

    private static void validateScopeRef(String tenantId, String scopeType, String scopeRef) {
        if ("TENANT".equals(scopeType) && !tenantId.equals(scopeRef)) {
            throw new IllegalArgumentException("scopeRef is invalid");
        }
    }

    private static String requireScopeType(String scopeType) {
        String normalized = requireText(scopeType, "scopeType", 32).toUpperCase(Locale.ROOT);
        if (!SCOPE_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("scopeType is invalid");
        }
        return normalized;
    }

    private static RoleGrantRecord.GrantEffect requireEffect(String grantEffect) {
        if (grantEffect == null || grantEffect.isBlank()) {
            return RoleGrantRecord.GrantEffect.ALLOW;
        }
        try {
            return RoleGrantRecord.GrantEffect.valueOf(requireText(grantEffect, "grantEffect", 16)
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("grantEffect is invalid", exception);
        }
    }

    private static void requireExpectedVersion(long version) {
        if (version < 1) {
            throw new IllegalArgumentException("expectedVersion must be positive");
        }
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String normalizeOptional(String value, String field, int max) {
        if (value == null) {
            return null;
        }
        return requireText(value, field, max);
    }

    private static BusinessProblem versionConflict() {
        return new BusinessProblem(ProblemCode.VERSION_CONFLICT, "聚合版本冲突");
    }

    private static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化幂等载荷", exception);
        }
    }

    private record CreateRoleInput(String roleCode, String roleName, String description, List<String> capabilityCodes) {
    }

    private record RequestGrantInput(
            String principalId, UUID roleId, String scopeType, String scopeRef, String grantEffect,
            Instant validFrom, Instant validTo, String requestReason
    ) {
    }

    private record DecideGrantInput(UUID grantId, long expectedVersion, String decision, String note) {
    }

    private record RevokeGrantInput(UUID grantId, long expectedVersion, String reason) {
    }

    private record CreateDelegationInput(
            String delegatePrincipalId, List<String> capabilityCodes, String scopeType, String scopeRef,
            Instant validFrom, Instant validTo, String reason
    ) {
    }

    private record RevokeDelegationInput(UUID delegationId, long expectedVersion, String reason) {
    }

    private record CommandExecution(
            String operation,
            String requestDigest,
            AuthorizationDecision authorization,
            IdempotencyDecision decision
    ) {
        boolean replay() {
            return decision.kind() == IdempotencyDecision.Kind.REPLAY;
        }
    }
}
