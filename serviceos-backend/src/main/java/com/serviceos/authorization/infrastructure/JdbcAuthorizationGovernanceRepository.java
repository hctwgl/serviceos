package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.CapabilityView;
import com.serviceos.authorization.application.AuthorizationGovernanceRepository;
import com.serviceos.authorization.domain.AuthRole;
import com.serviceos.authorization.domain.DelegationRecord;
import com.serviceos.authorization.domain.RoleGrantRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 授权治理 JDBC 适配器。可授予范围与委托子集校验在 SQL 中失败关闭，禁止只信应用层集合差。
 */
@Repository
final class JdbcAuthorizationGovernanceRepository implements AuthorizationGovernanceRepository {
    private static final String ROLE_SELECT = """
            SELECT r.role_id, r.tenant_id, r.role_code, r.role_name, r.role_kind, r.role_status,
                   r.description, r.aggregate_version, r.created_at, r.updated_at
              FROM auth_role r
            """;
    private static final String GRANT_SELECT = """
            SELECT g.grant_id, g.tenant_id, g.principal_id, g.role_id, r.role_code,
                   g.scope_type, g.scope_ref, g.grant_status, g.grant_effect,
                   g.valid_from, g.valid_to, g.source_code, g.approval_ref,
                   g.requested_by, g.request_reason, g.approved_by, g.approved_at,
                   g.rejected_by, g.rejected_at, g.reject_reason,
                   g.revoked_at, g.revoked_by, g.revoke_reason,
                   g.aggregate_version, g.created_at, g.updated_at
              FROM auth_role_grant g
              JOIN auth_role r ON r.role_id = g.role_id
            """;
    private static final String DELEGATION_SELECT = """
            SELECT d.delegation_id, d.tenant_id, d.delegator_principal_id, d.delegate_principal_id,
                   d.scope_type, d.scope_ref, d.valid_from, d.valid_to, d.reason,
                   d.delegation_status, d.aggregate_version, d.created_at, d.updated_at,
                   d.revoked_at, d.revoked_by, d.revoke_reason
              FROM auth_delegation d
            """;

    private final JdbcClient jdbc;

    JdbcAuthorizationGovernanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CapabilityView> listCapabilities() {
        return jdbc.sql("""
                        SELECT capability_code, capability_name, risk_level
                          FROM auth_capability
                         ORDER BY capability_code
                        """)
                .query((rs, rowNum) -> new CapabilityView(
                        rs.getString("capability_code"),
                        rs.getString("capability_name"),
                        rs.getString("risk_level")))
                .list();
    }

    @Override
    public Optional<CapabilityView> findCapability(String capabilityCode) {
        return jdbc.sql("""
                        SELECT capability_code, capability_name, risk_level
                          FROM auth_capability
                         WHERE capability_code = :code
                        """)
                .param("code", capabilityCode)
                .query((rs, rowNum) -> new CapabilityView(
                        rs.getString("capability_code"),
                        rs.getString("capability_name"),
                        rs.getString("risk_level")))
                .optional();
    }

    @Override
    public Set<String> findExistingCapabilityCodes(List<String> capabilityCodes) {
        if (capabilityCodes.isEmpty()) {
            return Set.of();
        }
        List<String> found = jdbc.sql("""
                        SELECT capability_code
                          FROM auth_capability
                         WHERE capability_code IN (:codes)
                        """)
                .param("codes", capabilityCodes)
                .query(String.class)
                .list();
        return new HashSet<>(found);
    }

    @Override
    public List<String> findRoleCapabilityCodes(UUID roleId) {
        return jdbc.sql("""
                        SELECT capability_code
                          FROM auth_role_capability
                         WHERE role_id = :roleId
                         ORDER BY capability_code
                        """)
                .param("roleId", roleId)
                .query(String.class)
                .list();
    }

    @Override
    public List<AuthRole> listRoles(String tenantId) {
        List<AuthRole> roles = jdbc.sql(ROLE_SELECT + " WHERE r.tenant_id = :tenant ORDER BY r.role_code")
                .param("tenant", tenantId)
                .query(this::mapRoleWithoutCapabilities)
                .list();
        return roles.stream().map(role -> withCapabilities(role)).toList();
    }

    @Override
    public Optional<AuthRole> findRole(String tenantId, UUID roleId) {
        return roleQuery(tenantId, roleId, false);
    }

    @Override
    public Optional<AuthRole> findRoleForUpdate(String tenantId, UUID roleId) {
        return roleQuery(tenantId, roleId, true);
    }

    private Optional<AuthRole> roleQuery(String tenantId, UUID roleId, boolean forUpdate) {
        return jdbc.sql(ROLE_SELECT + " WHERE r.tenant_id = :tenant AND r.role_id = :id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId)
                .param("id", roleId)
                .query(this::mapRoleWithoutCapabilities)
                .optional()
                .map(this::withCapabilities);
    }

    @Override
    public void insertRole(AuthRole role) {
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, role_kind,
                            description, aggregate_version, created_at, updated_at
                        ) VALUES (
                            :id, :tenant, :code, :name, :status, :kind,
                            :description, :version, :createdAt, :updatedAt
                        )
                        """)
                .param("id", role.id())
                .param("tenant", role.tenantId())
                .param("code", role.roleCode())
                .param("name", role.roleName())
                .param("status", role.roleStatus())
                .param("kind", role.roleKind().name())
                .param("description", role.description())
                .param("version", role.version())
                .param("createdAt", Timestamp.from(role.createdAt()))
                .param("updatedAt", Timestamp.from(role.updatedAt()))
                .update();
    }

    @Override
    public void insertRoleCapabilities(UUID roleId, List<String> capabilityCodes, Instant grantedAt) {
        for (String code : capabilityCodes) {
            jdbc.sql("""
                            INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                            VALUES (:roleId, :code, :grantedAt)
                            """)
                    .param("roleId", roleId)
                    .param("code", code)
                    .param("grantedAt", Timestamp.from(grantedAt))
                    .update();
        }
    }

    @Override
    public List<RoleGrantRecord> listRoleGrants(String tenantId, String principalId, String grantStatus) {
        StringBuilder sql = new StringBuilder(GRANT_SELECT)
                .append(" WHERE g.tenant_id = :tenant");
        if (principalId != null) {
            sql.append(" AND g.principal_id = :principalId");
        }
        if (grantStatus != null) {
            sql.append(" AND g.grant_status = :grantStatus");
        }
        sql.append(" ORDER BY g.created_at DESC, g.grant_id");
        var query = jdbc.sql(sql.toString()).param("tenant", tenantId);
        if (principalId != null) {
            query = query.param("principalId", principalId);
        }
        if (grantStatus != null) {
            query = query.param("grantStatus", grantStatus);
        }
        return query.query(this::mapGrant).list();
    }

    @Override
    public Optional<RoleGrantRecord> findRoleGrant(String tenantId, UUID grantId) {
        return grantQuery(tenantId, grantId, false);
    }

    @Override
    public Optional<RoleGrantRecord> findRoleGrantForUpdate(String tenantId, UUID grantId) {
        return grantQuery(tenantId, grantId, true);
    }

    private Optional<RoleGrantRecord> grantQuery(String tenantId, UUID grantId, boolean forUpdate) {
        return jdbc.sql(GRANT_SELECT + " WHERE g.tenant_id = :tenant AND g.grant_id = :id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId)
                .param("id", grantId)
                .query(this::mapGrant)
                .optional();
    }

    @Override
    public void insertRoleGrant(RoleGrantRecord grant) {
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, valid_to, source_code, approval_ref, grant_status, grant_effect,
                            requested_by, request_reason, approved_by, approved_at,
                            rejected_by, rejected_at, reject_reason,
                            revoked_at, revoked_by, revoke_reason,
                            aggregate_version, created_at, updated_at
                        ) VALUES (
                            :id, :tenant, :principalId, :roleId, :scopeType, :scopeRef,
                            :validFrom, :validTo, :sourceCode, :approvalRef, :status, :effect,
                            :requestedBy, :requestReason, :approvedBy, :approvedAt,
                            :rejectedBy, :rejectedAt, :rejectReason,
                            :revokedAt, :revokedBy, :revokeReason,
                            :version, :createdAt, :updatedAt
                        )
                        """)
                .param("id", grant.id())
                .param("tenant", grant.tenantId())
                .param("principalId", grant.principalId())
                .param("roleId", grant.roleId())
                .param("scopeType", grant.scopeType())
                .param("scopeRef", grant.scopeRef())
                .param("validFrom", Timestamp.from(grant.validFrom()))
                .param("validTo", grant.validTo() == null ? null : Timestamp.from(grant.validTo()))
                .param("sourceCode", grant.sourceCode())
                .param("approvalRef", grant.approvalRef())
                .param("status", grant.grantStatus().name())
                .param("effect", grant.grantEffect().name())
                .param("requestedBy", grant.requestedBy())
                .param("requestReason", grant.requestReason())
                .param("approvedBy", grant.approvedBy())
                .param("approvedAt", grant.approvedAt() == null ? null : Timestamp.from(grant.approvedAt()))
                .param("rejectedBy", grant.rejectedBy())
                .param("rejectedAt", grant.rejectedAt() == null ? null : Timestamp.from(grant.rejectedAt()))
                .param("rejectReason", grant.rejectReason())
                .param("revokedAt", grant.revokedAt() == null ? null : Timestamp.from(grant.revokedAt()))
                .param("revokedBy", grant.revokedBy())
                .param("revokeReason", grant.revokeReason())
                .param("version", grant.version())
                .param("createdAt", Timestamp.from(grant.createdAt()))
                .param("updatedAt", Timestamp.from(grant.updatedAt()))
                .update();
    }

    @Override
    public boolean updateRoleGrant(RoleGrantRecord grant, long expectedVersion) {
        int updated = jdbc.sql("""
                        UPDATE auth_role_grant
                           SET grant_status = :status,
                               approved_by = :approvedBy,
                               approved_at = :approvedAt,
                               rejected_by = :rejectedBy,
                               rejected_at = :rejectedAt,
                               reject_reason = :rejectReason,
                               approval_ref = :approvalRef,
                               revoked_at = :revokedAt,
                               revoked_by = :revokedBy,
                               revoke_reason = :revokeReason,
                               aggregate_version = :version,
                               updated_at = :updatedAt
                         WHERE tenant_id = :tenant
                           AND grant_id = :id
                           AND aggregate_version = :expected
                        """)
                .param("status", grant.grantStatus().name())
                .param("approvedBy", grant.approvedBy())
                .param("approvedAt", grant.approvedAt() == null ? null : Timestamp.from(grant.approvedAt()))
                .param("rejectedBy", grant.rejectedBy())
                .param("rejectedAt", grant.rejectedAt() == null ? null : Timestamp.from(grant.rejectedAt()))
                .param("rejectReason", grant.rejectReason())
                .param("approvalRef", grant.approvalRef())
                .param("revokedAt", grant.revokedAt() == null ? null : Timestamp.from(grant.revokedAt()))
                .param("revokedBy", grant.revokedBy())
                .param("revokeReason", grant.revokeReason())
                .param("version", grant.version())
                .param("updatedAt", Timestamp.from(grant.updatedAt()))
                .param("tenant", grant.tenantId())
                .param("id", grant.id())
                .param("expected", expectedVersion)
                .update();
        return updated == 1;
    }

    @Override
    public List<DelegationRecord> listDelegations(String tenantId, String delegatePrincipalId) {
        StringBuilder sql = new StringBuilder(DELEGATION_SELECT)
                .append(" WHERE d.tenant_id = :tenant");
        if (delegatePrincipalId != null) {
            sql.append(" AND d.delegate_principal_id = :delegate");
        }
        sql.append(" ORDER BY d.created_at DESC, d.delegation_id");
        var query = jdbc.sql(sql.toString()).param("tenant", tenantId);
        if (delegatePrincipalId != null) {
            query = query.param("delegate", delegatePrincipalId);
        }
        return query.query(this::mapDelegationWithoutCapabilities).list().stream()
                .map(this::withDelegationCapabilities)
                .toList();
    }

    @Override
    public Optional<DelegationRecord> findDelegation(String tenantId, UUID delegationId) {
        return delegationQuery(tenantId, delegationId, false);
    }

    @Override
    public Optional<DelegationRecord> findDelegationForUpdate(String tenantId, UUID delegationId) {
        return delegationQuery(tenantId, delegationId, true);
    }

    private Optional<DelegationRecord> delegationQuery(String tenantId, UUID delegationId, boolean forUpdate) {
        return jdbc.sql(DELEGATION_SELECT + " WHERE d.tenant_id = :tenant AND d.delegation_id = :id"
                        + (forUpdate ? " FOR UPDATE" : ""))
                .param("tenant", tenantId)
                .param("id", delegationId)
                .query(this::mapDelegationWithoutCapabilities)
                .optional()
                .map(this::withDelegationCapabilities);
    }

    @Override
    public void insertDelegation(DelegationRecord delegation) {
        jdbc.sql("""
                        INSERT INTO auth_delegation (
                            delegation_id, tenant_id, delegator_principal_id, delegate_principal_id,
                            scope_type, scope_ref, valid_from, valid_to, reason, delegation_status,
                            created_by, created_at, revoked_by, revoked_at, revoke_reason,
                            aggregate_version, updated_at
                        ) VALUES (
                            :id, :tenant, :delegator, :delegate,
                            :scopeType, :scopeRef, :validFrom, :validTo, :reason, :status,
                            :createdBy, :createdAt, NULL, NULL, NULL,
                            :version, :updatedAt
                        )
                        """)
                .param("id", delegation.id())
                .param("tenant", delegation.tenantId())
                .param("delegator", delegation.delegatorPrincipalId())
                .param("delegate", delegation.delegatePrincipalId())
                .param("scopeType", delegation.scopeType())
                .param("scopeRef", delegation.scopeRef())
                .param("validFrom", Timestamp.from(delegation.validFrom()))
                .param("validTo", delegation.validTo() == null ? null : Timestamp.from(delegation.validTo()))
                .param("reason", delegation.reason())
                .param("status", delegation.status().name())
                .param("createdBy", delegation.delegatorPrincipalId())
                .param("createdAt", Timestamp.from(delegation.createdAt()))
                .param("version", delegation.version())
                .param("updatedAt", Timestamp.from(delegation.updatedAt()))
                .update();
        for (String code : delegation.capabilityCodes()) {
            jdbc.sql("""
                            INSERT INTO auth_delegation_capability (delegation_id, capability_code)
                            VALUES (:id, :code)
                            """)
                    .param("id", delegation.id())
                    .param("code", code)
                    .update();
        }
    }

    @Override
    public boolean updateDelegation(DelegationRecord delegation, long expectedVersion) {
        int updated = jdbc.sql("""
                        UPDATE auth_delegation
                           SET delegation_status = :status,
                               revoked_by = :revokedBy,
                               revoked_at = :revokedAt,
                               revoke_reason = :revokeReason,
                               aggregate_version = :version,
                               updated_at = :updatedAt
                         WHERE tenant_id = :tenant
                           AND delegation_id = :id
                           AND aggregate_version = :expected
                        """)
                .param("status", delegation.status().name())
                .param("revokedBy", delegation.revokedBy())
                .param("revokedAt", delegation.revokedAt() == null ? null : Timestamp.from(delegation.revokedAt()))
                .param("revokeReason", delegation.revokeReason())
                .param("version", delegation.version())
                .param("updatedAt", Timestamp.from(delegation.updatedAt()))
                .param("tenant", delegation.tenantId())
                .param("id", delegation.id())
                .param("expected", expectedVersion)
                .update();
        return updated == 1;
    }

    @Override
    public boolean approverCoversCapabilities(
            String tenantId, String approverPrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant evaluatedAt
    ) {
        for (String capability : capabilityCodes) {
            if (!hasCoveringAllow(tenantId, approverPrincipalId, capability, scopeType, scopeRef,
                    evaluatedAt, null, null)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean delegatorCoversDelegation(
            String tenantId, String delegatorPrincipalId, List<String> capabilityCodes,
            String scopeType, String scopeRef, Instant validFrom, Instant validTo, Instant evaluatedAt
    ) {
        for (String capability : capabilityCodes) {
            if (!hasCoveringAllow(tenantId, delegatorPrincipalId, capability, scopeType, scopeRef,
                    evaluatedAt, validFrom, validTo)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 覆盖规则：TENANT 范围覆盖同租户任意更窄范围；同类型同引用精确覆盖。
     * 委托期限必须落在委托人有效授权区间内（valid_from ≤ 委托起；委托人 valid_to 为空或 ≥ 委托止）。
     * 不向 JDBC 绑定空 timestamptz，避免 PostgreSQL 无法推断参数类型。
     */
    private boolean hasCoveringAllow(
            String tenantId, String principalId, String capability,
            String scopeType, String scopeRef, Instant evaluatedAt,
            Instant requiredFrom, Instant requiredTo
    ) {
        String periodClause;
        if (requiredFrom == null && requiredTo == null) {
            periodClause = "";
        } else if (requiredFrom != null && requiredTo == null) {
            periodClause = " AND g.valid_from <= :requiredFrom";
        } else if (requiredFrom == null) {
            periodClause = " AND (g.valid_to IS NULL OR g.valid_to >= :requiredTo)";
        } else {
            periodClause = """
                     AND g.valid_from <= :requiredFrom
                     AND (g.valid_to IS NULL OR g.valid_to >= :requiredTo)
                    """;
        }
        var grantQuery = jdbc.sql("""
                        SELECT COUNT(*)
                          FROM auth_role_grant g
                          JOIN auth_role r ON r.role_id = g.role_id
                          JOIN auth_role_capability rc ON rc.role_id = g.role_id
                         WHERE g.tenant_id = :tenant
                           AND g.principal_id = :principalId
                           AND rc.capability_code = :capability
                           AND r.tenant_id = :tenant
                           AND r.role_status = 'ACTIVE'
                           AND g.grant_status = 'ACTIVE'
                           AND g.grant_effect = 'ALLOW'
                           AND g.revoked_at IS NULL
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                           AND (
                                (g.scope_type = 'TENANT' AND g.scope_ref = :tenant)
                             OR (g.scope_type = :scopeType AND g.scope_ref = :scopeRef)
                           )
                        """ + periodClause)
                .param("tenant", tenantId)
                .param("principalId", principalId)
                .param("capability", capability)
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef);
        if (requiredFrom != null) {
            grantQuery = grantQuery.param("requiredFrom", timestamptz(requiredFrom));
        }
        if (requiredTo != null) {
            grantQuery = grantQuery.param("requiredTo", timestamptz(requiredTo));
        }
        Long grantHits = grantQuery.query(Long.class).single();
        if (grantHits != null && grantHits > 0) {
            return true;
        }

        String delegationPeriod;
        if (requiredFrom == null && requiredTo == null) {
            delegationPeriod = "";
        } else if (requiredFrom != null && requiredTo == null) {
            delegationPeriod = " AND d.valid_from <= :requiredFrom";
        } else if (requiredFrom == null) {
            delegationPeriod = " AND (d.valid_to IS NULL OR d.valid_to >= :requiredTo)";
        } else {
            delegationPeriod = """
                     AND d.valid_from <= :requiredFrom
                     AND (d.valid_to IS NULL OR d.valid_to >= :requiredTo)
                    """;
        }
        var delegationQuery = jdbc.sql("""
                        SELECT COUNT(*)
                          FROM auth_delegation d
                          JOIN auth_delegation_capability dc ON dc.delegation_id = d.delegation_id
                         WHERE d.tenant_id = :tenant
                           AND d.delegate_principal_id = :principalId
                           AND dc.capability_code = :capability
                           AND d.delegation_status = 'ACTIVE'
                           AND d.revoked_at IS NULL
                           AND d.valid_from <= :evaluatedAt
                           AND (d.valid_to IS NULL OR d.valid_to > :evaluatedAt)
                           AND (
                                (d.scope_type = 'TENANT' AND d.scope_ref = :tenant)
                             OR (d.scope_type = :scopeType AND d.scope_ref = :scopeRef)
                           )
                        """ + delegationPeriod)
                .param("tenant", tenantId)
                .param("principalId", principalId)
                .param("capability", capability)
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef);
        if (requiredFrom != null) {
            delegationQuery = delegationQuery.param("requiredFrom", timestamptz(requiredFrom));
        }
        if (requiredTo != null) {
            delegationQuery = delegationQuery.param("requiredTo", timestamptz(requiredTo));
        }
        Long delegationHits = delegationQuery.query(Long.class).single();
        return delegationHits != null && delegationHits > 0;
    }

    @Override
    public boolean roleHasHighOrCriticalCapability(UUID roleId) {
        Long count = jdbc.sql("""
                        SELECT COUNT(*)
                          FROM auth_role_capability rc
                          JOIN auth_capability c ON c.capability_code = rc.capability_code
                         WHERE rc.role_id = :roleId
                           AND c.risk_level IN ('HIGH', 'CRITICAL')
                        """)
                .param("roleId", roleId)
                .query(Long.class)
                .single();
        return count != null && count > 0;
    }

    @Override
    public void insertEvent(
            UUID eventId, String tenantId, String eventType, String resourceType, UUID resourceId,
            long resourceVersion, String reason, String actorId, String requestDigest,
            String correlationId, Instant occurredAt
    ) {
        jdbc.sql("""
                        INSERT INTO auth_role_grant_event (
                            event_id, tenant_id, event_type, resource_type, resource_id,
                            resource_version, reason, actor_id, request_digest, correlation_id, occurred_at
                        ) VALUES (
                            :eventId, :tenant, :eventType, :resourceType, :resourceId,
                            :version, :reason, :actorId, :digest, :correlationId, :occurredAt
                        )
                        """)
                .param("eventId", eventId)
                .param("tenant", tenantId)
                .param("eventType", eventType)
                .param("resourceType", resourceType)
                .param("resourceId", resourceId)
                .param("version", resourceVersion)
                .param("reason", reason)
                .param("actorId", actorId)
                .param("digest", requestDigest)
                .param("correlationId", correlationId)
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }

    @Override
    public long bumpGrantGeneration(String tenantId, Instant updatedAt) {
        jdbc.sql("""
                        INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                        VALUES (:tenant, 1, :updatedAt)
                        ON CONFLICT (tenant_id) DO UPDATE
                           SET generation = auth_tenant_grant_generation.generation + 1,
                               updated_at = EXCLUDED.updated_at
                        """)
                .param("tenant", tenantId)
                .param("updatedAt", Timestamp.from(updatedAt))
                .update();
        return currentGrantGeneration(tenantId);
    }

    @Override
    public long currentGrantGeneration(String tenantId) {
        return jdbc.sql("""
                        SELECT generation
                          FROM auth_tenant_grant_generation
                         WHERE tenant_id = :tenant
                        """)
                .param("tenant", tenantId)
                .query(Long.class)
                .optional()
                .orElse(0L);
    }

    private AuthRole withCapabilities(AuthRole role) {
        return new AuthRole(role.id(), role.tenantId(), role.roleCode(), role.roleName(),
                role.roleKind(), role.roleStatus(), role.description(),
                findRoleCapabilityCodes(role.id()), role.version(), role.createdAt(), role.updatedAt());
    }

    private DelegationRecord withDelegationCapabilities(DelegationRecord delegation) {
        List<String> codes = jdbc.sql("""
                        SELECT capability_code
                          FROM auth_delegation_capability
                         WHERE delegation_id = :id
                         ORDER BY capability_code
                        """)
                .param("id", delegation.id())
                .query(String.class)
                .list();
        return new DelegationRecord(delegation.id(), delegation.tenantId(),
                delegation.delegatorPrincipalId(), delegation.delegatePrincipalId(), codes,
                delegation.scopeType(), delegation.scopeRef(), delegation.validFrom(),
                delegation.validTo(), delegation.reason(), delegation.status(),
                delegation.version(), delegation.createdAt(), delegation.updatedAt(),
                delegation.revokedAt(), delegation.revokedBy(), delegation.revokeReason());
    }

    private AuthRole mapRoleWithoutCapabilities(ResultSet rs, int rowNum) throws SQLException {
        return new AuthRole(
                (UUID) rs.getObject("role_id"),
                rs.getString("tenant_id"),
                rs.getString("role_code"),
                rs.getString("role_name"),
                AuthRole.RoleKind.valueOf(rs.getString("role_kind")),
                rs.getString("role_status"),
                rs.getString("description"),
                List.of(),
                rs.getLong("aggregate_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private RoleGrantRecord mapGrant(ResultSet rs, int rowNum) throws SQLException {
        return new RoleGrantRecord(
                (UUID) rs.getObject("grant_id"),
                rs.getString("tenant_id"),
                rs.getString("principal_id"),
                (UUID) rs.getObject("role_id"),
                rs.getString("role_code"),
                rs.getString("scope_type"),
                rs.getString("scope_ref"),
                RoleGrantRecord.GrantStatus.valueOf(rs.getString("grant_status")),
                RoleGrantRecord.GrantEffect.valueOf(rs.getString("grant_effect")),
                rs.getTimestamp("valid_from").toInstant(),
                optionalInstant(rs, "valid_to"),
                rs.getString("source_code"),
                rs.getString("approval_ref"),
                rs.getString("requested_by"),
                rs.getString("request_reason"),
                rs.getString("approved_by"),
                optionalInstant(rs, "approved_at"),
                rs.getString("rejected_by"),
                optionalInstant(rs, "rejected_at"),
                rs.getString("reject_reason"),
                optionalInstant(rs, "revoked_at"),
                rs.getString("revoked_by"),
                rs.getString("revoke_reason"),
                rs.getLong("aggregate_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private DelegationRecord mapDelegationWithoutCapabilities(ResultSet rs, int rowNum) throws SQLException {
        return new DelegationRecord(
                (UUID) rs.getObject("delegation_id"),
                rs.getString("tenant_id"),
                rs.getString("delegator_principal_id"),
                rs.getString("delegate_principal_id"),
                new ArrayList<>(),
                rs.getString("scope_type"),
                rs.getString("scope_ref"),
                rs.getTimestamp("valid_from").toInstant(),
                optionalInstant(rs, "valid_to"),
                rs.getString("reason"),
                DelegationRecord.Status.valueOf(rs.getString("delegation_status")),
                rs.getLong("aggregate_version"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                optionalInstant(rs, "revoked_at"),
                rs.getString("revoked_by"),
                rs.getString("revoke_reason"));
    }

    private static Instant optionalInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
