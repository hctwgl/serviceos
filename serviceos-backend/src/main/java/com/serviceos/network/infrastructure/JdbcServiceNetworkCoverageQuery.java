package com.serviceos.network.infrastructure;

import com.serviceos.network.api.ServiceNetworkCoverageQuery;
import com.serviceos.network.api.ServiceNetworkCoverageView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 网点 ServiceCoverage 查询（PostgreSQL）。
 */
@Component
public class JdbcServiceNetworkCoverageQuery implements ServiceNetworkCoverageQuery {
    private final JdbcClient jdbc;

    public JdbcServiceNetworkCoverageQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ServiceNetworkCoverageView> listActiveCoverage(
            String tenantId,
            Collection<String> serviceNetworkIds,
            String brandCode,
            String businessType,
            Instant asOf
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(serviceNetworkIds, "serviceNetworkIds");
        Objects.requireNonNull(brandCode, "brandCode");
        Objects.requireNonNull(businessType, "businessType");
        Instant evaluatedAt = Objects.requireNonNull(asOf, "asOf");
        if (serviceNetworkIds.isEmpty()) {
            return List.of();
        }
        // 与 ServiceNetworkDirectoryQuery 一致：按 ::text 匹配项目侧 network_id。
        List<String> networkIds = serviceNetworkIds.stream()
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .toList();
        if (networkIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT coverage_id, service_network_id, brand_code, business_type, region_code
                  FROM net_service_network_coverage
                 WHERE tenant_id = :tenantId
                   AND service_network_id::text IN (:networkIds)
                   AND brand_code = :brandCode
                   AND business_type = :businessType
                   AND coverage_status = 'ACTIVE'
                   AND valid_from <= :asOf
                   AND (valid_to IS NULL OR valid_to > :asOf)
                 ORDER BY service_network_id, region_code
                """)
                .param("tenantId", tenantId.trim())
                .param("networkIds", networkIds)
                .param("brandCode", brandCode.trim())
                .param("businessType", businessType.trim())
                .param("asOf", timestamptz(evaluatedAt))
                .query((rs, rowNum) -> new ServiceNetworkCoverageView(
                        rs.getObject("coverage_id", UUID.class),
                        rs.getObject("service_network_id", UUID.class),
                        rs.getString("brand_code"),
                        rs.getString("business_type"),
                        rs.getString("region_code")))
                .list();
    }
}
