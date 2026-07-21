package com.serviceos.authorization.infrastructure;

import com.serviceos.authorization.api.PrincipalActiveRoleQuery;
import com.serviceos.jooq.generated.tables.AuthRole;
import com.serviceos.jooq.generated.tables.AuthRoleGrant;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.AuthRole.AUTH_ROLE;
import static com.serviceos.jooq.generated.tables.AuthRoleGrant.AUTH_ROLE_GRANT;

@Repository
final class JooqPrincipalActiveRoleQuery implements PrincipalActiveRoleQuery {
    private final DSLContext dsl;

    JooqPrincipalActiveRoleQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Set<UUID> listActiveRoleIds(String tenantId, String principalId, Instant evaluatedAt) {
        AuthRoleGrant g = AUTH_ROLE_GRANT.as("g");
        AuthRole r = AUTH_ROLE.as("r");
        return new HashSet<>(dsl.selectDistinct(g.ROLE_ID)
                .from(g)
                .join(r).on(r.ROLE_ID.eq(g.ROLE_ID).and(r.TENANT_ID.eq(g.TENANT_ID)))
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.PRINCIPAL_ID.eq(principalId))
                .and(g.GRANT_STATUS.eq("ACTIVE"))
                .and(g.GRANT_EFFECT.eq("ALLOW"))
                .and(g.REVOKED_AT.isNull())
                .and(r.ROLE_STATUS.eq("ACTIVE"))
                .and(g.VALID_FROM.le(evaluatedAt))
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(evaluatedAt)))
                .fetch(g.ROLE_ID));
    }

    @Override
    public boolean roleExists(String tenantId, UUID roleId) {
        AuthRole t = AUTH_ROLE;
        Integer count = dsl.selectCount()
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.ROLE_ID.eq(roleId))
                .and(t.ROLE_STATUS.eq("ACTIVE"))
                .fetchSingle(0, Integer.class);
        return count != null && count > 0;
    }
}
