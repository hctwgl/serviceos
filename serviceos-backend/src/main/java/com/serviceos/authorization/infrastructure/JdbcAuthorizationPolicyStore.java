package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.application.AuthorizationPolicyStore;
import com.serviceos.authorization.application.CapabilityGrantMatch;
import com.serviceos.authorization.application.ProjectScopeGrantMatch;
import com.serviceos.authorization.application.ProjectScopePolicyStore;
import com.serviceos.authorization.api.AuthorizationRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * RoleGrant 权威查询。有效期、撤销与 tenant scope 在同一 SQL 中实时判定，不能只信缓存/JWT。
 */
@Repository
final class JdbcAuthorizationPolicyStore implements AuthorizationPolicyStore, ProjectScopePolicyStore {
    private static final String POLICY_VERSION = "role-grant-v2";

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
        List<MatchedGrantRow> grants = jdbc.sql("""
                        SELECT g.grant_id::text, g.scope_type, g.scope_ref
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
                           AND (
                                (g.scope_type = 'TENANT' AND g.scope_ref = :tenantId)
                             OR (g.scope_type = 'PROJECT' AND g.scope_ref = :projectId)
                             OR (g.scope_type = 'REGION' AND g.scope_ref = :regionCode)
                             OR (g.scope_type = 'NETWORK' AND g.scope_ref = :networkId)
                           )
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                           AND g.revoked_at IS NULL
                         ORDER BY g.grant_id
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("capability", request.capability())
                // 空串不是合法 scope_ref，用作 null-safe sentinel，避免 JDBC 参数类型推断歧义。
                .param("projectId", nullSafeScope(request.projectId()))
                .param("regionCode", nullSafeScope(request.regionCode()))
                .param("networkId", nullSafeScope(request.networkId()))
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> new MatchedGrantRow(
                        rs.getString("grant_id"), rs.getString("scope_type"), rs.getString("scope_ref")))
                .list();
        return grants.isEmpty()
                ? CapabilityGrantMatch.denied(POLICY_VERSION)
                : new CapabilityGrantMatch(
                        true,
                        grants.stream().map(MatchedGrantRow::grantId).toList(),
                        grants.stream().map(row -> row.scopeType() + ":" + row.scopeRef()).toList(),
                        POLICY_VERSION);
    }

    @Override
    public ProjectScopeGrantMatch findProjectScopeGrants(
            String tenantId, String principalId, String capability, Instant evaluatedAt
    ) {
        List<String> scopes = jdbc.sql("""
                        SELECT g.scope_type || ':' || g.scope_ref
                          FROM auth_role_grant g
                          JOIN auth_role r ON r.role_id = g.role_id
                          JOIN auth_role_capability rc ON rc.role_id = g.role_id
                         WHERE g.tenant_id = :tenantId
                           AND g.principal_id = :principalId
                           AND rc.capability_code = :capability
                           AND r.tenant_id = :tenantId
                           AND r.role_status = 'ACTIVE'
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                           AND g.revoked_at IS NULL
                         ORDER BY g.scope_type, g.scope_ref, g.grant_id
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("capability", capability)
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query(String.class)
                .list();
        return new ProjectScopeGrantMatch(scopes, POLICY_VERSION);
    }

    private static String nullSafeScope(String value) {
        return value == null ? "" : value;
    }

    private record MatchedGrantRow(String grantId, String scopeType, String scopeRef) {
    }
}
