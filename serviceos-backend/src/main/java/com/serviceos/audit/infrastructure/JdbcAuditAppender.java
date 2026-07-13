package com.serviceos.audit.infrastructure;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
final class JdbcAuditAppender implements AuditAppender {
    private final JdbcClient jdbc;

    JdbcAuditAppender(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void append(AuditEntry entry) {
        jdbc.sql("""
                        INSERT INTO aud_audit_record (
                            audit_id, tenant_id, actor_id, action_name, target_type,
                            target_id, result_code, request_digest, correlation_id, occurred_at
                        ) VALUES (
                            :auditId, :tenantId, :actorId, :action, :targetType,
                            :targetId, :result, :requestDigest, :correlationId, :occurredAt
                        )
                        """)
                .params(Map.of(
                        "auditId", entry.auditId(),
                        "tenantId", entry.tenantId(),
                        "actorId", entry.actorId(),
                        "action", entry.action(),
                        "targetType", entry.targetType(),
                        "targetId", entry.targetId(),
                        "result", entry.result(),
                        "requestDigest", entry.requestDigest(),
                        "correlationId", entry.correlationId(),
                        "occurredAt", entry.occurredAt()))
                .update();
    }
}
