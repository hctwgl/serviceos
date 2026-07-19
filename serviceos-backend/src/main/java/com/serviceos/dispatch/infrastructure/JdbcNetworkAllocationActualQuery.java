package com.serviceos.dispatch.infrastructure;

import com.serviceos.dispatch.api.NetworkAllocationActualQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 月度 NETWORK 派单实际量（ORDER_COUNT）。
 *
 * <p>口径：当月 UTC 自然月内创建的 NETWORK ServiceAssignment（ACTIVE/ENDED），
 * 并与工单 brand/project 对齐。AMOUNT/WEIGHTED_VOLUME 不在此实现。</p>
 */
@Component
final class JdbcNetworkAllocationActualQuery implements NetworkAllocationActualQuery {
    private final JdbcClient jdbc;

    JdbcNetworkAllocationActualQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Long> countMonthlyNetworkAssignments(
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
        OffsetDateTime monthStart = evaluatedAt.atOffset(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.DAYS)
                .withDayOfMonth(1);
        Instant periodStart = monthStart.toInstant();
        Instant periodEnd = monthStart.plusMonths(1).toInstant();
        List<Map.Entry<String, Long>> rows = jdbc.sql("""
                SELECT a.assignee_id AS network_id, COUNT(*) AS assignment_count
                  FROM dsp_service_assignment a
                  JOIN wo_work_order w
                    ON w.tenant_id = a.tenant_id
                   AND w.id = a.work_order_id
                 WHERE a.tenant_id = :tenantId
                   AND w.project_id = :projectId
                   AND w.brand_code = :brandCode
                   AND a.business_type = :businessType
                   AND a.responsibility_level = 'NETWORK'
                   AND a.status IN ('ACTIVE', 'ENDED')
                   AND a.created_at >= :periodStart
                   AND a.created_at < :periodEnd
                 GROUP BY a.assignee_id
                 ORDER BY a.assignee_id
                """)
                .param("tenantId", tenantId.trim())
                .param("projectId", projectId)
                .param("brandCode", brandCode.trim())
                .param("businessType", businessType.trim())
                .param("periodStart", timestamptz(periodStart))
                .param("periodEnd", timestamptz(periodEnd))
                .query((rs, rowNum) -> Map.entry(
                        rs.getString("network_id"),
                        rs.getLong("assignment_count")))
                .list();
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map.Entry<String, Long> row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return Map.copyOf(result);
    }
}
