package com.serviceos.workorder.infrastructure;

import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 从 WorkOrder 权威表读取表达式所需的最小事实集合。
 *
 * <p>查询必须同时携带 tenantId 与 workOrderId，避免仅凭全局 UUID 读取其他租户事实。
 * JDBC 细节留在 infrastructure，调用方只能依赖 workorder::api 暴露的只读端口。</p>
 */
@Repository
final class JdbcWorkOrderExpressionContextQuery implements WorkOrderExpressionContextQuery {
    private final JdbcClient jdbc;

    JdbcWorkOrderExpressionContextQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkOrderExpressionContext> find(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT id, client_code, brand_code, service_product_code,
                               province_code, city_code, district_code
                          FROM wo_work_order
                         WHERE tenant_id = :tenantId AND id = :workOrderId
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new WorkOrderExpressionContext(
                        rs.getObject("id", UUID.class),
                        rs.getString("client_code"),
                        rs.getString("brand_code"),
                        rs.getString("service_product_code"),
                        rs.getString("province_code"),
                        rs.getString("city_code"),
                        rs.getString("district_code")))
                .optional();
    }
}
