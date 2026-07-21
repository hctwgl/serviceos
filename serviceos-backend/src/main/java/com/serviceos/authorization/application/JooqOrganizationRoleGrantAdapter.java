package com.serviceos.authorization.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.jooq.generated.tables.AuthRoleGrant;
import com.serviceos.jooq.generated.tables.AuthRoleGrantEvent;
import com.serviceos.jooq.generated.tables.AuthTenantGrantGeneration;
import com.serviceos.organization.api.OrganizationRoleGrantPort;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.AuthRoleGrant.AUTH_ROLE_GRANT;
import static com.serviceos.jooq.generated.tables.AuthRoleGrantEvent.AUTH_ROLE_GRANT_EVENT;
import static com.serviceos.jooq.generated.tables.AuthTenantGrantGeneration.AUTH_TENANT_GRANT_GENERATION;
import static org.jooq.impl.DSL.excluded;

/**
 * 任职终止时批量撤销有效 RoleGrant；同步推进 grant generation 并追加治理事件。
 */
@Component
final class JooqOrganizationRoleGrantAdapter implements OrganizationRoleGrantPort {
    private final DSLContext dsl;
    private final AuditAppender audit;
    private final Clock clock;

    JooqOrganizationRoleGrantAdapter(DSLContext dsl, AuditAppender audit, Clock clock) {
        this.dsl = dsl;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public int terminateActiveGrants(
            String tenantId, String principalId, Instant effectiveAt,
            String actorId, String reason, String correlationId
    ) {
        AuthRoleGrant g = AUTH_ROLE_GRANT;
        // FOR UPDATE 先锁定待撤销授权行，防止与审批/撤销并发写冲突；后续条件更新在同一事务内完成。
        List<UUID> grantIds = dsl.select(g.GRANT_ID)
                .from(g)
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.PRINCIPAL_ID.eq(principalId))
                .and(g.GRANT_STATUS.in("ACTIVE", "PENDING_APPROVAL"))
                .and(g.REVOKED_AT.isNull())
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(effectiveAt)))
                .forUpdate()
                .fetch(g.GRANT_ID);
        if (grantIds.isEmpty()) {
            return 0;
        }
        // 条件更新保留与锁定查询相同的谓词（状态/未撤销/有效期），影响行数即实际撤销数。
        int updated = dsl.update(g)
                .set(g.REVOKED_AT, effectiveAt)
                .set(g.REVOKED_BY, actorId)
                .set(g.REVOKE_REASON, reason)
                .set(g.GRANT_STATUS, "REVOKED")
                .set(g.AGGREGATE_VERSION, g.AGGREGATE_VERSION.plus(1))
                .set(g.UPDATED_AT, effectiveAt)
                .where(g.TENANT_ID.eq(tenantId))
                .and(g.PRINCIPAL_ID.eq(principalId))
                .and(g.GRANT_STATUS.in("ACTIVE", "PENDING_APPROVAL"))
                .and(g.REVOKED_AT.isNull())
                .and(g.VALID_TO.isNull().or(g.VALID_TO.gt(effectiveAt)))
                .execute();
        AuthRoleGrantEvent e = AUTH_ROLE_GRANT_EVENT;
        for (UUID grantId : grantIds) {
            dsl.insertInto(e)
                    .set(e.EVENT_ID, UUID.randomUUID())
                    .set(e.TENANT_ID, tenantId)
                    .set(e.EVENT_TYPE, "ROLE_GRANT_REVOKED")
                    .set(e.RESOURCE_TYPE, "RoleGrant")
                    .set(e.RESOURCE_ID, grantId)
                    .set(e.RESOURCE_VERSION, 1L)
                    .set(e.REASON, reason)
                    .set(e.ACTOR_ID, actorId)
                    .set(e.REQUEST_DIGEST, Sha256.digest("terminate-grant|" + grantId))
                    .set(e.CORRELATION_ID, correlationId)
                    .set(e.OCCURRED_AT, effectiveAt)
                    .execute();
        }
        AuthTenantGrantGeneration t = AUTH_TENANT_GRANT_GENERATION;
        dsl.insertInto(t)
                .set(t.TENANT_ID, tenantId)
                .set(t.GENERATION, 1L)
                .set(t.UPDATED_AT, effectiveAt)
                .onConflict(t.TENANT_ID)
                .doUpdate()
                .set(t.GENERATION, t.GENERATION.plus(1))
                .set(t.UPDATED_AT, excluded(t.UPDATED_AT))
                .execute();
        String digest = Sha256.digest("terminate-grants|" + principalId + "|" + updated);
        audit.append(new AuditEntry(UUID.randomUUID(), tenantId, actorId, "ROLE_GRANTS_REVOKED",
                "organization.manageMembership", "RoleGrant", principalId, "ALLOW",
                List.of(), "employment-termination", "SUCCEEDED",
                "revoked=" + updated, digest, correlationId, clock.instant()));
        return updated;
    }
}
