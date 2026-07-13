package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.application.AuthorizationPolicyStore;
import com.serviceos.authorization.application.CapabilityGrantMatch;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * RoleGrant 权威查询。有效期、撤销与 tenant scope 在同一 SQL 中实时判定，不能只信缓存/JWT。
 */
@Repository
final class JdbcAuthorizationPolicyStore implements AuthorizationPolicyStore {
    private static final String POLICY_VERSION = "role-grant-v1";

    private final JdbcClient jdbc;

    JdbcAuthorizationPolicyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CapabilityGrantMatch findTenantCapabilityGrants(
            String tenantId,
            String principalId,
            String capability,
            Instant evaluatedAt
    ) {
        List<String> grantIds = jdbc.sql("""
                        SELECT g.grant_id::text
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
                           AND g.scope_type = 'TENANT'
                           AND g.scope_ref = :tenantId
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                           AND g.revoked_at IS NULL
                         ORDER BY g.grant_id
                        """)
                .params(Map.of(
                        "tenantId", tenantId,
                        "principalId", principalId,
                        "capability", capability,
                        "evaluatedAt", timestamptz(evaluatedAt)))
                .query(String.class)
                .list();
        return grantIds.isEmpty()
                ? CapabilityGrantMatch.denied(POLICY_VERSION)
                : new CapabilityGrantMatch(true, List.copyOf(grantIds), POLICY_VERSION);
    }
}
