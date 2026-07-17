package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.IdentityAuthorizationEvidence;
import com.serviceos.identity.api.IdentityAuthorizationPort;
import org.springframework.stereotype.Component;

/** identity 用例的授权适配器；所有判定仍复用 authorization 的实时 RoleGrant 与拒绝审计。 */
@Component
final class IdentityAuthorizationAdapter implements IdentityAuthorizationPort {
    private final AuthorizationService authorization;

    IdentityAuthorizationAdapter(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Override
    public IdentityAuthorizationEvidence requireTenantCapability(
            CurrentPrincipal principal, String capability, String resourceId, String correlationId
    ) {
        var decision = authorization.require(principal, AuthorizationRequest.tenantCapability(
                capability, principal.tenantId(), "SecurityPrincipal", resourceId), correlationId);
        return new IdentityAuthorizationEvidence(decision.matchedGrantIds(), decision.policyVersion());
    }
}
