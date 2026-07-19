package com.serviceos.workorder.infrastructure;

import com.serviceos.workorder.api.WorkOrderConfigurationBinding;
import com.serviceos.workorder.api.WorkOrderConfigurationBindingQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** 从 WorkOrder 权威表读取冻结 Bundle 绑定。 */
@Repository
final class JdbcWorkOrderConfigurationBindingQuery implements WorkOrderConfigurationBindingQuery {
    private final JdbcClient jdbc;

    JdbcWorkOrderConfigurationBindingQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkOrderConfigurationBinding> find(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT id, project_id, configuration_bundle_id, configuration_bundle_digest
                          FROM wo_work_order
                         WHERE tenant_id = :tenantId AND id = :workOrderId
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new WorkOrderConfigurationBinding(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_digest")))
                .optional();
    }
}
