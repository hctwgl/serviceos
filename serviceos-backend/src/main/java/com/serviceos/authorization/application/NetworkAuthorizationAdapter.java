package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkAuthorizationEvidence;
import com.serviceos.network.api.NetworkAuthorizationPort;
import org.springframework.stereotype.Component;

/** network 用例的授权适配器；判定仍复用 authorization 的实时 RoleGrant 与拒绝审计。 */
@Component
final class NetworkAuthorizationAdapter implements NetworkAuthorizationPort {
    private final AuthorizationService authorization;

    NetworkAuthorizationAdapter(AuthorizationService authorization) {
        this.authorization = authorization;
    }

    @Override
    public NetworkAuthorizationEvidence requireTenantCapability(
            CurrentPrincipal principal, String capability, String resourceId, String correlationId
    ) {
        var decision = authorization.require(principal, AuthorizationRequest.tenantCapability(
                capability, principal.tenantId(), "Network", resourceId), correlationId);
        return new NetworkAuthorizationEvidence(decision.matchedGrantIds(), decision.policyVersion());
    }

    @Override
    public NetworkAuthorizationEvidence requireNetworkCapability(
            CurrentPrincipal principal, String capability, String networkId,
            String resourceId, String correlationId
    ) {
        var decision = authorization.require(principal, AuthorizationRequest.networkCapability(
                capability, principal.tenantId(), "ServiceNetwork", resourceId, networkId), correlationId);
        return new NetworkAuthorizationEvidence(decision.matchedGrantIds(), decision.policyVersion());
    }
}
