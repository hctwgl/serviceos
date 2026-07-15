package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.ProjectRegionScopeResolver;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * RoleGrant 项目集合解析器。TENANT 表示租户内全部项目；PROJECT 与 REGION 权威映射取并集。
 * NETWORK 尚无权威项目映射，因此只有该范围时不能猜测或扩大权限，必须拒绝并保留审计。
 */
@Service
final class DefaultProjectScopeAuthorizationService implements ProjectScopeAuthorizationService {
    static final String PROJECT_SCOPE_MISSING = "PROJECT_SCOPE_MISSING";
    static final String PROJECT_SCOPE_UNRESOLVED = "PROJECT_SCOPE_UNRESOLVED";

    private final ProjectScopePolicyStore policyStore;
    private final ProjectRegionScopeResolver regionScopes;
    private final AuthorizationDenialAuditWriter denialAudit;
    private final Clock clock;

    DefaultProjectScopeAuthorizationService(
            ProjectScopePolicyStore policyStore,
            ProjectRegionScopeResolver regionScopes,
            AuthorizationDenialAuditWriter denialAudit,
            Clock clock
    ) {
        this.policyStore = policyStore;
        this.regionScopes = regionScopes;
        this.denialAudit = denialAudit;
        this.clock = clock;
    }

    @Override
    public AuthorizedProjectScope require(
            CurrentPrincipal principal, String capability, String resourceType, String correlationId
    ) {
        ProjectScopeGrantMatch match = policyStore.findProjectScopeGrants(
                principal.tenantId(), principal.principalId(), capability, clock.instant());
        boolean tenantWide = false;
        boolean unresolved = false;
        Set<UUID> projects = new LinkedHashSet<>();
        Set<String> regions = new LinkedHashSet<>();
        for (String scope : match.scopeTypesAndRefs()) {
            int separator = scope.indexOf(':');
            if (separator < 1 || separator == scope.length() - 1) {
                throw new IllegalStateException("RoleGrant scope is malformed");
            }
            String type = scope.substring(0, separator);
            String reference = scope.substring(separator + 1);
            switch (type) {
                case "TENANT" -> tenantWide = true;
                case "PROJECT" -> projects.add(parseProject(reference));
                case "REGION" -> regions.add(reference);
                case "NETWORK" -> unresolved = true;
                default -> throw new IllegalStateException("RoleGrant scope type is unsupported: " + type);
            }
        }
        if (tenantWide) {
            return new AuthorizedProjectScope(true, Set.of(), Sha256.digest("TENANT:*"));
        }
        if (!regions.isEmpty()) {
            projects.addAll(regionScopes.resolve(principal.tenantId(), regions, clock.instant()));
        }
        if (!projects.isEmpty()) {
            String canonical = projects.stream().sorted(Comparator.comparing(UUID::toString))
                    .map(UUID::toString).reduce((left, right) -> left + "," + right).orElseThrow();
            return new AuthorizedProjectScope(false, projects, Sha256.digest("PROJECTS:" + canonical));
        }
        deny(principal, capability, resourceType, correlationId,
                unresolved ? PROJECT_SCOPE_UNRESOLVED : PROJECT_SCOPE_MISSING, match.policyVersion());
        throw new IllegalStateException("unreachable");
    }

    private void deny(
            CurrentPrincipal principal,
            String capability,
            String resourceType,
            String correlationId,
            String reason,
            String policyVersion
    ) {
        denialAudit.append(principal, AuthorizationRequest.tenantCapability(
                capability, principal.tenantId(), resourceType, "collection"),
                correlationId, reason, policyVersion);
        throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed");
    }

    private static UUID parseProject(String reference) {
        try {
            return UUID.fromString(reference);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("PROJECT RoleGrant scope_ref is not a UUID", exception);
        }
    }
}
