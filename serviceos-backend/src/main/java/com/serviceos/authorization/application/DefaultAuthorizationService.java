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
 * E1 授权基线：先实施租户硬边界和稳定 capability。
 * 项目、区域、网点、参与关系和字段策略将在相同接口下逐步接入权威 grant/policy 数据。
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
        CapabilityGrantMatch grants = policyStore.findTenantCapabilityGrants(
                request.tenantId(), principal.principalId(), request.capability(), clock.instant());
        if (!grants.allowed()) {
            return AuthorizationDecision.deny(CAPABILITY_MISSING, grants.policyVersion());
        }
        return new AuthorizationDecision(
                AuthorizationDecision.Effect.ALLOW,
                java.util.List.of(), grants.matchedGrantIds(), java.util.List.of(), grants.policyVersion());
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
