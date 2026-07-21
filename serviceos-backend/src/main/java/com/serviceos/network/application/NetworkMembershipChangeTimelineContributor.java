package com.serviceos.network.application;

import com.serviceos.identity.api.PrincipalChangeTimelineContributor;
import com.serviceos.identity.api.PrincipalChangeTimelineItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 将 net_directory_event 中的网点任职事实贡献到主体变更时间线。
 *
 * <p>只投影挂在 NetworkMembership 上的不可变事件（邀请/终止）；
 * 不从可变任职行合成历史，也不混入师傅服务关系事件。</p>
 */
@Component
final class NetworkMembershipChangeTimelineContributor implements PrincipalChangeTimelineContributor {
    private final JdbcClient jdbc;

    NetworkMembershipChangeTimelineContributor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String source() {
        return "NETWORK_MEMBERSHIP";
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
                       n.network_name,
                       m.membership_role
                  FROM net_directory_event e
                  JOIN net_network_membership m
                    ON m.tenant_id = e.tenant_id
                   AND m.membership_id = e.resource_id
                  JOIN net_service_network n
                    ON n.tenant_id = m.tenant_id
                   AND n.service_network_id = m.service_network_id
                 WHERE e.tenant_id = :tenantId
                   AND m.principal_id = :principalId
                   AND e.resource_type = 'NetworkMembership'
                   AND e.event_type IN ('MEMBERSHIP_INVITED', 'MEMBERSHIP_TERMINATED')
                 ORDER BY e.occurred_at DESC, e.directory_event_id DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("limit", limit)
                .query((rs, rowNum) -> PrincipalChangeTimelineItem.of(
                        "NETWORK_MEMBERSHIP",
                        rs.getString("event_type"),
                        networkMembershipSummary(
                                rs.getString("event_type"),
                                rs.getString("network_name"),
                                rs.getString("membership_role"),
                                rs.getString("reason")),
                        rs.getString("actor_id"),
                        "SUCCEEDED",
                        rs.getString("correlation_id"),
                        rs.getLong("resource_version"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("directory_event_id", UUID.class)))
                .list();
    }

    private static String networkMembershipSummary(
            String eventType, String networkName, String membershipRole, String reason
    ) {
        String network = networkName == null || networkName.isBlank() ? "网点" : networkName.trim();
        String role = membershipRole == null || membershipRole.isBlank() ? null : membershipRole.trim();
        String target = role == null ? network : network + " · " + role;
        String base = switch (eventType) {
            case "MEMBERSHIP_INVITED" -> "网点任职已邀请 · " + target;
            case "MEMBERSHIP_TERMINATED" -> "网点任职已终止 · " + target;
            default -> eventType + " · " + target;
        };
        if (reason == null || reason.isBlank()) {
            return base;
        }
        return base + " · " + reason.trim();
    }
}
