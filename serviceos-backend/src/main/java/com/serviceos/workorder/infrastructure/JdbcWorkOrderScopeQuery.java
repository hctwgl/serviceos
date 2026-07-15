package com.serviceos.workorder.infrastructure;

import com.serviceos.workorder.api.WorkOrderScope;
import com.serviceos.workorder.api.WorkOrderScopeQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 工单范围适配器。tenantId 必须参与查询，调用方不能仅凭全局 UUID 推断其他租户的工单范围。
 */
@Repository
final class JdbcWorkOrderScopeQuery implements WorkOrderScopeQuery {
    private final JdbcClient jdbc;

    JdbcWorkOrderScopeQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkOrderScope> find(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT id, project_id
                          FROM wo_work_order
                         WHERE tenant_id=:tenantId AND id=:workOrderId
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, row) -> new WorkOrderScope(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class)))
                .optional();
    }
}
