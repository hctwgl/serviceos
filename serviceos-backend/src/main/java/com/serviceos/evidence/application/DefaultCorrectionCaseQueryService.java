package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.evidence.api.CorrectionCaseQueryService;
import com.serviceos.evidence.api.CorrectionCaseQueueItem;
import com.serviceos.evidence.api.CorrectionCaseQueuePage;
import com.serviceos.evidence.api.CorrectionCaseQueueQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 整改队列先解析实时项目范围，再执行单条 evidence 范围化 SQL；游标绑定范围与全部筛选。
 */
@Service
final class DefaultCorrectionCaseQueryService implements CorrectionCaseQueryService {
    private static final String READ = "evidence.read";
    private static final Set<String> STATUSES =
            Set.of("OPEN", "IN_PROGRESS", "RESUBMITTED", "CLOSED", "WAIVED");

    private final CorrectionCaseRepository corrections;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final Clock clock;

    DefaultCorrectionCaseQueryService(
            CorrectionCaseRepository corrections,
            AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes,
            Clock clock
    ) {
        this.corrections = corrections;
        this.authorization = authorization;
        this.projectScopes = projectScopes;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CorrectionCaseQueuePage list(
            CurrentPrincipal principal, String correlationId, CorrectionCaseQueueQuery query
    ) {
        Objects.requireNonNull(query, "query must not be null");
        validateLimit(query.limit());
        String status = normalize(query.status(), STATUSES, "status", "OPEN");
        QueryScope scope = query.projectId() == null
                ? collectionScope(principal, correlationId)
                : projectScope(principal, correlationId, query.projectId());
        Cursor cursor = decode(
                query.cursor(),
                scope.digest(),
                query.projectId(),
                status,
                query.taskId(),
                query.sourceReviewCaseId());
        List<CorrectionCaseQueueItem> fetched = corrections.findQueuePage(
                principal.tenantId(),
                scope.tenantWide(),
                scope.projectIds(),
                status,
                query.taskId(),
                query.sourceReviewCaseId(),
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.correctionCaseId(),
                query.limit() + 1);
        boolean more = fetched.size() > query.limit();
        List<CorrectionCaseQueueItem> selected =
                more ? fetched.subList(0, query.limit()) : fetched;
        CorrectionCaseQueueItem last = more ? selected.getLast() : null;
        return new CorrectionCaseQueuePage(
                selected,
                last == null ? null : encode(
                        scope.digest(),
                        query.projectId(),
                        status,
                        query.taskId(),
                        query.sourceReviewCaseId(),
                        last.createdAt(),
                        last.correctionCaseId()),
                clock.instant());
    }

    private QueryScope projectScope(
            CurrentPrincipal principal, String correlationId, UUID projectId
    ) {
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "CorrectionCase", "collection", projectId.toString()),
                correlationId);
        return new QueryScope(
                false, List.of(projectId), Sha256.digest("PROJECTS:" + projectId));
    }

    private QueryScope collectionScope(CurrentPrincipal principal, String correlationId) {
        AuthorizedProjectScope scope =
                projectScopes.require(principal, READ, "CorrectionCase", correlationId);
        return new QueryScope(
                scope.tenantWide(),
                scope.projectIds().stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList(),
                scope.scopeDigest());
    }

    private static String normalize(
            String value, Set<String> accepted, String field, String defaultValue
    ) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!accepted.contains(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return normalized;
    }

    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED, "limit must be between 1 and 100");
        }
    }

    private static String encode(
            String scopeDigest,
            UUID projectId,
            String status,
            UUID taskId,
            UUID sourceReviewCaseId,
            Instant createdAt,
            UUID correctionCaseId
    ) {
        String raw = String.join(
                "|",
                scopeDigest,
                nullable(projectId),
                status,
                nullable(taskId),
                nullable(sourceReviewCaseId),
                createdAt.toString(),
                correctionCaseId.toString());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(
            String value,
            String scopeDigest,
            UUID projectId,
            String status,
            UUID taskId,
            UUID sourceReviewCaseId
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 7
                    || !scopeDigest.equals(parts[0])
                    || !nullable(projectId).equals(parts[1])
                    || !status.equals(parts[2])
                    || !nullable(taskId).equals(parts[3])
                    || !nullable(sourceReviewCaseId).equals(parts[4])) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[5]), UUID.fromString(parts[6]));
        } catch (RuntimeException exception) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "cursor is invalid for the requested correction scope");
        }
    }

    private static String nullable(Object value) {
        return value == null ? "-" : value.toString();
    }

    private record Cursor(Instant createdAt, UUID correctionCaseId) {
    }

    private record QueryScope(boolean tenantWide, List<UUID> projectIds, String digest) {
        private QueryScope {
            projectIds = List.copyOf(projectIds);
        }
    }
}
