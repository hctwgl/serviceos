package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.PrincipalActiveRoleQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

@Repository
final class JdbcPrincipalActiveRoleQuery implements PrincipalActiveRoleQuery {
    private final JdbcClient jdbc;

    JdbcPrincipalActiveRoleQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> listActiveRoleIds(String tenantId, String principalId, Instant evaluatedAt) {
        return new HashSet<>(jdbc.sql("""
                        SELECT DISTINCT g.role_id
                          FROM auth_role_grant g
                          JOIN auth_role r ON r.role_id = g.role_id AND r.tenant_id = g.tenant_id
                         WHERE g.tenant_id = :tenantId
                           AND g.principal_id = :principalId
                           AND g.grant_status = 'ACTIVE'
                           AND g.grant_effect = 'ALLOW'
                           AND g.revoked_at IS NULL
                           AND r.role_status = 'ACTIVE'
                           AND g.valid_from <= :evaluatedAt
                           AND (g.valid_to IS NULL OR g.valid_to > :evaluatedAt)
                        """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("evaluatedAt", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> rs.getObject("role_id", UUID.class))
                .list());
    }

    @Override
    public boolean roleExists(String tenantId, UUID roleId) {
        Integer count = jdbc.sql("""
                        SELECT count(*)::int
                          FROM auth_role
                         WHERE tenant_id = :tenantId
                           AND role_id = :roleId
                           AND role_status = 'ACTIVE'
                        """)
                .param("tenantId", tenantId)
                .param("roleId", roleId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }
}
