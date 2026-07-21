package com.serviceos.identity.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 主体授权拒绝安全活动条目；不含 digest / matched grants。 */
public record PrincipalAuthorizationDenialItem(
        UUID auditId,
        UUID principalId,
        String capabilityCode,
        String targetType,
        String targetId,
        String decisionCode,
        String resultCode,
        String errorCode,
        String correlationId,
        Instant occurredAt
) {
    public PrincipalAuthorizationDenialItem {
        Objects.requireNonNull(auditId, "auditId");
        Objects.requireNonNull(principalId, "principalId");
        Objects.requireNonNull(capabilityCode, "capabilityCode");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(decisionCode, "decisionCode");
        Objects.requireNonNull(resultCode, "resultCode");
        Objects.requireNonNull(correlationId, "correlationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        // errorCode 可为 null（历史行缺省时前端展示为 —）
    }
}
