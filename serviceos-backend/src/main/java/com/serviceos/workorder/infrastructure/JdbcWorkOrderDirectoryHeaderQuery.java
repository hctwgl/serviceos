package com.serviceos.workorder.infrastructure;

import com.serviceos.workorder.api.WorkOrderDirectoryHeader;
import com.serviceos.workorder.api.WorkOrderDirectoryHeaderQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 从 {@code wo_work_order} 读取目录用非 PII 头字段。
 * 查询必须同时携带 tenantId 与 workOrderId。
 */
@Repository
final class JdbcWorkOrderDirectoryHeaderQuery implements WorkOrderDirectoryHeaderQuery {
    private final JdbcClient jdbc;

    JdbcWorkOrderDirectoryHeaderQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<WorkOrderDirectoryHeader> find(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                        SELECT id, brand_code, service_product_code,
                               province_code, city_code, district_code, received_at
                          FROM wo_work_order
                         WHERE tenant_id = :tenantId AND id = :workOrderId
                        """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> {
                    Timestamp received = rs.getTimestamp("received_at");
                    Instant receivedAt = received == null ? null : received.toInstant();
                    return new WorkOrderDirectoryHeader(
                            rs.getObject("id", UUID.class),
                            rs.getString("brand_code"),
                            rs.getString("service_product_code"),
                            rs.getString("province_code"),
                            rs.getString("city_code"),
                            rs.getString("district_code"),
                            receivedAt);
                })
                .optional();
    }
}
