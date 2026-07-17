package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.application.AuthorizationPolicyStore;
import com.serviceos.authorization.application.CapabilityGrantMatch;
import com.serviceos.authorization.application.ProjectScopeGrantMatch;
import com.serviceos.authorization.application.ProjectScopePolicyStore;
import com.serviceos.authorization.api.AuthorizationRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * RoleGrant/Delegation 权威查询。仅 ACTIVE 未过期未撤销授权参与判定；匹配范围内 DENY 优先于 ALLOW；
 * policyVersion 绑定租户 grant generation，撤销后使依赖旧版本的上下文失败关闭。
 */
@Repository
final class JdbcAuthorizationPolicyStore implements AuthorizationPolicyStore, ProjectScopePolicyStore {
    private final JdbcClient jdbc;

    JdbcAuthorizationPolicyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CapabilityGrantMatch findCapabilityGrants(
            String tenantId,
            String principalId,
            AuthorizationRequest request,
            Instant evaluatedAt
    ) {
        String policyVersion = policyVersion(tenantId);
        List<MatchedGrantRow> grantRows = jdbc.sql("""
                        SELECT g.grant_id::text AS match_id,
                               g.scope_type,
                               g.scope_ref,
                               g.grant_effect AS effect
                          FROM auth_role_grant g
                          JOIN auth_role r
                            ON r.role_id = g.role_id
                          JOIN auth_role_capability rc
                            ON rc.role_id = g.role_id
                          JOIN auth_capability c
                            ON c.capability_code = rc.capability_code
                         WHERE g.tenant_id = :tenantId
                           AND g.principal_id = :principalId
                           AND c.capability_code = :capability
                           AND r.tenant_id = :tenantId
                           AND r.role_status = 'ACTIVE'
                           AND g.grant_status = 'ACTIVE'
                           AND g.revoked_at IS NULL
                           AND (
                                (g.scope_type = 'TENANT' AND g.scope_ref = :tenantId)
                             OR (g.scope_type = 'PROJECT' AND g.scope_ref = :projectId)
                             OR (g.scope_type = 'REGION' AND g.scope_ref = :regionCode)
                             OR (g.scope_type = 'NETWORK' AND g.scope_ref = :networkId)
                           )
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                         ORDER BY g.grant_id
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("capability", request.capability())
                .param("projectId", nullSafeScope(request.projectId()))
                .param("regionCode", nullSafeScope(request.regionCode()))
                .param("networkId", nullSafeScope(request.networkId()))
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> new MatchedGrantRow(
                        rs.getString("match_id"),
                        rs.getString("scope_type"),
                        rs.getString("scope_ref"),
                        rs.getString("effect")))
                .list();

        List<MatchedGrantRow> denyRows = grantRows.stream()
                .filter(row -> "DENY".equals(row.effect()))
                .toList();
        if (!denyRows.isEmpty()) {
            return new CapabilityGrantMatch(
                    false,
                    denyRows.stream().map(MatchedGrantRow::matchId).toList(),
                    denyRows.stream().map(row -> row.scopeType() + ":" + row.scopeRef()).toList(),
                    policyVersion);
        }

        List<MatchedGrantRow> allowRows = new ArrayList<>(grantRows.stream()
                .filter(row -> "ALLOW".equals(row.effect()))
                .toList());

        List<MatchedGrantRow> delegationRows = jdbc.sql("""
                        SELECT ('delegation:' || d.delegation_id::text) AS match_id,
                               d.scope_type,
                               d.scope_ref,
                               'ALLOW' AS effect
                          FROM auth_delegation d
                          JOIN auth_delegation_capability dc
                            ON dc.delegation_id = d.delegation_id
                         WHERE d.tenant_id = :tenantId
                           AND d.delegate_principal_id = :principalId
                           AND dc.capability_code = :capability
                           AND d.delegation_status = 'ACTIVE'
                           AND d.revoked_at IS NULL
                           AND (
                                (d.scope_type = 'TENANT' AND d.scope_ref = :tenantId)
                             OR (d.scope_type = 'PROJECT' AND d.scope_ref = :projectId)
                             OR (d.scope_type = 'REGION' AND d.scope_ref = :regionCode)
                             OR (d.scope_type = 'NETWORK' AND d.scope_ref = :networkId)
                           )
                           AND d.valid_from <= :evaluatedAt
                           AND (d.valid_to IS NULL OR d.valid_to > :evaluatedAt)
                         ORDER BY d.delegation_id
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("capability", request.capability())
                .param("projectId", nullSafeScope(request.projectId()))
                .param("regionCode", nullSafeScope(request.regionCode()))
                .param("networkId", nullSafeScope(request.networkId()))
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> new MatchedGrantRow(
                        rs.getString("match_id"),
                        rs.getString("scope_type"),
                        rs.getString("scope_ref"),
                        rs.getString("effect")))
                .list();
        allowRows.addAll(delegationRows);

        if (allowRows.isEmpty()) {
            return CapabilityGrantMatch.denied(policyVersion);
        }
        return new CapabilityGrantMatch(
                true,
                allowRows.stream().map(MatchedGrantRow::matchId).toList(),
                allowRows.stream().map(row -> row.scopeType() + ":" + row.scopeRef()).toList(),
                policyVersion);
    }

    @Override
    public ProjectScopeGrantMatch findProjectScopeGrants(
            String tenantId, String principalId, String capability, Instant evaluatedAt
    ) {
        String policyVersion = policyVersion(tenantId);
        List<String> grantScopes = jdbc.sql("""
                        SELECT g.scope_type || ':' || g.scope_ref
                          FROM auth_role_grant g
                          JOIN auth_role r ON r.role_id = g.role_id
                          JOIN auth_role_capability rc ON rc.role_id = g.role_id
                         WHERE g.tenant_id = :tenantId
                           AND g.principal_id = :principalId
                           AND rc.capability_code = :capability
                           AND r.tenant_id = :tenantId
                           AND r.role_status = 'ACTIVE'
                           AND g.grant_status = 'ACTIVE'
                           AND g.grant_effect = 'ALLOW'
                           AND g.revoked_at IS NULL
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                         ORDER BY g.scope_type, g.scope_ref, g.grant_id
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("capability", capability)
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query(String.class)
                .list();
        List<String> delegationScopes = jdbc.sql("""
                        SELECT d.scope_type || ':' || d.scope_ref
                          FROM auth_delegation d
                          JOIN auth_delegation_capability dc ON dc.delegation_id = d.delegation_id
                         WHERE d.tenant_id = :tenantId
                           AND d.delegate_principal_id = :principalId
                           AND dc.capability_code = :capability
                           AND d.delegation_status = 'ACTIVE'
                           AND d.revoked_at IS NULL
                           AND d.valid_from <= :evaluatedAt
                           AND (d.valid_to IS NULL OR d.valid_to > :evaluatedAt)
                         ORDER BY d.scope_type, d.scope_ref, d.delegation_id
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("capability", capability)
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query(String.class)
                .list();
        Set<String> scopes = new LinkedHashSet<>(grantScopes);
        scopes.addAll(delegationScopes);
        return new ProjectScopeGrantMatch(List.copyOf(scopes), policyVersion);
    }

    @Override
    public List<String> listEffectiveCapabilityCodes(
            String tenantId,
            String principalId,
            String scopeType,
            String scopeRef,
            Instant evaluatedAt
    ) {
        List<CapabilityEffectRow> rows = jdbc.sql("""
                        SELECT c.capability_code, g.grant_effect AS effect
                          FROM auth_role_grant g
                          JOIN auth_role r ON r.role_id = g.role_id
                          JOIN auth_role_capability rc ON rc.role_id = g.role_id
                          JOIN auth_capability c ON c.capability_code = rc.capability_code
                         WHERE g.tenant_id = :tenantId
                           AND g.principal_id = :principalId
                           AND r.tenant_id = :tenantId
                           AND r.role_status = 'ACTIVE'
                           AND g.grant_status = 'ACTIVE'
                           AND g.revoked_at IS NULL
                           AND g.scope_type = :scopeType
                           AND g.scope_ref = :scopeRef
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                        UNION ALL
                        SELECT dc.capability_code, 'ALLOW' AS effect
                          FROM auth_delegation d
                          JOIN auth_delegation_capability dc ON dc.delegation_id = d.delegation_id
                         WHERE d.tenant_id = :tenantId
                           AND d.delegate_principal_id = :principalId
                           AND d.delegation_status = 'ACTIVE'
                           AND d.revoked_at IS NULL
                           AND d.scope_type = :scopeType
                           AND d.scope_ref = :scopeRef
                           AND d.valid_from <= :evaluatedAt
                           AND (d.valid_to IS NULL OR d.valid_to > :evaluatedAt)
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef)
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> new CapabilityEffectRow(
                        rs.getString("capability_code"), rs.getString("effect")))
                .list();

        Set<String> denied = new LinkedHashSet<>();
        Set<String> allowed = new LinkedHashSet<>();
        for (CapabilityEffectRow row : rows) {
            if ("DENY".equals(row.effect())) {
                denied.add(row.capabilityCode());
            } else if ("ALLOW".equals(row.effect())) {
                allowed.add(row.capabilityCode());
            }
        }

        // 运行时 TENANT 范围授权可覆盖 PROJECT/REGION/NETWORK 请求；导航能力集合同步并入。
        // 业务命令仍按具体资源重新鉴权，本集合不是授权凭证。
        if (!"TENANT".equals(scopeType)) {
            mergeEffects(allowed, denied, jdbc.sql("""
                            SELECT c.capability_code, g.grant_effect AS effect
                              FROM auth_role_grant g
                              JOIN auth_role r ON r.role_id = g.role_id
                              JOIN auth_role_capability rc ON rc.role_id = g.role_id
                              JOIN auth_capability c ON c.capability_code = rc.capability_code
                             WHERE g.tenant_id = :tenantId
                               AND g.principal_id = :principalId
                               AND r.role_status = 'ACTIVE'
                               AND g.grant_status = 'ACTIVE'
                               AND g.revoked_at IS NULL
                               AND g.scope_type = 'TENANT'
                               AND g.scope_ref = :tenantId
                               AND g.valid_from <= :evaluatedAt
                               AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                            """)
                    .param("tenantId", tenantId)
                    .param("principalId", principalId)
                    .param("evaluatedAt", timestamptz(evaluatedAt))
                    .query((rs, rowNum) -> new CapabilityEffectRow(
                            rs.getString("capability_code"), rs.getString("effect")))
                    .list());
        } else {
            List<String> projectRegion = jdbc.sql("""
                            SELECT DISTINCT c.capability_code
                              FROM auth_role_grant g
                              JOIN auth_role r ON r.role_id = g.role_id
                              JOIN auth_role_capability rc ON rc.role_id = g.role_id
                              JOIN auth_capability c ON c.capability_code = rc.capability_code
                             WHERE g.tenant_id = :tenantId
                               AND g.principal_id = :principalId
                               AND r.role_status = 'ACTIVE'
                               AND g.grant_status = 'ACTIVE'
                               AND g.grant_effect = 'ALLOW'
                               AND g.revoked_at IS NULL
                               AND g.scope_type IN ('PROJECT', 'REGION')
                               AND g.valid_from <= :evaluatedAt
                               AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                            """)
                    .param("tenantId", tenantId)
                    .param("principalId", principalId)
                    .param("evaluatedAt", timestamptz(evaluatedAt))
                    .query(String.class)
                    .list();
            allowed.addAll(projectRegion);
        }
        allowed.removeAll(denied);
        return List.copyOf(allowed);
    }

    private static void mergeEffects(
            Set<String> allowed, Set<String> denied, List<CapabilityEffectRow> rows
    ) {
        for (CapabilityEffectRow row : rows) {
            if ("DENY".equals(row.effect())) {
                denied.add(row.capabilityCode());
            } else if ("ALLOW".equals(row.effect())) {
                allowed.add(row.capabilityCode());
            }
        }
    }

    @Override
    public String policyVersion(String tenantId) {
        long generation = jdbc.sql("""
                        SELECT generation
                          FROM auth_tenant_grant_generation
                         WHERE tenant_id = :tenantId
                        """)
                .param("tenantId", tenantId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        return "role-grant-v3:g" + generation;
    }

    private record CapabilityEffectRow(String capabilityCode, String effect) {
    }

    private static String nullSafeScope(String value) {
        return value == null ? "" : value;
    }

    private record MatchedGrantRow(String matchId, String scopeType, String scopeRef, String effect) {
    }
}
