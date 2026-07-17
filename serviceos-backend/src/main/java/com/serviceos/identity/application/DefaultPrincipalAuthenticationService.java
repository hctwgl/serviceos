package com.serviceos.identity.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.identity.api.AuthenticatedIdentity;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalAuthenticationService;
import com.serviceos.identity.domain.SecurityPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 受信 OIDC 身份解析入口。
 *
 * <p>同一 `(tenant, issuer, subject)` 先获取事务 advisory lock，再读取或创建 Principal、Profile、
 * IdentityLink、生命周期事实和审计；任何一步失败全部回滚。未知 SERVICE 主体和未命中显式
 * tenant/client JIT 策略的 USER 主体均失败关闭，不能把 JWT subject 直接当内部 Principal。</p>
 */
@Service
final class DefaultPrincipalAuthenticationService implements PrincipalAuthenticationService {
    private final IdentityDirectoryRepository directory;
    private final JitRegistrationPolicy registrationPolicy;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultPrincipalAuthenticationService(
            IdentityDirectoryRepository directory,
            JitRegistrationPolicy registrationPolicy,
            AuditAppender audit,
            Clock clock
    ) {
        this.directory = directory;
        this.registrationPolicy = registrationPolicy;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public String resolveOrRegister(AuthenticatedIdentity identity, String correlationId) {
        String requestDigest = Sha256.digest(identity.tenantId() + "|" + identity.issuer()
                + "|" + identity.subject() + "|" + identity.clientId());
        directory.lockIdentityKey(identity.tenantId(), identity.issuer(), identity.subject());
        var existing = directory.findByExternalIdentity(
                identity.tenantId(), identity.issuer(), identity.subject());
        if (existing.isPresent()) {
            return existing.orElseThrow().requireActive().id().toString();
        }

        if (identity.principalType() != CurrentPrincipal.PrincipalType.USER
                || !registrationPolicy.allows(identity.tenantId(), identity.clientId())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "外部身份尚未登记");
        }

        Instant now = clock.instant();
        UUID principalId = UUID.randomUUID();
        SecurityPrincipal principal = SecurityPrincipal.register(
                principalId, identity.tenantId(), SecurityPrincipal.Type.USER,
                identity.displayName(), now);
        directory.insertPrincipal(principal, "jit-registration");
        directory.insertIdentityLink(
                UUID.randomUUID(), identity.tenantId(), principalId, identity.issuer(),
                identity.subject(), identity.clientId(), "jit-registration", now);
        directory.insertLifecycleEvent(
                UUID.randomUUID(), identity.tenantId(), principalId, "REGISTERED", 1,
                "OIDC_JIT", "jit-registration", requestDigest, correlationId, now);
        audit.append(new AuditEntry(
                UUID.randomUUID(), identity.tenantId(), "jit-registration",
                "PRINCIPAL_REGISTERED", "identity.jitRegister", "SecurityPrincipal",
                principalId.toString(), "ALLOW", List.of(), "oidc-resource-server",
                "SUCCEEDED", null, requestDigest, correlationId, now));
        return principalId.toString();
    }
}
