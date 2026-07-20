package com.serviceos.audit.infrastructure;

import com.serviceos.audit.api.AuditQueryService;
import com.serviceos.audit.api.AuditRecordPage;
import com.serviceos.audit.api.AuditRecordView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
class JdbcAuditQueryService implements AuditQueryService {
    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcAuditQueryService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AuditRecordPage listByTarget(
            String tenantId, String targetType, String targetId, int limit
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        List<AuditRecordView> items = jdbc.sql("""
                SELECT audit_id, actor_id, action_name, capability_code, target_type, target_id,
                       decision_code, result_code, error_code, correlation_id, occurred_at
                  FROM aud_audit_record
                 WHERE tenant_id = :tenantId
                   AND target_type = :targetType
                   AND target_id = :targetId
                 ORDER BY occurred_at DESC, audit_id DESC
                 LIMIT :limit
                """)
                .param("tenantId", tenantId)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("limit", limit)
                .query((rs, rowNum) -> new AuditRecordView(
                        rs.getObject("audit_id", UUID.class),
                        rs.getString("actor_id"),
                        rs.getString("action_name"),
                        rs.getString("capability_code"),
                        rs.getString("target_type"),
                        rs.getString("target_id"),
                        rs.getString("decision_code"),
                        rs.getString("result_code"),
                        rs.getString("error_code"),
                        rs.getString("correlation_id"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant()))
                .list();
        return new AuditRecordPage(items, clock.instant());
    }
}
