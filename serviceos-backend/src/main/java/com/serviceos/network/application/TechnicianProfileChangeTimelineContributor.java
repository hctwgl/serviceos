package com.serviceos.network.application;

import com.serviceos.identity.api.PrincipalChangeTimelineContributor;
import com.serviceos.identity.api.PrincipalChangeTimelineItem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 将 net_directory_event 中的师傅档案生命周期事实贡献到主体变更时间线。
 *
 * <p>只投影挂在 TechnicianProfile 上的创建/停用/启用事件；
 * 不投影客户端种类声明，也不混入服务关系或网点任职事件。</p>
 */
@Component
final class TechnicianProfileChangeTimelineContributor implements PrincipalChangeTimelineContributor {
    private final JdbcClient jdbc;

    TechnicianProfileChangeTimelineContributor(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String source() {
        return "TECHNICIAN_PROFILE";
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
                       p.display_name
                  FROM net_directory_event e
                  JOIN net_technician_profile p
                    ON p.tenant_id = e.tenant_id
                   AND p.technician_profile_id = e.resource_id
                 WHERE e.tenant_id = :tenantId
                   AND p.principal_id = :principalId
                   AND e.resource_type = 'TechnicianProfile'
                   AND e.event_type IN (
                        'TECHNICIAN_CREATED', 'TECHNICIAN_DISABLED', 'TECHNICIAN_ENABLED'
                   )
                 ORDER BY e.occurred_at DESC, e.directory_event_id DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("limit", limit)
                .query((rs, rowNum) -> PrincipalChangeTimelineItem.of(
                        "TECHNICIAN_PROFILE",
                        rs.getString("event_type"),
                        technicianProfileSummary(
                                rs.getString("event_type"),
                                rs.getString("display_name"),
                                rs.getString("reason")),
                        rs.getString("actor_id"),
                        "SUCCEEDED",
                        rs.getString("correlation_id"),
                        rs.getLong("resource_version"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("directory_event_id", UUID.class)))
                .list();
    }

    private static String technicianProfileSummary(
            String eventType, String displayName, String reason
    ) {
        String name = displayName == null || displayName.isBlank() ? "师傅档案" : displayName.trim();
        String base = switch (eventType) {
            case "TECHNICIAN_CREATED" -> "师傅档案已创建 · " + name;
            case "TECHNICIAN_DISABLED" -> "师傅档案已停用 · " + name;
            case "TECHNICIAN_ENABLED" -> "师傅档案已启用 · " + name;
            default -> eventType + " · " + name;
        };
        if (reason == null || reason.isBlank()) {
            return base;
        }
        return base + " · " + reason.trim();
    }
}
