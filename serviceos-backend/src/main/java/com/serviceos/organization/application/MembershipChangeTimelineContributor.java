package com.serviceos.organization.application;

import com.serviceos.identity.api.PrincipalChangeTimelineContributor;
import com.serviceos.identity.api.PrincipalChangeTimelineItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 将 org_structure_event 中的任职事实贡献到主体变更时间线。
 *
 * <p>只投影 MEMBERSHIP_* 且挂在 OrgMembership 上的不可变事件；不从可变任职行合成历史。</p>
 */
@Component
final class MembershipChangeTimelineContributor implements PrincipalChangeTimelineContributor {
    private final JdbcClient jdbc;

    MembershipChangeTimelineContributor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String source() {
        return "MEMBERSHIP";
    }

    @Override
    public String requiredCapability() {
        return "organization.read";
    }

    @Override
    public List<PrincipalChangeTimelineItem> listForPrincipal(
            String tenantId, UUID principalId, int limit
    ) {
        return jdbc.sql("""
                SELECT e.structure_event_id,
                       e.event_type,
                       e.reason,
                       e.actor_id,
                       e.correlation_id,
                       e.resource_version,
                       e.occurred_at,
                       o.organization_name,
                       u.unit_name
                  FROM org_structure_event e
                  JOIN org_membership m
                    ON m.tenant_id = e.tenant_id
                   AND m.membership_id = e.resource_id
                  JOIN org_organization o
                    ON o.tenant_id = m.tenant_id
                   AND o.organization_id = m.organization_id
                  LEFT JOIN org_unit u
                    ON u.tenant_id = m.tenant_id
                   AND u.org_unit_id = m.org_unit_id
                 WHERE e.tenant_id = :tenantId
                   AND m.principal_id = :principalId
                   AND e.resource_type = 'OrgMembership'
                   AND e.event_type IN (
                        'MEMBERSHIP_CREATED', 'MEMBERSHIP_TRANSFERRED', 'MEMBERSHIP_TERMINATED'
                   )
                 ORDER BY e.occurred_at DESC, e.structure_event_id DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("limit", limit)
                .query((rs, rowNum) -> PrincipalChangeTimelineItem.of(
                        "MEMBERSHIP",
                        rs.getString("event_type"),
                        membershipSummary(
                                rs.getString("event_type"),
                                rs.getString("organization_name"),
                                rs.getString("unit_name"),
                                rs.getString("reason")),
                        rs.getString("actor_id"),
                        "SUCCEEDED",
                        rs.getString("correlation_id"),
                        rs.getLong("resource_version"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("structure_event_id", UUID.class)))
                .list();
    }

    private static String membershipSummary(
            String eventType, String organizationName, String unitName, String reason
    ) {
        String org = organizationName == null || organizationName.isBlank() ? "组织" : organizationName.trim();
        String unit = unitName == null || unitName.isBlank() ? null : unitName.trim();
        String target = unit == null ? org : org + " / " + unit;
        String base = switch (eventType) {
            case "MEMBERSHIP_CREATED" -> "任职已创建 · " + target;
            case "MEMBERSHIP_TRANSFERRED" -> "任职已调动 · " + target;
            case "MEMBERSHIP_TERMINATED" -> "任职已终止 · " + target;
            default -> eventType + " · " + target;
        };
        if (reason == null || reason.isBlank()) {
            return base;
        }
        return base + " · " + reason.trim();
    }
}
