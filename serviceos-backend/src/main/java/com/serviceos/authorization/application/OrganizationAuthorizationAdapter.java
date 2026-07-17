package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.organization.api.OrganizationAuthorizationEvidence;
import com.serviceos.organization.api.OrganizationAuthorizationPort;
import org.springframework.stereotype.Component;

/** organization 用例的授权适配器；判定仍复用 authorization 的实时 RoleGrant 与拒绝审计。 */
@Component
final class OrganizationAuthorizationAdapter implements OrganizationAuthorizationPort {
    private final AuthorizationService authorization;

    OrganizationAuthorizationAdapter(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Override
    public OrganizationAuthorizationEvidence requireTenantCapability(
            CurrentPrincipal principal, String capability, String resourceId, String correlationId
    ) {
        var decision = authorization.require(principal, AuthorizationRequest.tenantCapability(
                capability, principal.tenantId(), "Organization", resourceId), correlationId);
        return new OrganizationAuthorizationEvidence(decision.matchedGrantIds(), decision.policyVersion());
    }
}
