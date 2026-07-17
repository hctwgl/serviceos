package com.serviceos.integration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.InboundEnvelopeQueueItem;
import com.serviceos.integration.api.InboundEnvelopeQueuePage;
import com.serviceos.integration.api.InboundEnvelopeQueueQuery;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.api.InboundMessageQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderQueryService;
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
 * 入站摘要查询始终按服务端 tenant 和实时 project/tenant scope 授权。
 *
 * <p>队列查询先解析实时项目范围，再执行单条 integration 范围化 SQL；仅返回已绑定
 * projectId 的 Envelope，游标绑定范围与全部筛选。</p>
 */
@Service
final class DefaultInboundMessageQueryService implements InboundMessageQueryService {
    private static final String READ = "integration.readInbound";
    private static final Set<String> PROCESSING_STATUSES =
            Set.of("RECEIVED", "COMPLETED", "REJECTED");
    private static final Set<String> MESSAGE_TYPES =
            Set.of("CREATE_WORK_ORDER", "RECORD_CLIENT_REVIEW_RESULT");

    private final InboundMessageRepository messages;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final WorkOrderQueryService workOrders;
    private final Clock clock;

    DefaultInboundMessageQueryService(
            InboundMessageRepository messages,
            AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes,
            WorkOrderQueryService workOrders,
            Clock clock
    ) {
        this.messages = messages;
        this.authorization = authorization;
        this.projectScopes = projectScopes;
        this.workOrders = workOrders;
        this.clock = clock;
    }

    @Override
    public InboundEnvelopeView getEnvelope(
            CurrentPrincipal principal,
            String correlationId,
            UUID envelopeId
    ) {
        InboundEnvelopeView view = messages.findEnvelope(principal.tenantId(), envelopeId)
                .map(InboundMessageRepository.InboundEnvelopeRecord::view)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "InboundEnvelope does not exist"));
        requireRead(principal, correlationId, "InboundEnvelope", envelopeId.toString(), view.projectId());
        return view;
    }

    @Override
    public CanonicalMessageView getCanonicalMessage(
            CurrentPrincipal principal,
            String correlationId,
            UUID messageId
    ) {
        CanonicalMessageView view = messages.findCanonical(principal.tenantId(), messageId)
                .map(InboundMessageRepository.CanonicalMessageRecord::view)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "CanonicalMessage does not exist"));
        requireRead(principal, correlationId, "CanonicalMessage", messageId.toString(), view.projectId());
        return view;
    }

    @Override
    @Transactional(readOnly = true)
    public InboundEnvelopeQueuePage list(
            CurrentPrincipal principal, String correlationId, InboundEnvelopeQueueQuery query
    ) {
        Objects.requireNonNull(query, "query must not be null");
        validateLimit(query.limit());
        String processingStatus = normalize(
                query.processingStatus(), PROCESSING_STATUSES, "processingStatus", "RECEIVED");
        String messageType = normalize(query.messageType(), MESSAGE_TYPES, "messageType", null);
        String resultType = normalizeText(query.resultType(), "resultType", 80);
        String resultId = normalizeText(query.resultId(), "resultId", 160);
        QueryScope scope = query.projectId() == null
                ? collectionScope(principal, correlationId)
                : projectScope(principal, correlationId, query.projectId());
        Cursor cursor = decode(
                query.cursor(),
                scope.digest(),
                query.projectId(),
                processingStatus,
                messageType,
                resultType,
                resultId,
                query.canonicalMessageId());
        List<InboundEnvelopeQueueItem> fetched = messages.findQueuePage(
                principal.tenantId(),
                scope.tenantWide(),
                scope.projectIds(),
                processingStatus,
                messageType,
                resultType,
                resultId,
                query.canonicalMessageId(),
                cursor == null ? null : cursor.receivedAt(),
                cursor == null ? null : cursor.envelopeId(),
                query.limit() + 1);
        boolean more = fetched.size() > query.limit();
        List<InboundEnvelopeQueueItem> selected =
                more ? fetched.subList(0, query.limit()) : fetched;
        InboundEnvelopeQueueItem last = more ? selected.getLast() : null;
        return new InboundEnvelopeQueuePage(
                selected,
                last == null ? null : encode(
                        scope.digest(),
                        query.projectId(),
                        processingStatus,
                        messageType,
                        resultType,
                        resultId,
                        query.canonicalMessageId(),
                        last.receivedAt(),
                        last.inboundEnvelopeId()),
                clock.instant());
    }

    @Override
    public List<InboundEnvelopeView> listForWorkOrder(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit
    ) {
        validateLimit(limit);
        WorkOrderDetail workOrder = workOrders.get(principal, correlationId, workOrderId);
        UUID projectId = workOrder.workOrder().projectId();
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "WorkOrder", workOrderId.toString(), projectId.toString()),
                correlationId);
        return messages.listEnvelopesByWorkOrder(
                        principal.tenantId(), projectId, workOrderId, limit)
                .stream()
                .map(InboundMessageRepository.InboundEnvelopeRecord::view)
                .toList();
    }

    private QueryScope projectScope(
            CurrentPrincipal principal, String correlationId, UUID projectId
    ) {
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), "InboundEnvelope", "collection", projectId.toString()),
                correlationId);
        return new QueryScope(
                false, List.of(projectId), Sha256.digest("PROJECTS:" + projectId));
    }

    private QueryScope collectionScope(CurrentPrincipal principal, String correlationId) {
        AuthorizedProjectScope scope =
                projectScopes.require(principal, READ, "InboundEnvelope", correlationId);
        return new QueryScope(
                scope.tenantWide(),
                scope.projectIds().stream()
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList(),
                scope.scopeDigest());
    }

    private void requireRead(
            CurrentPrincipal principal,
            String correlationId,
            String resourceType,
            String resourceId,
            UUID projectId
    ) {
        if (projectId == null) {
            authorization.require(principal, AuthorizationRequest.tenantCapability(
                    READ, principal.tenantId(), resourceType, resourceId), correlationId);
            return;
        }
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), resourceType, resourceId, projectId.toString()), correlationId);
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

    private static String normalizeText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
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
            String processingStatus,
            String messageType,
            String resultType,
            String resultId,
            UUID canonicalMessageId,
            Instant receivedAt,
            UUID envelopeId
    ) {
        String raw = String.join(
                "|",
                scopeDigest,
                nullable(projectId),
                processingStatus,
                nullable(messageType),
                nullable(resultType),
                nullable(resultId),
                nullable(canonicalMessageId),
                receivedAt.toString(),
                envelopeId.toString());
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(
            String value,
            String scopeDigest,
            UUID projectId,
            String processingStatus,
            String messageType,
            String resultType,
            String resultId,
            UUID canonicalMessageId
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 9
                    || !scopeDigest.equals(parts[0])
                    || !nullable(projectId).equals(parts[1])
                    || !processingStatus.equals(parts[2])
                    || !nullable(messageType).equals(parts[3])
                    || !nullable(resultType).equals(parts[4])
                    || !nullable(resultId).equals(parts[5])
                    || !nullable(canonicalMessageId).equals(parts[6])) {
                throw new IllegalArgumentException();
            }
            return new Cursor(Instant.parse(parts[7]), UUID.fromString(parts[8]));
        } catch (RuntimeException exception) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "cursor is invalid for the requested inbound envelope scope");
        }
    }

    private static String nullable(Object value) {
        return value == null ? "-" : value.toString();
    }

    private record Cursor(Instant receivedAt, UUID envelopeId) {
    }

    private record QueryScope(boolean tenantWide, List<UUID> projectIds, String digest) {
        private QueryScope {
            projectIds = List.copyOf(projectIds);
        }
    }
}
