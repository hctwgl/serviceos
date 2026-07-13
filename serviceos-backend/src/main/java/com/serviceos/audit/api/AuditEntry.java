package com.serviceos.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 不可变审计摘要。requestDigest 用于证明输入一致性，不把原始敏感请求复制到审计表。
 */
public record AuditEntry(
        UUID auditId,
        String tenantId,
        String actorId,
        String action,
        String capabilityCode,
        String targetType,
        String targetId,
        String decision,
        List<String> matchedGrantIds,
        String authorizationPolicyVersion,
        String result,
        String errorCode,
        String requestDigest,
        String correlationId,
        Instant occurredAt
) {
    public AuditEntry {
        matchedGrantIds = matchedGrantIds == null ? List.of() : List.copyOf(matchedGrantIds);
    }
}
