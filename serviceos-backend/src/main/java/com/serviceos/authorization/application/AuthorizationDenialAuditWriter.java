package com.serviceos.authorization.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * 拒绝审计使用独立事务，确保外层业务事务因 AccessDenied 回滚后仍保留安全证据。
 */
@Component
class AuthorizationDenialAuditWriter {
    private final AuditAppender audit;
    private final Clock clock;

    AuthorizationDenialAuditWriter(AuditAppender audit, Clock clock) {
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(
            CurrentPrincipal principal,
            AuthorizationRequest request,
            String correlationId,
            String reasonCode,
            String policyVersion
    ) {
        String targetId = request.resourceId() == null ? request.tenantId() : request.resourceId();
        String digest = Sha256.digest(request.capability() + "|" + request.resourceType() + "|" + targetId);
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "AUTHORIZATION_DENIED", request.capability(), request.resourceType(), targetId,
                "DENY", java.util.List.of(), policyVersion, "REJECTED", reasonCode,
                digest, correlationId, clock.instant()));
    }
}
