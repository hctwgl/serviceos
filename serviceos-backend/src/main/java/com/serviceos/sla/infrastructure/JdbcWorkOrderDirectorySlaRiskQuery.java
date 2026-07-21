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
 * M434：批量聚合本页工单的开放 SLA 风险计数。
 *
 * <p>open = RUNNING∪BREACHED；breached ⊆ open。仅返回 openCount&gt;0 的行。</p>
 */
@Component
final class JdbcWorkOrderDirectorySlaRiskQuery implements WorkOrderDirectorySlaRiskQuery {

    private static final String SQL = """
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
        jdbc.sql(SQL)
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
}
