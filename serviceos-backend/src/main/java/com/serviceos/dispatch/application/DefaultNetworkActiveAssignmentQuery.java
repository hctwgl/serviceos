package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.NetworkActiveAssignmentQuery;
import com.serviceos.dispatch.api.NetworkActiveAssignmentView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ACTIVE NETWORK 责任列表：仅读 dispatch 拥有表，不穿越工单/任务模块边界。
 */
@Service
final class DefaultNetworkActiveAssignmentQuery implements NetworkActiveAssignmentQuery {
    private final JdbcClient jdbc;

    DefaultNetworkActiveAssignmentQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NetworkActiveAssignmentView> listActiveForNetwork(String tenantId, String networkId) {
        return jdbc.sql("""
                        SELECT n.service_assignment_id,
                               n.work_order_id,
                               n.task_id,
                               n.business_type,
                               n.effective_from,
                               t.assignee_id AS technician_id
                          FROM dsp_service_assignment n
                          LEFT JOIN dsp_service_assignment t
                            ON t.tenant_id = n.tenant_id
                           AND t.task_id = n.task_id
                           AND t.responsibility_level = 'TECHNICIAN'
                           AND t.status = 'ACTIVE'
                         WHERE n.tenant_id = :tenantId
                           AND n.responsibility_level = 'NETWORK'
                           AND n.assignee_id = :networkId
                           AND n.status = 'ACTIVE'
                         ORDER BY n.effective_from DESC NULLS LAST, n.service_assignment_id
                        """)
                .param("tenantId", tenantId)
                .param("networkId", networkId)
                .query((rs, rowNum) -> new NetworkActiveAssignmentView(
                        rs.getObject("service_assignment_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getObject("task_id", UUID.class),
                        rs.getString("business_type"),
                        toInstant(rs.getTimestamp("effective_from")),
                        rs.getString("technician_id")))
                .list();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
