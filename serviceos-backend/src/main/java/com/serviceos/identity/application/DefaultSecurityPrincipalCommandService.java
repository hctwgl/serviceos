package com.serviceos.identity.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.IdentityAuthorizationEvidence;
import com.serviceos.identity.api.IdentityAuthorizationPort;
import com.serviceos.identity.api.PrincipalPersonaView;
import com.serviceos.identity.api.SecurityPrincipalCommandService;
import com.serviceos.identity.api.SecurityPrincipalView;
import com.serviceos.identity.domain.SecurityPrincipal;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 主体目录命令编排。
 *
 * <p>每个命令在同一事务内完成：授权 → 幂等抢占 → 锁定主体/身份键 → 版本迁移 →
 * 只追加生命周期事实 → 审计 → 幂等结果。身份绑定按 identity advisory lock 再主体行锁的固定顺序，
 * 生命周期/Profile/Persona 按主体行锁串行，失败不留下半绑定、覆盖历史或伪成功。</p>
 */
@Service
final class DefaultSecurityPrincipalCommandService implements SecurityPrincipalCommandService {
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build();

    private final IdentityDirectoryRepository directory;
    private final IdentityAuthorizationPort authorization;
    private final IdempotencyService idempotency;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultSecurityPrincipalCommandService(
            IdentityDirectoryRepository directory,
            IdentityAuthorizationPort authorization,
            IdempotencyService idempotency,
            AuditAppender audit,
            Clock clock
    ) {
        this.directory = directory;
        this.authorization = authorization;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SecurityPrincipalView register(
            CurrentPrincipal actor,
            CommandMetadata metadata,
            String displayName,
            String employeeNumber,
            String personaType
    ) {
        displayName = requireText(displayName, "displayName", 200);
        employeeNumber = normalizeOptional(employeeNumber, "employeeNumber", 128);
        String normalizedPersona = personaType == null || personaType.isBlank()
                ? null
                : requirePersonaType(personaType);
        UUID principalId = UUID.randomUUID();
        var input = new RegisterInput(displayName, employeeNumber, normalizedPersona);
        CommandExecution execution = begin(actor, metadata, "identity.register", "identity.register",
                principalId, input);
        if (execution.replay()) {
            String resourceId = execution.decision().resourceId()
                    .orElseThrow(() -> new BusinessProblem(
                            ProblemCode.IDENTITY_PROFILE_CONFLICT, "登记幂等重放缺少资源标识"));
            return requirePrincipal(actor.tenantId(), UUID.fromString(resourceId)).toView();
        }

        Instant now = clock.instant();
        SecurityPrincipal principal = SecurityPrincipal.register(
                principalId, actor.tenantId(), SecurityPrincipal.Type.USER,
                displayName, employeeNumber, now);
        try {
            directory.insertPrincipal(principal, actor.principalId());
        } catch (RuntimeException exception) {
            if (isUniqueViolation(exception)) {
                throw new BusinessProblem(ProblemCode.IDENTITY_PROFILE_CONFLICT, "工号已存在于本租户");
            }
            throw exception;
        }
        long version = 1L;
        if (normalizedPersona != null) {
            UUID personaId = UUID.randomUUID();
            if (!directory.addPersonaAndAdvance(
                    actor.tenantId(), principalId, 1L, personaId, normalizedPersona,
                    now, null, actor.principalId(), now)) {
                throw versionConflict();
            }
            version = 2L;
        }
        complete(actor, metadata, execution, principalId, version,
                "REGISTERED", "identity.register", "admin-register", now);
        return requirePrincipal(actor.tenantId(), principalId).toView();
    }

    @Override
    @Transactional
    public SecurityPrincipalView linkIdentity(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion,
            String issuer, String subject, String clientId
    ) {
        requireExpectedVersion(expectedVersion);
        issuer = requireText(issuer, "issuer", 512);
        subject = requireText(subject, "subject", 255);
        clientId = normalizeOptional(clientId, "clientId", 128);
        var input = new LinkInput(principalId, expectedVersion, issuer, subject, clientId);
        CommandExecution execution = begin(actor, metadata, "identity.link", "identity.manageLinks",
                principalId, input);
        if (execution.replay()) return requirePrincipal(actor.tenantId(), principalId).toView();

        directory.lockIdentityKey(actor.tenantId(), issuer, subject);
        if (directory.findByExternalIdentity(actor.tenantId(), issuer, subject).isPresent()) {
            throw new BusinessProblem(ProblemCode.IDENTITY_LINK_CONFLICT, "外部身份已经绑定");
        }
        SecurityPrincipal principal = lockedActive(actor.tenantId(), principalId, expectedVersion);
        Instant now = clock.instant();
        directory.insertIdentityLink(UUID.randomUUID(), actor.tenantId(), principalId,
                issuer, subject, clientId, actor.principalId(), now);
        if (!directory.advanceVersion(actor.tenantId(), principalId, expectedVersion, now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, principalId, expectedVersion + 1,
                "IDENTITY_LINKED", "identity.manageLinks", null, now);
        return requirePrincipal(actor.tenantId(), principalId).toView();
    }

    @Override
    @Transactional
    public SecurityPrincipalView disable(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId,
            long expectedVersion, String reason
    ) {
        return changeLifecycle(actor, metadata, principalId, expectedVersion, reason, true);
    }

    @Override
    @Transactional
    public SecurityPrincipalView enable(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId,
            long expectedVersion, String reason
    ) {
        return changeLifecycle(actor, metadata, principalId, expectedVersion, reason, false);
    }

    @Override
    @Transactional
    public SecurityPrincipalView updateProfile(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion,
            String displayName, String employeeNumber
    ) {
        requireExpectedVersion(expectedVersion);
        displayName = requireText(displayName, "displayName", 200);
        employeeNumber = normalizeOptional(employeeNumber, "employeeNumber", 128);
        var input = new ProfileInput(principalId, expectedVersion, displayName, employeeNumber);
        CommandExecution execution = begin(actor, metadata, "identity.updateProfile", "identity.manageProfile",
                principalId, input);
        if (execution.replay()) return requirePrincipal(actor.tenantId(), principalId).toView();

        lockedActive(actor.tenantId(), principalId, expectedVersion);
        Instant now = clock.instant();
        if (!directory.updateProfile(actor.tenantId(), principalId, expectedVersion,
                displayName, employeeNumber, actor.principalId(), now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, principalId, expectedVersion + 1,
                "PROFILE_UPDATED", "identity.manageProfile", null, now);
        return requirePrincipal(actor.tenantId(), principalId).toView();
    }

    @Override
    @Transactional
    public PrincipalPersonaView addPersona(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId, long expectedVersion,
            String personaType, Instant validFrom, Instant validTo
    ) {
        requireExpectedVersion(expectedVersion);
        personaType = requirePersonaType(personaType);
        if (validFrom == null || (validTo != null && !validTo.isAfter(validFrom))) {
            throw new IllegalArgumentException("persona validity is invalid");
        }
        var input = new PersonaInput(principalId, expectedVersion, personaType, validFrom, validTo);
        CommandExecution execution = begin(actor, metadata, "identity.addPersona", "identity.manageProfile",
                principalId, input);
        if (execution.replay()) {
            UUID personaId = UUID.fromString(execution.decision().resourceId().orElseThrow());
            return directory.findPersona(actor.tenantId(), personaId).orElseThrow(() ->
                    new IllegalStateException("幂等结果引用的 Persona 不存在"));
        }

        lockedActive(actor.tenantId(), principalId, expectedVersion);
        Instant now = clock.instant();
        UUID personaId = UUID.randomUUID();
        if (!directory.addPersonaAndAdvance(actor.tenantId(), principalId, expectedVersion,
                personaId, personaType, validFrom, validTo, actor.principalId(), now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, principalId, expectedVersion + 1,
                "PERSONA_ADDED", "identity.manageProfile", null, now, personaId.toString());
        return directory.findPersona(actor.tenantId(), personaId).orElseThrow();
    }

    private SecurityPrincipalView changeLifecycle(
            CurrentPrincipal actor, CommandMetadata metadata, UUID principalId,
            long expectedVersion, String reason, boolean disable
    ) {
        requireExpectedVersion(expectedVersion);
        reason = requireText(reason, "reason", 500);
        if (disable && actor.principalId().equals(principalId.toString())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "不能通过当前会话停用自身主体");
        }
        String operation = disable ? "identity.disable" : "identity.enable";
        var input = new LifecycleInput(principalId, expectedVersion, reason);
        CommandExecution execution = begin(actor, metadata, operation, "identity.manageLifecycle",
                principalId, input);
        if (execution.replay()) return requirePrincipal(actor.tenantId(), principalId).toView();

        SecurityPrincipal current = locked(actor.tenantId(), principalId, expectedVersion);
        if (disable && current.status() != SecurityPrincipal.Status.ACTIVE) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "主体已经停用");
        }
        if (!disable && current.status() != SecurityPrincipal.Status.DISABLED) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "主体已经启用");
        }
        Instant now = clock.instant();
        if (!directory.advanceLifecycle(actor.tenantId(), principalId, expectedVersion,
                disable ? "DISABLED" : "ACTIVE", actor.principalId(), reason, now)) {
            throw versionConflict();
        }
        complete(actor, metadata, execution, principalId, expectedVersion + 1,
                disable ? "DISABLED" : "ENABLED", "identity.manageLifecycle", reason, now);
        return requirePrincipal(actor.tenantId(), principalId).toView();
    }

    private CommandExecution begin(
            CurrentPrincipal actor, CommandMetadata metadata, String operation,
            String capability, UUID principalId, Object input
    ) {
        IdentityAuthorizationEvidence decision = authorization.requireTenantCapability(
                actor, capability, principalId.toString(), metadata.correlationId());
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        String requestDigest = Sha256.digest(canonicalJson(input));
        IdempotencyDecision idempotencyDecision = idempotency.begin(context, operation, requestDigest);
        return new CommandExecution(operation, requestDigest, decision, idempotencyDecision);
    }

    private void complete(
            CurrentPrincipal actor, CommandMetadata metadata, CommandExecution execution,
            UUID principalId, long newVersion, String eventType, String capability,
            String reason, Instant now
    ) {
        complete(actor, metadata, execution, principalId, newVersion, eventType, capability, reason, now,
                principalId.toString());
    }

    private void complete(
            CurrentPrincipal actor, CommandMetadata metadata, CommandExecution execution,
            UUID principalId, long newVersion, String eventType, String capability,
            String reason, Instant now, String resourceId
    ) {
        directory.insertLifecycleEvent(UUID.randomUUID(), actor.tenantId(), principalId, eventType,
                newVersion, reason, actor.principalId(), execution.requestDigest(),
                metadata.correlationId(), now);
        audit.append(new AuditEntry(UUID.randomUUID(), actor.tenantId(), actor.principalId(),
                eventType, capability, "SecurityPrincipal", principalId.toString(), "ALLOW",
                execution.authorization().matchedGrantIds(), execution.authorization().policyVersion(),
                "SUCCEEDED", null, execution.requestDigest(), metadata.correlationId(), now));
        CommandContext context = new CommandContext(actor.tenantId(), actor.principalId(),
                metadata.correlationId(), metadata.idempotencyKey());
        idempotency.complete(context, execution.operation(), resourceId,
                Sha256.digest(resourceId + "|" + newVersion));
    }

    private SecurityPrincipal lockedActive(String tenantId, UUID principalId, long expectedVersion) {
        return locked(tenantId, principalId, expectedVersion).requireActive();
    }

    private SecurityPrincipal locked(String tenantId, UUID principalId, long expectedVersion) {
        SecurityPrincipal principal = directory.findByIdForUpdate(tenantId, principalId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "主体不存在"));
        if (principal.version() != expectedVersion) throw versionConflict();
        return principal;
    }

    private SecurityPrincipal requirePrincipal(String tenantId, UUID principalId) {
        return directory.findById(tenantId, principalId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "主体不存在"));
    }

    private static boolean isReplay(IdempotencyDecision decision) {
        return decision.kind() == IdempotencyDecision.Kind.REPLAY;
    }

    private static void requireExpectedVersion(long version) {
        if (version < 1) throw new IllegalArgumentException("expectedVersion must be positive");
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String normalizeOptional(String value, String field, int max) {
        if (value == null) return null;
        return requireText(value, field, max);
    }

    private static String requirePersonaType(String value) {
        String normalized = requireText(value, "personaType", 40);
        try {
            return PersonaType.valueOf(normalized).name();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("personaType is invalid", exception);
        }
    }

    private static boolean isUniqueViolation(Throwable exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            String name = cursor.getClass().getName();
            String message = cursor.getMessage() == null ? "" : cursor.getMessage();
            if (name.contains("DuplicateKey") || message.contains("uq_idn_person_profile_employee")) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static BusinessProblem versionConflict() {
        return new BusinessProblem(ProblemCode.VERSION_CONFLICT, "主体版本已被并发修改");
    }

    private static String canonicalJson(Object value) {
        try {
            return CANONICAL_JSON.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("主体命令不能序列化", exception);
        }
    }

    private enum PersonaType { INTERNAL_EMPLOYEE, NETWORK_MEMBER, TECHNICIAN, CONSUMER, SERVICE_ACCOUNT }

    private record RegisterInput(String displayName, String employeeNumber, String personaType) {}
    private record LinkInput(UUID principalId, long expectedVersion, String issuer, String subject, String clientId) {}
    private record LifecycleInput(UUID principalId, long expectedVersion, String reason) {}
    private record ProfileInput(UUID principalId, long expectedVersion, String displayName, String employeeNumber) {}
    private record PersonaInput(UUID principalId, long expectedVersion, String personaType, Instant validFrom, Instant validTo) {}

    private record CommandExecution(
            String operation,
            String requestDigest,
            IdentityAuthorizationEvidence authorization,
            IdempotencyDecision decision
    ) {
        boolean replay() { return isReplay(decision); }
    }
}
