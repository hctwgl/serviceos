package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.time.Clock;

/**
 * E1 授权内核：租户是不可跨越的硬边界；有效 RoleGrant 再按租户、项目、区域或网点范围匹配。
 */
@Service
final class DefaultAuthorizationService implements AuthorizationService {
    static final String TENANT_SCOPE_MISMATCH = "TENANT_SCOPE_MISMATCH";
    static final String CAPABILITY_MISSING = "CAPABILITY_MISSING";

    private final AuthorizationDenialAuditWriter denialAudit;
    private final AuthorizationPolicyStore policyStore;
    private final Clock clock;

    DefaultAuthorizationService(
            AuthorizationDenialAuditWriter denialAudit,
            AuthorizationPolicyStore policyStore,
            Clock clock
    ) {
        this.denialAudit = denialAudit;
        this.policyStore = policyStore;
        this.clock = clock;
    }

    @Override
    public AuthorizationDecision authorize(
            CurrentPrincipal principal,
            AuthorizationRequest request,
            String correlationId
    ) {
        if (!principal.tenantId().equals(request.tenantId())) {
            return AuthorizationDecision.deny(TENANT_SCOPE_MISMATCH, "tenant-boundary-v1");
        }
        CapabilityGrantMatch grants = policyStore.findCapabilityGrants(
                request.tenantId(), principal.principalId(), request, clock.instant());
        if (!grants.allowed()) {
            return AuthorizationDecision.deny(CAPABILITY_MISSING, grants.policyVersion());
        }
        return new AuthorizationDecision(
                AuthorizationDecision.Effect.ALLOW,
                java.util.List.of(), grants.matchedGrantIds(), grants.matchedScopeExplanations(),
                java.util.List.of(), grants.policyVersion());
    }

    @Override
    public AuthorizationDecision require(
            CurrentPrincipal principal,
            AuthorizationRequest request,
            String correlationId
    ) {
        AuthorizationDecision decision = authorize(principal, request, correlationId);
        if (decision.effect() == AuthorizationDecision.Effect.ALLOW) {
            return decision;
        }

        String reason = decision.reasonCodes().getFirst();
        denialAudit.append(principal, request, correlationId, reason, decision.policyVersion());
        throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed");
    }
}
