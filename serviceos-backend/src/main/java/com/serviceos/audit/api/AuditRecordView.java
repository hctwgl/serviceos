package com.serviceos.audit.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 审计只读视图；不含请求正文与敏感字段。 */
public record AuditRecordView(
        UUID auditId,
        String actorId,
        String actionName,
        String capabilityCode,
        String targetType,
        String targetId,
        String decisionCode,
        String resultCode,
        String errorCode,
        String correlationId,
        Instant occurredAt
) {
    public AuditRecordView {
        Objects.requireNonNull(auditId, "auditId");
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(actionName, "actionName");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(resultCode, "resultCode");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
