package com.serviceos.authorization.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.organization.api.OrganizationRoleGrantPort;
import com.serviceos.shared.Sha256;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 任职终止时批量撤销有效 RoleGrant；同步推进 grant generation 并追加治理事件。
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
        List<UUID> grantIds = jdbc.sql("""
                        SELECT grant_id
                          FROM auth_role_grant
                         WHERE tenant_id = :tenant
                           AND principal_id = :principalId
                           AND grant_status IN ('ACTIVE', 'PENDING_APPROVAL')
                           AND revoked_at IS NULL
                           AND (valid_to IS NULL OR valid_to > :effectiveAt)
                         FOR UPDATE
                        """)
                .param("tenant", tenantId)
                .param("principalId", principalId)
                .param("effectiveAt", java.sql.Timestamp.from(effectiveAt))
                .query(UUID.class)
                .list();
        if (grantIds.isEmpty()) {
            return 0;
        }
        int updated = jdbc.sql("""
                        UPDATE auth_role_grant
                           SET revoked_at = :effectiveAt,
                               revoked_by = :actor,
                               revoke_reason = :reason,
                               grant_status = 'REVOKED',
                               aggregate_version = aggregate_version + 1,
                               updated_at = :effectiveAt
                         WHERE tenant_id = :tenant
                           AND principal_id = :principalId
                           AND grant_status IN ('ACTIVE', 'PENDING_APPROVAL')
                           AND revoked_at IS NULL
                           AND (valid_to IS NULL OR valid_to > :effectiveAt)
                        """)
                .param("effectiveAt", java.sql.Timestamp.from(effectiveAt))
                .param("actor", actorId)
                .param("reason", reason)
                .param("tenant", tenantId)
                .param("principalId", principalId)
                .update();
        for (UUID grantId : grantIds) {
            jdbc.sql("""
                            INSERT INTO auth_role_grant_event (
                                event_id, tenant_id, event_type, resource_type, resource_id,
                                resource_version, reason, actor_id, request_digest, correlation_id, occurred_at
                            ) VALUES (
                                :eventId, :tenant, 'ROLE_GRANT_REVOKED', 'RoleGrant', :grantId,
                                1, :reason, :actor, :digest, :correlationId, :occurredAt
                            )
                            """)
                    .param("eventId", UUID.randomUUID())
                    .param("tenant", tenantId)
                    .param("grantId", grantId)
                    .param("reason", reason)
                    .param("actor", actorId)
                    .param("digest", Sha256.digest("terminate-grant|" + grantId))
                    .param("correlationId", correlationId)
                    .param("occurredAt", java.sql.Timestamp.from(effectiveAt))
                    .update();
        }
        jdbc.sql("""
                        INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                        VALUES (:tenant, 1, :updatedAt)
                        ON CONFLICT (tenant_id) DO UPDATE
                           SET generation = auth_tenant_grant_generation.generation + 1,
                               updated_at = EXCLUDED.updated_at
                        """)
                .param("tenant", tenantId)
                .param("updatedAt", java.sql.Timestamp.from(effectiveAt))
                .update();
        String digest = Sha256.digest("terminate-grants|" + principalId + "|" + updated);
        audit.append(new AuditEntry(UUID.randomUUID(), tenantId, actorId, "ROLE_GRANTS_REVOKED",
                "organization.manageMembership", "RoleGrant", principalId, "ALLOW",
                List.of(), "employment-termination", "SUCCEEDED",
                "revoked=" + updated, digest, correlationId, clock.instant()));
        return updated;
    }
}
