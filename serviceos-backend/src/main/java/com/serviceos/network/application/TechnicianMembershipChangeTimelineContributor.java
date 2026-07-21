package com.serviceos.network.application;

import com.serviceos.identity.api.PrincipalChangeTimelineContributor;
import com.serviceos.identity.api.PrincipalChangeTimelineItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 将 net_directory_event 中的师傅服务关系事实贡献到主体变更时间线。
 *
 * <p>只投影挂在 NetworkTechnicianMembership 上的不可变事件（创建/终止）；
 * 通过师傅档案关联到 principal，不从可变关系行合成历史，也不混入网点任职事件。</p>
 */
@Component
final class TechnicianMembershipChangeTimelineContributor implements PrincipalChangeTimelineContributor {
    private final JdbcClient jdbc;

    TechnicianMembershipChangeTimelineContributor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String source() {
        return "TECHNICIAN_MEMBERSHIP";
    }

    @Override
    public String requiredCapability() {
        return "network.read";
    }

    @Override
    public List<PrincipalChangeTimelineItem> listForPrincipal(
            String tenantId, UUID principalId, int limit
    ) {
        return jdbc.sql("""
                SELECT e.directory_event_id,
                       e.event_type,
                       e.reason,
                       e.actor_id,
                       e.correlation_id,
                       e.resource_version,
                       e.occurred_at,
                       n.network_name
                  FROM net_directory_event e
                  JOIN net_network_technician_membership m
                    ON m.tenant_id = e.tenant_id
                   AND m.membership_id = e.resource_id
                  JOIN net_technician_profile p
                    ON p.tenant_id = m.tenant_id
                   AND p.technician_profile_id = m.technician_profile_id
                  JOIN net_service_network n
                    ON n.tenant_id = m.tenant_id
                   AND n.service_network_id = m.service_network_id
                 WHERE e.tenant_id = :tenantId
                   AND p.principal_id = :principalId
                   AND e.resource_type = 'NetworkTechnicianMembership'
                   AND e.event_type IN (
                        'TECHNICIAN_MEMBERSHIP_CREATED', 'TECHNICIAN_MEMBERSHIP_TERMINATED'
                   )
                 ORDER BY e.occurred_at DESC, e.directory_event_id DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("limit", limit)
                .query((rs, rowNum) -> PrincipalChangeTimelineItem.of(
                        "TECHNICIAN_MEMBERSHIP",
                        rs.getString("event_type"),
                        technicianMembershipSummary(
                                rs.getString("event_type"),
                                rs.getString("network_name"),
                                rs.getString("reason")),
                        rs.getString("actor_id"),
                        "SUCCEEDED",
                        rs.getString("correlation_id"),
                        rs.getLong("resource_version"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("directory_event_id", UUID.class)))
                .list();
    }

    private static String technicianMembershipSummary(
            String eventType, String networkName, String reason
    ) {
        String network = networkName == null || networkName.isBlank() ? "网点" : networkName.trim();
        String base = switch (eventType) {
            case "TECHNICIAN_MEMBERSHIP_CREATED" -> "师傅服务关系已建立 · " + network;
            case "TECHNICIAN_MEMBERSHIP_TERMINATED" -> "师傅服务关系已终止 · " + network;
            default -> eventType + " · " + network;
        };
        if (reason == null || reason.isBlank()) {
            return base;
        }
        return base + " · " + reason.trim();
    }
}
