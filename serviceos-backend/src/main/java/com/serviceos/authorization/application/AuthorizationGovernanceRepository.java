package com.serviceos.authorization.application;

import com.serviceos.authorization.api.CapabilityView;
import com.serviceos.authorization.domain.AuthRole;
import com.serviceos.authorization.domain.DelegationRecord;
import com.serviceos.authorization.domain.RoleGrantRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 授权治理持久化端口。 */
public interface AuthorizationGovernanceRepository {
    List<CapabilityView> listCapabilities();

    Optional<CapabilityView> findCapability(String capabilityCode);

    Set<String> findExistingCapabilityCodes(List<String> capabilityCodes);

    List<String> findRoleCapabilityCodes(UUID roleId);

    List<AuthRole> listRoles(String tenantId);

    Optional<AuthRole> findRole(String tenantId, UUID roleId);

    Optional<AuthRole> findRoleForUpdate(String tenantId, UUID roleId);

    void insertRole(AuthRole role);

    void insertRoleCapabilities(UUID roleId, List<String> capabilityCodes, Instant grantedAt);

    List<RoleGrantRecord> listRoleGrants(String tenantId, String principalId, String grantStatus);

    Optional<RoleGrantRecord> findRoleGrant(String tenantId, UUID grantId);

    Optional<RoleGrantRecord> findRoleGrantForUpdate(String tenantId, UUID grantId);

    void insertRoleGrant(RoleGrantRecord grant);

    boolean updateRoleGrant(RoleGrantRecord grant, long expectedVersion);

    List<DelegationRecord> listDelegations(String tenantId, String delegatePrincipalId);

    Optional<DelegationRecord> findDelegation(String tenantId, UUID delegationId);

    Optional<DelegationRecord> findDelegationForUpdate(String tenantId, UUID delegationId);

    void insertDelegation(DelegationRecord delegation);

    boolean updateDelegation(DelegationRecord delegation, long expectedVersion);

    /**
     * 审批者是否对每个 capability 持有覆盖目标范围的 ACTIVE ALLOW（含委托合成）。
     */
    boolean approverCoversCapabilities(
            String tenantId, String approverPrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant evaluatedAt
    );

    /**
     * 委托人是否对每个 capability 持有覆盖目标范围与期限的 ACTIVE ALLOW。
     */
    boolean delegatorCoversDelegation(
            String tenantId, String delegatorPrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant validFrom, Instant validTo, Instant evaluatedAt
    );

    boolean roleHasHighOrCriticalCapability(UUID roleId);

    void insertEvent(
            UUID eventId, String tenantId, String eventType, String resourceType, UUID resourceId,
            long resourceVersion, String reason, String actorId, String requestDigest,
            String correlationId, Instant occurredAt
    );

    long bumpGrantGeneration(String tenantId, Instant updatedAt);

    long currentGrantGeneration(String tenantId);
}
