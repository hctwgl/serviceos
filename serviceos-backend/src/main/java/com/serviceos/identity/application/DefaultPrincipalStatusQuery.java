package com.serviceos.identity.application;

import com.serviceos.identity.api.PrincipalStatusQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Principal ACTIVE 判定：直接查询 idn_security_principal，避免 network 依赖 identity 内部 Repository。 */
@Service
final class DefaultPrincipalStatusQuery implements PrincipalStatusQuery {
    private final JdbcClient jdbc;

    DefaultPrincipalStatusQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActive(String tenantId, UUID principalId) {
        return Boolean.TRUE.equals(jdbc.sql("""
                        SELECT principal_status = 'ACTIVE'
                          FROM idn_security_principal
                         WHERE tenant_id=:tenant AND principal_id=:principalId
                        """)
                .param("tenant", tenantId).param("principalId", principalId)
                .query(Boolean.class).optional().orElse(false));
    }
}
