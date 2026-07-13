package com.serviceos.audit.infrastructure;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
final class JdbcAuditAppender implements AuditAppender {
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    JdbcAuditAppender(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuditEntry entry) {
        jdbc.sql("""
                        INSERT INTO aud_audit_record (
                            audit_id, tenant_id, actor_id, action_name, capability_code, target_type,
                            target_id, decision_code, matched_grant_ids, authorization_policy_version,
                            result_code, error_code,
                            request_digest, correlation_id, occurred_at
                        ) VALUES (
                            :auditId, :tenantId, :actorId, :action, :capabilityCode, :targetType,
                            :targetId, :decision, CAST(:matchedGrantIds AS jsonb), :policyVersion,
                            :result, :errorCode,
                            :requestDigest, :correlationId, :occurredAt
                        )
                        """)
                .param("auditId", entry.auditId())
                .param("tenantId", entry.tenantId())
                .param("actorId", entry.actorId())
                .param("action", entry.action())
                .param("capabilityCode", entry.capabilityCode(), java.sql.Types.VARCHAR)
                .param("targetType", entry.targetType())
                .param("targetId", entry.targetId())
                .param("decision", entry.decision(), java.sql.Types.VARCHAR)
                .param("matchedGrantIds", toJson(entry.matchedGrantIds()))
                .param("policyVersion", entry.authorizationPolicyVersion(), java.sql.Types.VARCHAR)
                .param("result", entry.result())
                .param("errorCode", entry.errorCode(), java.sql.Types.VARCHAR)
                .param("requestDigest", entry.requestDigest())
                .param("correlationId", entry.correlationId())
                .param("occurredAt", entry.occurredAt())
                .update();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Audit metadata cannot be serialized", exception);
        }
    }
}
