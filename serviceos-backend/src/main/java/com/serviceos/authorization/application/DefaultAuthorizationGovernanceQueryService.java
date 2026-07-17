package com.serviceos.authorization.application;

import com.serviceos.authorization.api.AuthorizationGovernanceQueryService;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.CapabilityView;
import com.serviceos.authorization.api.DelegationPage;
import com.serviceos.authorization.api.RoleGrantPage;
import com.serviceos.authorization.api.RolePage;
import com.serviceos.authorization.api.RoleView;
import com.serviceos.authorization.domain.AuthRole;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** 授权治理查询：只认实时 Capability，不信任 JWT role 声明。 */
@Service
final class DefaultAuthorizationGovernanceQueryService implements AuthorizationGovernanceQueryService {
    private final AuthorizationGovernanceRepository directory;
    private final AuthorizationService authorization;
    private final Clock clock;

    DefaultAuthorizationGovernanceQueryService(
            AuthorizationGovernanceRepository directory,
            AuthorizationService authorization,
            Clock clock
    ) {
        this.directory = directory;
        this.authorization = authorization;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CapabilityView> listCapabilities(CurrentPrincipal actor, String correlationId) {
        require(actor, correlationId, "authorization.read", "capabilities");
        return directory.listCapabilities();
    }

    @Override
    @Transactional(readOnly = true)
    public RolePage listRoles(CurrentPrincipal actor, String correlationId) {
        require(actor, correlationId, "authorization.read", "roles");
        return new RolePage(
                directory.listRoles(actor.tenantId()).stream().map(AuthRole::toView).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public RoleView getRole(CurrentPrincipal actor, String correlationId, UUID roleId) {
        require(actor, correlationId, "authorization.read", roleId.toString());
        return directory.findRole(actor.tenantId(), roleId)
                .map(AuthRole::toView)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "角色不存在"));
    }

    @Override
    @Transactional(readOnly = true)
    public RoleGrantPage listRoleGrants(
            CurrentPrincipal actor, String correlationId, String principalId, String grantStatus
    ) {
        require(actor, correlationId, "authorization.read", "role-grants");
        return new RoleGrantPage(
                directory.listRoleGrants(actor.tenantId(), principalId, grantStatus).stream()
                        .map(grant -> grant.toView()).toList(),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public DelegationPage listDelegations(
            CurrentPrincipal actor, String correlationId, String delegatePrincipalId
    ) {
        require(actor, correlationId, "authorization.read", "delegations");
        return new DelegationPage(
                directory.listDelegations(actor.tenantId(), delegatePrincipalId).stream()
                        .map(delegation -> delegation.toView()).toList(),
                clock.instant());
    }

    private void require(CurrentPrincipal actor, String correlationId, String capability, String resourceId) {
        authorization.require(actor, AuthorizationRequest.tenantCapability(
                capability, actor.tenantId(), "AuthorizationGovernance", resourceId), correlationId);
    }
}
