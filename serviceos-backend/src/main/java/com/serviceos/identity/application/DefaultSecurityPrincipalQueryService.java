package com.serviceos.identity.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.IdentityAuthorizationPort;
import com.serviceos.identity.api.IdentityLinkView;
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
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 普通目录与敏感身份绑定分权读取；所有查询先做 tenant 能力校验，再执行 tenant 约束 SQL。 */
@Service
final class DefaultSecurityPrincipalQueryService implements SecurityPrincipalQueryService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "DISABLED");
    private final IdentityDirectoryRepository directory;
    private final IdentityDirectoryQueryRepository queries;
    private final IdentityAuthorizationPort authorization;
    private final Clock clock;

    DefaultSecurityPrincipalQueryService(
            IdentityDirectoryRepository directory,
            IdentityDirectoryQueryRepository queries,
            IdentityAuthorizationPort authorization,
            Clock clock
    ) {
        this.directory = directory;
        this.queries = queries;
        this.authorization = authorization;
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
