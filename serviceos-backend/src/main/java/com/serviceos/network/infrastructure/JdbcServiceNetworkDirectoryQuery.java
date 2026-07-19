package com.serviceos.network.infrastructure;

import com.serviceos.network.api.ServiceNetworkDirectoryQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** ACTIVE ServiceNetwork 过滤（按 service_network_id 文本匹配项目 network_id）。 */
@Component
final class JdbcServiceNetworkDirectoryQuery implements ServiceNetworkDirectoryQuery {
    private final JdbcClient jdbc;

    JdbcServiceNetworkDirectoryQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> listActiveNetworkIds(String tenantId, Collection<String> networkIds) {
        String safeTenant = Objects.requireNonNull(tenantId, "tenantId").trim();
        Objects.requireNonNull(networkIds, "networkIds");
        if (networkIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(jdbc.sql("""
                SELECT service_network_id::text
                  FROM net_service_network
                 WHERE tenant_id = :tenantId
                   AND service_network_id::text IN (:networkIds)
                   AND network_status = 'ACTIVE'
                 ORDER BY service_network_id ASC
                """)
                .param("tenantId", safeTenant)
                .param("networkIds", networkIds)
                .query(String.class)
                .list());
    }
}
