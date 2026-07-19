package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.PricingShadowSnapshotView;
import com.serviceos.configuration.application.PricingShadowSnapshotQueryRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** cfg_calculation_snapshot SHADOW 只读 JDBC 适配器。 */
@Repository
class JdbcPricingShadowSnapshotQueryRepository implements PricingShadowSnapshotQueryRepository {
    private final JdbcClient jdbc;

    JdbcPricingShadowSnapshotQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PricingShadowSnapshotView> listByWorkOrder(String tenantId, UUID workOrderId) {
        return jdbc.sql("""
                SELECT snapshot_id, work_order_id, project_id, source_event_id, source_event_type,
                       pricing_key, currency, total_amount_minor, mode, correlation_id, created_at
                  FROM cfg_calculation_snapshot
                 WHERE tenant_id = :tenantId
                   AND work_order_id = :workOrderId
                 ORDER BY created_at DESC, pricing_key ASC
                """)
                .param("tenantId", tenantId)
                .param("workOrderId", workOrderId)
                .query((rs, rowNum) -> new PricingShadowSnapshotView(
                        rs.getObject("snapshot_id", UUID.class),
                        rs.getObject("work_order_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("source_event_id", UUID.class),
                        rs.getString("source_event_type"),
                        rs.getString("pricing_key"),
                        rs.getString("currency"),
                        rs.getLong("total_amount_minor"),
                        rs.getString("mode"),
                        rs.getString("correlation_id"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
