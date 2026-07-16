package com.serviceos.integration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.OutboundDeliveryQueryService;
import com.serviceos.integration.api.OutboundDeliveryQueueItem;
import com.serviceos.integration.api.OutboundDeliveryQueuePage;
import com.serviceos.integration.api.OutboundDeliveryQueueQuery;
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
 * 外发队列先解析实时项目范围，再执行单条 integration 范围化 SQL；游标绑定范围与全部筛选。
 */
@Service
final class DefaultOutboundDeliveryQueryService implements OutboundDeliveryQueryService {
    private static final String READ = "integration.readOutbound";
    private static final Set<String> STATUSES = Set.of(
            "PENDING", "SENDING", "DELIVERED", "ACKNOWLEDGED", "REJECTED", "FAILED_FINAL", "UNKNOWN");
    private static final Set<String> MESSAGE_TYPES = Set.of("SUBMIT_CLIENT_REVIEW");

    private final OutboundDeliveryRepository deliveries;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final Clock clock;

    DefaultOutboundDeliveryQueryService(
            OutboundDeliveryRepository deliveries,
            AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes,
            Clock clock
    ) {
        this.deliveries = deliveries;
        this.authorization = authorization;
        this.projectScopes = projectScopes;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public OutboundDeliveryQueuePage list(
            CurrentPrincipal principal, String correlationId, OutboundDeliveryQueueQuery query
    ) {
        Objects.requireNonNull(query, "query must not be null");
        validateLimit(query.limit());
        String status = normalize(query.status(), STATUSES, "status", "UNKNOWN");
        String messageType = normalize(
                query.businessMessageType(), MESSAGE_TYPES, "businessMessageType", null);
        QueryScope scope = query.projectId() == null
                ? collectionScope(principal, correlationId)
                : projectScope(principal, correlationId, query.projectId());
        Cursor cursor = decode(
                query.cursor(),
                scope.digest(),
                query.projectId(),
                status,
                messageType,
                query.sourceWorkOrderId(),
                query.sourceReviewCaseId());
        List<OutboundDeliveryQueueItem> fetched = deliveries.findQueuePage(
                principal.tenantId(),
                scope.tenantWide(),
                scope.projectIds(),
                status,
                messageType,
                query.sourceWorkOrderId(),
                query.sourceReviewCaseId(),
                cursor == null ? null : cursor.createdAt(),
                cursor == null ? null : cursor.deliveryId(),
                query.limit() + 1);
        boolean more = fetched.size() > query.limit();
        List<OutboundDeliveryQueueItem> selected =
                more ? fetched.subList(0, query.limit()) : fetched;
        OutboundDeliveryQueueItem last = more ? selected.getLast() : null;
        return new OutboundDeliveryQueuePage(
                selected,
                last == null ? null : encode(
                        scope.digest(),
                        query.projectId(),
                        status,
                        messageType,
                        query.sourceWorkOrderId(),
                        query.sourceReviewCaseId(),
                        last.createdAt(),
                        last.deliveryId()),
                clock.instant());
    }

    private QueryScope projectScope(
            CurrentPrincipal principal, String correlationId, UUID projectId
    ) {
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "OutboundDelivery", "collection", projectId.toString()),
                correlationId);
        return new QueryScope(
                false, List.of(projectId), Sha256.digest("PROJECTS:" + projectId));
    }

    private QueryScope collectionScope(CurrentPrincipal principal, String correlationId) {
        AuthorizedProjectScope scope =
                projectScopes.require(principal, READ, "OutboundDelivery", correlationId);
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
            String messageType,
            UUID sourceWorkOrderId,
            UUID sourceReviewCaseId,
            Instant createdAt,
            UUID deliveryId
    ) {
        String raw = String.join(
                "|",
                scopeDigest,
                nullable(projectId),
                status,
                nullable(messageType),
                nullable(sourceWorkOrderId),
                nullable(sourceReviewCaseId),
                createdAt.toString(),
                deliveryId.toString());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(
            String value,
            String scopeDigest,
            UUID projectId,
            String status,
            String messageType,
            UUID sourceWorkOrderId,
            UUID sourceReviewCaseId
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 8
                    || !scopeDigest.equals(parts[0])
                    || !nullable(projectId).equals(parts[1])
                    || !status.equals(parts[2])
                    || !nullable(messageType).equals(parts[3])
                    || !nullable(sourceWorkOrderId).equals(parts[4])
                    || !nullable(sourceReviewCaseId).equals(parts[5])) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[6]), UUID.fromString(parts[7]));
        } catch (RuntimeException exception) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "cursor is invalid for the requested outbound delivery scope");
        }
    }

    private static String nullable(Object value) {
        return value == null ? "-" : value.toString();
    }

    private record Cursor(Instant createdAt, UUID deliveryId) {
    }

    private record QueryScope(boolean tenantWide, List<UUID> projectIds, String digest) {
        private QueryScope {
            projectIds = List.copyOf(projectIds);
        }
    }
}
