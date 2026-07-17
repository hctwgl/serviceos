package com.serviceos.authorization.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.organization.api.OrganizationRoleGrantPort;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 任职终止时批量撤销有效 RoleGrant；principal_id 在 auth_role_grant 为 varchar(128)。
 */
@Component
final class OrganizationRoleGrantAdapter implements OrganizationRoleGrantPort {
    private final JdbcClient jdbc;
    private final AuditAppender audit;
    private final Clock clock;

    OrganizationRoleGrantAdapter(JdbcClient jdbc, AuditAppender audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public int terminateActiveGrants(
            String tenantId, String principalId, Instant effectiveAt,
            String actorId, String reason, String correlationId
    ) {
        int updated = jdbc.sql("""
                UPDATE auth_role_grant
                   SET revoked_at=:effectiveAt, revoked_by=:actor, revoke_reason=:reason
                 WHERE tenant_id=:tenant AND principal_id=:principalId
                   AND revoked_at IS NULL
                   AND (valid_to IS NULL OR valid_to > :effectiveAt)
                """)
                .param("effectiveAt", java.sql.Timestamp.from(effectiveAt))
                .param("actor", actorId).param("reason", reason)
                .param("tenant", tenantId).param("principalId", principalId)
                .update();
        if (updated > 0) {
            String digest = Sha256.digest("terminate-grants|" + principalId + "|" + updated);
            audit.append(new AuditEntry(UUID.randomUUID(), tenantId, actorId, "ROLE_GRANTS_REVOKED",
                    "organization.manageMembership", "RoleGrant", principalId, "ALLOW",
                    java.util.List.of(), "employment-termination", "SUCCEEDED",
                    "revoked=" + updated, digest, correlationId, clock.instant()));
        }
        return updated;
    }
}
