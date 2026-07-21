package com.serviceos.network.infrastructure;

import com.serviceos.network.api.NetworkDirectoryLabelQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** M439：批量解析网点名称与师傅档案显示名，供工单目录旁载。 */
@Component
final class JdbcNetworkDirectoryLabelQuery implements NetworkDirectoryLabelQuery {

    private final JdbcClient jdbc;

    JdbcNetworkDirectoryLabelQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, String> findNetworkNames(String tenantId, Collection<UUID> networkIds) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(networkIds, "networkIds must not be null");
        if (networkIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(networkIds);
        Map<UUID, String> result = new HashMap<>();
        jdbc.sql("""
                SELECT service_network_id AS id, network_name AS name
                  FROM net_service_network
                 WHERE tenant_id = :tenantId
                   AND service_network_id IN (:ids)
                """)
                .param("tenantId", tenantId)
                .param("ids", ids)
                .query((rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    if (id != null && name != null && !name.isBlank()) {
                        result.put(id, name.trim());
                    }
                    return null;
                })
                .list();
        return Map.copyOf(result);
    }

    @Override
    public Map<UUID, String> findTechnicianProfileDisplayNames(
            String tenantId, Collection<UUID> technicianProfileIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(technicianProfileIds, "technicianProfileIds must not be null");
        if (technicianProfileIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(technicianProfileIds);
        Map<UUID, String> result = new HashMap<>();
        jdbc.sql("""
                SELECT technician_profile_id AS id, display_name AS name
                  FROM net_technician_profile
                 WHERE tenant_id = :tenantId
                   AND technician_profile_id IN (:ids)
                """)
                .param("tenantId", tenantId)
                .param("ids", ids)
                .query((rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    if (id != null && name != null && !name.isBlank()) {
                        result.put(id, name.trim());
                    }
                    return null;
                })
                .list();
        return Map.copyOf(result);
    }
}
