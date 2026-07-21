package com.serviceos.authorization.application;

import com.serviceos.identity.api.PrincipalChangeTimelineContributor;
import com.serviceos.identity.api.PrincipalChangeTimelineItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 将 auth_role_grant_event 中的授权治理事实贡献到主体变更时间线。
 *
 * <p>只投影挂在 RoleGrant 上的不可变事件；不把通用 AUTHORIZATION_DENIED 混入主体活动流。</p>
 */
@Component
final class RoleGrantChangeTimelineContributor implements PrincipalChangeTimelineContributor {
    private final JdbcClient jdbc;

    RoleGrantChangeTimelineContributor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String source() {
        return "ROLE_GRANT";
    }

    @Override
    public String requiredCapability() {
        return "authorization.read";
    }

    @Override
    public List<PrincipalChangeTimelineItem> listForPrincipal(
            String tenantId, UUID principalId, int limit
    ) {
        return jdbc.sql("""
                SELECT e.event_id,
                       e.event_type,
                       e.reason,
                       e.actor_id,
                       e.correlation_id,
                       e.resource_version,
                       e.occurred_at,
                       r.role_code,
                       g.grant_effect,
                       g.scope_type,
                       g.scope_ref
                  FROM auth_role_grant_event e
                  JOIN auth_role_grant g
                    ON g.tenant_id = e.tenant_id
                   AND g.grant_id = e.resource_id
                  JOIN auth_role r
                    ON r.tenant_id = g.tenant_id
                   AND r.role_id = g.role_id
                 WHERE e.tenant_id = :tenantId
                   AND g.principal_id = :principalId
                   AND e.resource_type = 'RoleGrant'
                   AND e.event_type IN (
                        'ROLE_GRANT_REQUESTED', 'ROLE_GRANT_APPROVED',
                        'ROLE_GRANT_REJECTED', 'ROLE_GRANT_REVOKED'
                   )
                 ORDER BY e.occurred_at DESC, e.event_id DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("limit", limit)
                .query((rs, rowNum) -> PrincipalChangeTimelineItem.of(
                        "ROLE_GRANT",
                        rs.getString("event_type"),
                        roleGrantSummary(
                                rs.getString("event_type"),
                                rs.getString("role_code"),
                                rs.getString("grant_effect"),
                                rs.getString("scope_type"),
                                rs.getString("scope_ref"),
                                rs.getString("reason")),
                        rs.getString("actor_id"),
                        "SUCCEEDED",
                        rs.getString("correlation_id"),
                        rs.getLong("resource_version"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("event_id", UUID.class)))
                .list();
    }

    private static String roleGrantSummary(
            String eventType,
            String roleCode,
            String grantEffect,
            String scopeType,
            String scopeRef,
            String reason
    ) {
        String role = roleCode == null || roleCode.isBlank() ? "角色" : roleCode.trim();
        String effect = grantEffect == null ? "" : " · " + grantEffect;
        String scope = (scopeType == null ? "" : scopeType)
                + (scopeRef == null || scopeRef.isBlank() ? "" : "=" + scopeRef);
        String scopePart = scope.isBlank() ? "" : " · " + scope;
        String base = switch (eventType) {
            case "ROLE_GRANT_REQUESTED" -> "角色授权已申请 · " + role + effect + scopePart;
            case "ROLE_GRANT_APPROVED" -> "角色授权已批准 · " + role + effect + scopePart;
            case "ROLE_GRANT_REJECTED" -> "角色授权已驳回 · " + role + effect + scopePart;
            case "ROLE_GRANT_REVOKED" -> "角色授权已撤销 · " + role + effect + scopePart;
            default -> eventType + " · " + role;
        };
        if (reason == null || reason.isBlank()) {
            return base;
        }
        return base + " · " + reason.trim();
    }
}
