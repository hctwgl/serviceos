package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.RolePrincipalDirectoryQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * RoleGrant → roleCode 主体目录。
 *
 * <p>范围：TENANT（scope_ref=tenantId）或 PROJECT（scope_ref=projectId）。
 * 同一主体若存在同角色 DENY 有效授予，则从 ALLOW 结果中排除。</p>
 */
@Component
final class JdbcRolePrincipalDirectoryQuery implements RolePrincipalDirectoryQuery {
    private final JdbcClient jdbc;

    JdbcRolePrincipalDirectoryQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, List<String>> listActivePrincipalsGroupedByRoleCode(
            String tenantId,
            UUID projectId,
            Instant asOf
    ) {
        String safeTenant = Objects.requireNonNull(tenantId, "tenantId").trim();
        Objects.requireNonNull(projectId, "projectId");
        Instant evaluatedAt = Objects.requireNonNull(asOf, "asOf");

        List<Row> rows = jdbc.sql("""
                SELECT r.role_code AS role_code,
                       g.principal_id AS principal_id,
                       g.grant_effect AS grant_effect
                  FROM auth_role_grant g
                  JOIN auth_role r
                    ON r.role_id = g.role_id
                   AND r.tenant_id = g.tenant_id
                 WHERE g.tenant_id = :tenantId
                   AND g.grant_status = 'ACTIVE'
                   AND g.revoked_at IS NULL
                   AND g.valid_from <= :asOf
                   AND (g.valid_to IS NULL OR g.valid_to > :asOf)
                   AND (
                        (g.scope_type = 'TENANT' AND g.scope_ref = :tenantId)
                     OR (g.scope_type = 'PROJECT' AND g.scope_ref = :projectId)
                   )
                 ORDER BY r.role_code ASC, g.principal_id ASC, g.created_at ASC
                """)
                .param("tenantId", safeTenant)
                .param("projectId", projectId.toString())
                .param("asOf", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> new Row(
                        rs.getString("role_code"),
                        rs.getString("principal_id"),
                        rs.getString("grant_effect")))
                .list();

        Map<String, Set<String>> denied = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> allowed = new LinkedHashMap<>();
        for (Row row : rows) {
            if ("DENY".equals(row.effect())) {
                denied.computeIfAbsent(row.roleCode(), key -> new LinkedHashSet<>())
                        .add(row.principalId());
            }
        }
        for (Row row : rows) {
            if (!"ALLOW".equals(row.effect())) {
                continue;
            }
            if (denied.getOrDefault(row.roleCode(), Set.of()).contains(row.principalId())) {
                continue;
            }
            allowed.computeIfAbsent(row.roleCode(), key -> new LinkedHashSet<>())
                    .add(row.principalId());
        }

        Map<String, List<String>> result = new LinkedHashMap<>();
        allowed.forEach((role, principals) -> result.put(role, List.copyOf(principals)));
        return Map.copyOf(result);
    }

    private record Row(String roleCode, String principalId, String effect) {
    }
}
