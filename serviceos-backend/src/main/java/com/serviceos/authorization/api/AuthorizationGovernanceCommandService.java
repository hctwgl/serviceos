package com.serviceos.authorization.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 授权治理写命令端口。 */
public interface AuthorizationGovernanceCommandService {
    RoleView createRole(
            CurrentPrincipal actor, CommandMetadata metadata,
            String roleCode, String roleName, String description, List<String> capabilityCodes
    );

    RoleGrantView requestRoleGrant(
            CurrentPrincipal actor, CommandMetadata metadata,
            String principalId, UUID roleId, String scopeType, String scopeRef,
            String grantEffect, Instant validFrom, Instant validTo, String requestReason
    );

    RoleGrantView decideRoleGrant(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID grantId, long expectedVersion, String decision, String note
    );

    RoleGrantView revokeRoleGrant(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID grantId, long expectedVersion, String reason
    );

    DelegationView createDelegation(
            CurrentPrincipal actor, CommandMetadata metadata,
            String delegatePrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant validFrom, Instant validTo, String reason
    );

    DelegationView revokeDelegation(
            CurrentPrincipal actor, CommandMetadata metadata,
            UUID delegationId, long expectedVersion, String reason
    );

    AuthorizationExplainResult explain(
            CurrentPrincipal actor, String correlationId,
            String subjectPrincipalId, AuthorizationRequest request
    );
}
