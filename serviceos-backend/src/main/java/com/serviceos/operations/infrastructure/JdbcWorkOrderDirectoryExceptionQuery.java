package com.serviceos.operations.infrastructure;

import com.serviceos.workorder.api.WorkOrderDirectoryExceptionQuery;
import com.serviceos.workorder.api.WorkOrderDirectoryExceptionSummary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * M450：批量聚合工单 OPEN 运营异常数（目录旁载）。
 */
@Component
final class JdbcWorkOrderDirectoryExceptionQuery implements WorkOrderDirectoryExceptionQuery {

    private static final String SIDE_CAR_SQL = """
            SELECT work_order_id AS work_order_id,
                   COUNT(*)::int AS open_count
              FROM ops_operational_exception
             WHERE tenant_id = :tenantId
               AND work_order_id IN (:workOrderIds)
               AND status = 'OPEN'
               AND work_order_id IS NOT NULL
             GROUP BY work_order_id
            HAVING COUNT(*) > 0
            """;

    private final JdbcClient jdbc;

    JdbcWorkOrderDirectoryExceptionQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<WorkOrderDirectoryExceptionSummary> findOpenCounts(
            String tenantId, Collection<UUID> workOrderIds
    ) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(workOrderIds, "workOrderIds must not be null");
        if (workOrderIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = List.copyOf(workOrderIds);
        List<WorkOrderDirectoryExceptionSummary> result = new ArrayList<>();
        jdbc.sql(SIDE_CAR_SQL)
                .param("tenantId", tenantId)
                .param("workOrderIds", ids)
                .query((rs, rowNum) -> {
                    UUID workOrderId = rs.getObject("work_order_id", UUID.class);
                    int open = rs.getInt("open_count");
                    if (workOrderId != null && open > 0) {
                        result.add(new WorkOrderDirectoryExceptionSummary(workOrderId, open));
                    }
                    return null;
                })
                .list();
        return List.copyOf(result);
    }
}
