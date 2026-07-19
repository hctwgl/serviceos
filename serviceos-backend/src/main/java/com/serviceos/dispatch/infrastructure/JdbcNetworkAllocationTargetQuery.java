package com.serviceos.dispatch.infrastructure;

import com.serviceos.dispatch.api.NetworkAllocationTargetQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/** 网点签约比例目标查询（PostgreSQL）。 */
@Component
final class JdbcNetworkAllocationTargetQuery implements NetworkAllocationTargetQuery {
    private final JdbcClient jdbc;

    JdbcNetworkAllocationTargetQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Double> listCommittedShares(
            String tenantId,
            UUID projectId,
            String brandCode,
            String businessType,
            Instant asOf
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(brandCode, "brandCode");
        Objects.requireNonNull(businessType, "businessType");
        Instant evaluatedAt = Objects.requireNonNull(asOf, "asOf");
        List<Map.Entry<String, Double>> rows = jdbc.sql("""
                SELECT DISTINCT ON (network_id)
                       network_id, committed_share
                  FROM dsp_network_allocation_target
                 WHERE tenant_id = :tenantId
                   AND project_id = :projectId
                   AND brand_code = :brandCode
                   AND business_type = :businessType
                   AND valid_from <= :asOf
                   AND (valid_to IS NULL OR valid_to > :asOf)
                 ORDER BY network_id, valid_from DESC
                """)
                .param("tenantId", tenantId.trim())
                .param("projectId", projectId)
                .param("brandCode", brandCode.trim())
                .param("businessType", businessType.trim())
                .param("asOf", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> Map.entry(
                        rs.getString("network_id"),
                        rs.getBigDecimal("committed_share").doubleValue()))
                .list();
        Map<String, Double> result = new LinkedHashMap<>();
        for (Map.Entry<String, Double> row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return Map.copyOf(result);
    }
}
