package com.serviceos.workorder.application;

import com.serviceos.workorder.api.ExternalWorkOrderPointer;
import com.serviceos.workorder.api.WorkOrderExternalLookup;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 按 tenant/client/externalOrder 定位工单指针。 */
@Service
final class JdbcWorkOrderExternalLookup implements WorkOrderExternalLookup {
    private final JdbcClient jdbc;

    JdbcWorkOrderExternalLookup(JdbcClient jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Optional<ExternalWorkOrderPointer> findByExternalOrder(
            String tenantId,
            String clientCode,
            String externalOrderCode
    ) {
        String safeTenant = required(tenantId, "tenantId");
        String safeClient = required(clientCode, "clientCode");
        String safeOrder = required(externalOrderCode, "externalOrderCode");
        return jdbc.sql("""
                SELECT id, project_id, status, version,
                       configuration_bundle_id, configuration_bundle_digest
                  FROM wo_work_order
                 WHERE tenant_id = :tenantId
                   AND client_code = :clientCode
                   AND external_order_code = :externalOrderCode
                """)
                .param("tenantId", safeTenant)
                .param("clientCode", safeClient)
                .param("externalOrderCode", safeOrder)
                .query((rs, rowNum) -> new ExternalWorkOrderPointer(
                        rs.getObject("id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("status"),
                        rs.getLong("version"),
                        rs.getObject("configuration_bundle_id", UUID.class),
                        rs.getString("configuration_bundle_digest")))
                .optional();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
