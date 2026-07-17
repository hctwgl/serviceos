package com.serviceos.authorization.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/** 授权治理只读端口。 */
public interface AuthorizationGovernanceQueryService {
    List<CapabilityView> listCapabilities(CurrentPrincipal actor, String correlationId);

    RolePage listRoles(CurrentPrincipal actor, String correlationId);

    RoleView getRole(CurrentPrincipal actor, String correlationId, UUID roleId);

    RoleGrantPage listRoleGrants(
            CurrentPrincipal actor, String correlationId, String principalId, String grantStatus
    );

    DelegationPage listDelegations(
            CurrentPrincipal actor, String correlationId, String delegatePrincipalId
    );
}
