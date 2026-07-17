package com.serviceos.identity.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.identity.api.PrincipalEmploymentLifecyclePort;
import com.serviceos.identity.domain.SecurityPrincipal;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * 任职终止触发的主体停用：不加 HTTP 幂等，由组织命令事务边界保证只执行一次。
 */
@Service
final class DefaultPrincipalEmploymentLifecycleService implements PrincipalEmploymentLifecyclePort {
    private final IdentityDirectoryRepository directory;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultPrincipalEmploymentLifecycleService(
            IdentityDirectoryRepository directory,
            AuditAppender audit,
            Clock clock
    ) {
        this.directory = directory;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void disableForEmploymentTermination(
            String tenantId, UUID principalId, String actorId, String reason, String correlationId
    ) {
        SecurityPrincipal principal = directory.findByIdForUpdate(tenantId, principalId).orElse(null);
        if (principal == null || principal.status() == SecurityPrincipal.Status.DISABLED) {
            return;
        }
        var now = clock.instant();
        String requestDigest = Sha256.digest("employment-termination|" + principalId + "|" + reason);
        if (!directory.advanceLifecycle(tenantId, principalId, principal.version(),
                "DISABLED", actorId, reason, now)) {
            throw new IllegalStateException("主体版本已被并发修改");
        }
        directory.insertLifecycleEvent(UUID.randomUUID(), tenantId, principalId, "DISABLED",
                principal.version() + 1, reason, actorId, requestDigest, correlationId, now);
        audit.append(new AuditEntry(UUID.randomUUID(), tenantId, actorId, "DISABLED",
                "organization.manageMembership", "SecurityPrincipal", principalId.toString(), "ALLOW",
                List.of(), "employment-termination", "SUCCEEDED", null, requestDigest, correlationId, now));
    }
}
