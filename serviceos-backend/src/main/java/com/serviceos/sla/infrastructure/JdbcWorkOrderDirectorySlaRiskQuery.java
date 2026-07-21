package com.serviceos.sla.infrastructure;

import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskQuery;
import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * M434/M442：批量聚合开放 SLA 风险，并按 OPEN/BREACHED 口径筛选工单。
 *
 * <p>open = RUNNING∪BREACHED；breached ⊆ open。旁载仅返回 openCount&gt;0 的行。</p>
 */
@Component
final class JdbcWorkOrderDirectorySlaRiskQuery implements WorkOrderDirectorySlaRiskQuery {

    private static final String SIDE_CAR_SQL = """
            SELECT work_order_id AS work_order_id,
                   COUNT(*) FILTER (WHERE status IN ('RUNNING', 'BREACHED')) AS open_count,
                   COUNT(*) FILTER (WHERE status = 'BREACHED') AS breached_count
              FROM sla_instance
             WHERE tenant_id = :tenantId
               AND work_order_id IN (:workOrderIds)
               AND status IN ('RUNNING', 'BREACHED')
             GROUP BY work_order_id
            HAVING COUNT(*) FILTER (WHERE status IN ('RUNNING', 'BREACHED')) > 0
            """;

    private static final String FILTER_OPEN_TENANT_WIDE = """
            SELECT DISTINCT work_order_id
              FROM sla_instance
             WHERE tenant_id = :tenantId
               AND work_order_id IS NOT NULL
               AND status IN ('RUNNING', 'BREACHED')
            """;

    private static final String FILTER_OPEN_PROJECT_SCOPED = """
            SELECT DISTINCT work_order_id
              FROM sla_instance
             WHERE tenant_id = :tenantId
               AND work_order_id IS NOT NULL
               AND project_id IN (:projectIds)
               AND status IN ('RUNNING', 'BREACHED')
            """;

    private static final String FILTER_BREACHED_TENANT_WIDE = """
            SELECT DISTINCT work_order_id
              FROM sla_instance
             WHERE tenant_id = :tenantId
               AND work_order_id IS NOT NULL
               AND status = 'BREACHED'
            """;

    private static final String FILTER_BREACHED_PROJECT_SCOPED = """
            SELECT DISTINCT work_order_id
              FROM sla_instance
             WHERE tenant_id = :tenantId
               AND work_order_id IS NOT NULL
               AND project_id IN (:projectIds)
               AND status = 'BREACHED'
            """;

    private final JdbcClient jdbc;

    JdbcWorkOrderDirectorySlaRiskQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WorkOrderDirectorySlaRiskSummary> findOpenRisks(
            String tenantId, Collection<UUID> workOrderIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workOrderIds, "workOrderIds must not be null");
        if (workOrderIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = List.copyOf(workOrderIds);
        List<WorkOrderDirectorySlaRiskSummary> result = new ArrayList<>();
        jdbc.sql(SIDE_CAR_SQL)
                .param("tenantId", tenantId)
                .param("workOrderIds", ids)
                .query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    int open = rs.getInt("open_count");
                    int breached = rs.getInt("breached_count");
                    if (workOrderId != null && open > 0) {
                        result.add(new WorkOrderDirectorySlaRiskSummary(workOrderId, open, breached));
                    }
                    return null;
                })
                .list();
        return List.copyOf(result);
    }

    @Override
    public List<UUID> findWorkOrderIdsBySlaRisk(
            String tenantId,
            String slaRisk,
            boolean tenantWide,
            Collection<UUID> projectIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(slaRisk, "slaRisk must not be null");
        Objects.requireNonNull(projectIds, "projectIds must not be null");
        if (!tenantWide && projectIds.isEmpty()) {
            return List.of();
        }
        String sql = switch (slaRisk) {
            case "OPEN" -> tenantWide ? FILTER_OPEN_TENANT_WIDE : FILTER_OPEN_PROJECT_SCOPED;
            case "BREACHED" -> tenantWide ? FILTER_BREACHED_TENANT_WIDE : FILTER_BREACHED_PROJECT_SCOPED;
            default -> throw new IllegalArgumentException("slaRisk is invalid");
        };
        List<UUID> ids = new ArrayList<>();
        var spec = jdbc.sql(sql).param("tenantId", tenantId);
        if (!tenantWide) {
            spec = spec.param("projectIds", List.copyOf(projectIds));
        }
        spec.query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    if (workOrderId != null) {
                        ids.add(workOrderId);
                    }
                    return null;
                })
                .list();
        return List.copyOf(ids);
    }
}
