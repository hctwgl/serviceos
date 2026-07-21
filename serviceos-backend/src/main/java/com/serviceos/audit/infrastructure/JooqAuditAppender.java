package com.serviceos.audit.infrastructure;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.jooq.generated.tables.AudAuditRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import static com.serviceos.jooq.generated.tables.AudAuditRecord.AUD_AUDIT_RECORD;

@Repository
final class JooqAuditAppender implements AuditAppender {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqAuditAppender(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(AuditEntry entry) {
        AudAuditRecord table = AUD_AUDIT_RECORD;
        // matched_grant_ids 由全局 JsonbStringConverter 绑定（String -> JSONB），无需手写 CAST。
        dsl.insertInto(table)
                .set(table.AUDIT_ID, entry.auditId())
                .set(table.TENANT_ID, entry.tenantId())
                .set(table.ACTOR_ID, entry.actorId())
                .set(table.ACTION_NAME, entry.action())
                .set(table.CAPABILITY_CODE, entry.capabilityCode())
                .set(table.TARGET_TYPE, entry.targetType())
                .set(table.TARGET_ID, entry.targetId())
                .set(table.DECISION_CODE, entry.decision())
                .set(table.MATCHED_GRANT_IDS, toJson(entry.matchedGrantIds()))
                .set(table.AUTHORIZATION_POLICY_VERSION, entry.authorizationPolicyVersion())
                .set(table.RESULT_CODE, entry.result())
                .set(table.ERROR_CODE, entry.errorCode())
                .set(table.REQUEST_DIGEST, entry.requestDigest())
                .set(table.CORRELATION_ID, entry.correlationId())
                .set(table.OCCURRED_AT, entry.occurredAt())
                .execute();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Audit metadata cannot be serialized", exception);
        }
    }
}
