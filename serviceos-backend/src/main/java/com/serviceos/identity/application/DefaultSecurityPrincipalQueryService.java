package com.serviceos.identity.application;

import com.serviceos.audit.api.AuditQueryService;
import com.serviceos.audit.api.AuditRecordView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.IdentityAuthorizationPort;
import com.serviceos.identity.api.IdentityLinkView;
import com.serviceos.identity.api.PrincipalChangeTimelineContributor;
import com.serviceos.identity.api.PrincipalChangeTimelineItem;
import com.serviceos.identity.api.PrincipalChangeTimelinePage;
import com.serviceos.identity.api.PrincipalLoginEventPage;
import com.serviceos.identity.api.PrincipalLoginEventView;
import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.identity.api.SecurityPrincipalDetail;
import com.serviceos.identity.api.SecurityPrincipalPage;
import com.serviceos.identity.api.SecurityPrincipalQueryService;
import com.serviceos.identity.domain.SecurityPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 普通目录与敏感身份绑定分权读取；所有查询先做 tenant 能力校验，再执行 tenant 约束 SQL。 */
@Service
final class DefaultSecurityPrincipalQueryService implements SecurityPrincipalQueryService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private final IdentityDirectoryRepository directory;
    private final IdentityDirectoryQueryRepository queries;
    private final IdentityAuthorizationPort authorization;
    private final AuditQueryService audits;
    private final PrincipalPersonaQuery personas;
    private final List<PrincipalChangeTimelineContributor> timelineContributors;
    private final Clock clock;

    DefaultSecurityPrincipalQueryService(
            IdentityDirectoryRepository directory,
            IdentityDirectoryQueryRepository queries,
            IdentityAuthorizationPort authorization,
            AuditQueryService audits,
            PrincipalPersonaQuery personas,
            List<PrincipalChangeTimelineContributor> timelineContributors,
            Clock clock
    ) {
        this.directory = directory;
        this.queries = queries;
        this.authorization = authorization;
        this.audits = audits;
        this.personas = personas;
        this.timelineContributors = List.copyOf(timelineContributors);
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityPrincipalPage list(
            CurrentPrincipal actor, String correlationId, String query,
            String status, String cursorValue, int limit
    ) {
        require(actor, correlationId, "identity.read", "directory");
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        String normalizedQuery = normalizeOptional(query, 200, "query");
        String normalizedStatus = normalizeStatus(status);
        String filterDigest = Sha256.digest("query=" + nullable(normalizedQuery) + "|status=" + nullable(normalizedStatus));
        Cursor cursor = decodeCursor(cursorValue, filterDigest);
        List<SecurityPrincipal> fetched = queries.findPage(
                actor.tenantId(), normalizedQuery, normalizedStatus,
                cursor == null ? null : cursor.displayName(), cursor == null ? null : cursor.id(), limit + 1);
        boolean more = fetched.size() > limit;
        List<SecurityPrincipal> selected = more ? fetched.subList(0, limit) : fetched;
        SecurityPrincipal last = more ? selected.getLast() : null;
        return new SecurityPrincipalPage(
                selected.stream().map(SecurityPrincipal::toView).toList(),
                last == null ? null : encodeCursor(filterDigest, last.displayName(), last.id()),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public SecurityPrincipalDetail get(CurrentPrincipal actor, String correlationId, UUID principalId) {
        require(actor, correlationId, "identity.read", principalId.toString());
        SecurityPrincipal principal = requirePrincipal(actor.tenantId(), principalId);
        return new SecurityPrincipalDetail(
                principal.toView(), directory.findPersonas(actor.tenantId(), principalId), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<IdentityLinkView> identities(
            CurrentPrincipal actor, String correlationId, UUID principalId
    ) {
        require(actor, correlationId, "identity.readSensitive", principalId.toString());
        requirePrincipal(actor.tenantId(), principalId);
        return directory.findIdentityLinks(actor.tenantId(), principalId);
    }

    @Override
    @Transactional(readOnly = true)
    public PrincipalLoginEventPage recentLogins(
            CurrentPrincipal actor, String correlationId, UUID principalId, Integer limit
    ) {
        require(actor, correlationId, "identity.read", principalId.toString());
        requirePrincipal(actor.tenantId(), principalId);
        int effective = limit == null ? 20 : limit;
        if (effective < 1 || effective > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
        return new PrincipalLoginEventPage(
                directory.listLoginEvents(actor.tenantId(), principalId, effective),
                clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public PrincipalChangeTimelinePage changeTimeline(
            CurrentPrincipal actor, String correlationId, UUID principalId, Integer limit
    ) {
        require(actor, correlationId, "identity.read", principalId.toString());
        requirePrincipal(actor.tenantId(), principalId);
        int effective = limit == null ? 50 : limit;
        if (effective < 1 || effective > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        int fetch = Math.min(100, effective * 2);
        List<PrincipalChangeTimelineItem> merged = new ArrayList<>();
        List<String> omittedSources = new ArrayList<>();
        for (IdentityDirectoryRepository.LifecycleEventRecord event : directory.listLifecycleEvents(
                actor.tenantId(), principalId, fetch)) {
            merged.add(PrincipalChangeTimelineItem.of(
                    "LIFECYCLE",
                    event.eventType(),
                    lifecycleSummary(event.eventType(), event.reason()),
                    event.actorId(),
                    "SUCCEEDED",
                    event.correlationId(),
                    event.principalVersion(),
                    event.occurredAt(),
                    event.eventId()));
        }
        for (AuditRecordView audit : audits.listByTarget(
                actor.tenantId(), "SecurityPrincipal", principalId.toString(), fetch).items()) {
            // 生命周期与登录已有专用源；审计只补充授权拒绝等旁路事实，避免重复 PROFILE/LOGIN。
            if (isRedundantAudit(audit.actionName())) {
                continue;
            }
            merged.add(PrincipalChangeTimelineItem.of(
                    "AUDIT",
                    audit.actionName(),
                    auditSummary(audit),
                    audit.actorId(),
                    audit.resultCode(),
                    audit.correlationId(),
                    null,
                    audit.occurredAt(),
                    audit.auditId()));
        }
        for (PrincipalLoginEventView login : directory.listLoginEvents(
                actor.tenantId(), principalId, fetch)) {
            merged.add(PrincipalChangeTimelineItem.of(
                    "LOGIN",
                    "LOGIN_SUCCEEDED",
                    "OIDC 登录成功 · 客户端 " + login.clientId(),
                    principalId.toString(),
                    login.outcome(),
                    "login",
                    null,
                    login.occurredAt(),
                    login.loginEventId()));
        }
        // 跨聚合贡献源：缺权 soft-omit，不因缺 organization/authorization 读权而失败关闭整页。
        for (PrincipalChangeTimelineContributor contributor : timelineContributors) {
            if (!authorization.allowsTenantCapability(
                    actor, contributor.requiredCapability(), principalId.toString(), correlationId)) {
                omittedSources.add(contributor.source());
                continue;
            }
            merged.addAll(contributor.listForPrincipal(actor.tenantId(), principalId, fetch));
        }
        merged.sort(Comparator
                .comparing(PrincipalChangeTimelineItem::occurredAt).reversed()
                .thenComparing(item -> item.refId().toString()));
        if (merged.size() > effective) {
            merged = new ArrayList<>(merged.subList(0, effective));
        }
        return new PrincipalChangeTimelinePage(
                resolveActorDisplayNames(actor.tenantId(), merged),
                omittedSources.stream().distinct().sorted().toList(),
                clock.instant());
    }

    private List<PrincipalChangeTimelineItem> resolveActorDisplayNames(
            String tenantId, List<PrincipalChangeTimelineItem> items
    ) {
        Set<UUID> actorIds = new HashSet<>();
        for (PrincipalChangeTimelineItem item : items) {
            parseUuid(item.actorId()).ifPresent(actorIds::add);
        }
        Map<UUID, String> names = personas.displayNames(tenantId, actorIds);
        List<PrincipalChangeTimelineItem> resolved = new ArrayList<>(items.size());
        for (PrincipalChangeTimelineItem item : items) {
            String display = parseUuid(item.actorId()).map(names::get).orElse(null);
            resolved.add(item.withActorDisplayName(display));
        }
        return resolved;
    }

    private static java.util.Optional<UUID> parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static boolean isRedundantAudit(String actionName) {
        return "PRINCIPAL_REGISTERED".equals(actionName)
                || "IDENTITY_LINKED".equals(actionName)
                || "PROFILE_UPDATED".equals(actionName)
                || "PERSONA_ADDED".equals(actionName)
                || "DISABLED".equals(actionName)
                || "ENABLED".equals(actionName)
                || "PRINCIPAL_LOGIN_SUCCEEDED".equals(actionName);
    }

    private static String lifecycleSummary(String eventType, String reason) {
        String base = switch (eventType) {
            case "REGISTERED" -> "主体已登记";
            case "IDENTITY_LINKED" -> "已绑定外部身份";
            case "PROFILE_UPDATED" -> "档案已更新";
            case "PERSONA_ADDED" -> "已添加 Persona";
            case "DISABLED" -> "主体已停用";
            case "ENABLED" -> "主体已启用";
            default -> eventType;
        };
        if (reason == null || reason.isBlank()) {
            return base;
        }
        return base + " · " + reason.trim();
    }

    private static String auditSummary(AuditRecordView audit) {
        String decision = audit.decisionCode() == null ? "" : " · " + audit.decisionCode();
        String capability = audit.capabilityCode() == null ? "" : " · " + audit.capabilityCode();
        return audit.actionName() + decision + capability;
    }

    private void require(CurrentPrincipal actor, String correlationId, String capability, String resourceId) {
        authorization.requireTenantCapability(actor, capability, resourceId, correlationId);
    }

    private SecurityPrincipal requirePrincipal(String tenantId, UUID principalId) {
        return directory.findById(tenantId, principalId).orElseThrow(() ->
                new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "主体不存在"));
    }

    private static String normalizeOptional(String value, int max, String field) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > max) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value;
    }

    private static String normalizeStatus(String status) {
        if (status == null) return null;
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("status is invalid");
        return status;
    }

    private static String encodeCursor(String digest, String name, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (digest + "|" + name + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String value, String digest) {
        if (value == null) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 3);
            if (parts.length != 3 || !digest.equals(parts[0])) throw new IllegalArgumentException();
            return new Cursor(parts[1], UUID.fromString(parts[2]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid for the requested filters", exception);
        }
    }

    private static String nullable(String value) {
        return value == null ? "-" : value;
    }

    private record Cursor(String displayName, UUID id) {
    }
}
