package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.NetworkCapacityCounterView;
import com.serviceos.dispatch.api.NetworkCapacitySummaryQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** 网点容量计数器只读适配：按 NETWORK assignee_id = networkId 查询。 */
@Service
final class DefaultNetworkCapacitySummaryQuery implements NetworkCapacitySummaryQuery {
    private final JdbcClient jdbc;

    DefaultNetworkCapacitySummaryQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkCapacityCounterView> listForNetwork(String tenantId, String networkId) {
        return jdbc.sql("""
                        SELECT capacity_counter_id, business_type, max_units, occupied_units,
                               version, updated_at
                          FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId
                           AND responsibility_level = 'NETWORK'
                           AND assignee_id = :networkId
                         ORDER BY business_type, capacity_counter_id
                        """)
                .param("tenantId", tenantId)
                .param("networkId", networkId)
                .query((rs, rowNum) -> new NetworkCapacityCounterView(
                        rs.getObject("capacity_counter_id", UUID.class),
                        rs.getString("business_type"),
                        rs.getInt("max_units"),
                        rs.getInt("occupied_units"),
                        rs.getLong("version"),
                        rs.getTimestamp("updated_at").toInstant()))
                .list();
    }
}
