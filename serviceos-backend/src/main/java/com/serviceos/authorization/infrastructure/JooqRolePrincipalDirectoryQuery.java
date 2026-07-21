package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.RolePrincipalDirectoryQuery;
import com.serviceos.jooq.generated.tables.AuthRole;
import com.serviceos.jooq.generated.tables.AuthRoleGrant;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.AuthRole.AUTH_ROLE;
import static com.serviceos.jooq.generated.tables.AuthRoleGrant.AUTH_ROLE_GRANT;

/**
 * RoleGrant → roleCode 主体目录。
 *
 * <p>范围：TENANT（scope_ref=tenantId）或 PROJECT（scope_ref=projectId）。
 * 同一主体若存在同角色 DENY 有效授予，则从 ALLOW 结果中排除。</p>
 */
@Component
final class JooqRolePrincipalDirectoryQuery implements RolePrincipalDirectoryQuery {
    private final DSLContext dsl;

    JooqRolePrincipalDirectoryQuery(DSLContext dsl) {
        this.dsl = dsl;
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

        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        AuthRole r = AUTH_ROLE.as("r");
        List<Row> rows = dsl.select(r.ROLE_CODE, g.PRINCIPAL_ID, g.GRANT_EFFECT)
                .from(g)
                .join(r).on(r.ROLE_ID.eq(g.ROLE_ID).and(r.TENANT_ID.eq(g.TENANT_ID)))
                .where(g.TENANT_ID.eq(safeTenant))
                .and(g.GRANT_STATUS.eq("ACTIVE"))
                .and(g.REVOKED_AT.isNull())
                .and(g.VALID_FROM.le(evaluatedAt))
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                .and(g.SCOPE_TYPE.eq("TENANT").and(g.SCOPE_REF.eq(safeTenant))
                        .or(g.SCOPE_TYPE.eq("PROJECT").and(g.SCOPE_REF.eq(projectId.toString()))))
                .orderBy(r.ROLE_CODE.asc(), g.PRINCIPAL_ID.asc(), g.CREATED_AT.asc())
                .fetch(row -> new Row(row.value1(), row.value2(), row.value3()));

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
