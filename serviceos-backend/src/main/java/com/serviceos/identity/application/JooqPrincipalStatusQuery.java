package com.serviceos.identity.application;

import com.serviceos.identity.api.PrincipalStatusQuery;
import com.serviceos.jooq.generated.tables.IdnSecurityPrincipal;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IdnSecurityPrincipal.IDN_SECURITY_PRINCIPAL;

/** Principal ACTIVE 判定：直接查询 idn_security_principal，避免 network 依赖 identity 内部 Repository。 */
@Service
final class JooqPrincipalStatusQuery implements PrincipalStatusQuery {
    private final DSLContext dsl;

    JooqPrincipalStatusQuery(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean isActive(String tenantId, UUID principalId) {
        IdnSecurityPrincipal p = IDN_SECURITY_PRINCIPAL;
        return dsl.select(p.PRINCIPAL_STATUS.eq("ACTIVE"))
                .from(p)
                .where(p.TENANT_ID.eq(tenantId))
                .and(p.PRINCIPAL_ID.eq(principalId))
                .fetchOptional()
                .map(Record1::value1)
                .orElse(false);
    }
}
