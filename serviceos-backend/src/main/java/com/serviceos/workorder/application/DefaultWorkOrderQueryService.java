package com.serviceos.workorder.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderPage;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import com.serviceos.workorder.api.WorkOrderView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 实时授权范围与稳定游标绑定的 WorkOrder 查询实现。 */
@Service
final class DefaultWorkOrderQueryService implements WorkOrderQueryService {
    private static final String READ = "workOrder.read";
    private static final Set<String> STATUSES = Set.of(
            "RECEIVED", "ACTIVE", "SUSPENDED", "FULFILLED", "CANCELLED", "CLOSED");
    private final WorkOrderQueryRepository queries;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final Clock clock;

    DefaultWorkOrderQueryService(WorkOrderQueryRepository queries, AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes, Clock clock) {
        this.queries = queries; this.authorization = authorization;
        this.projectScopes = projectScopes; this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public WorkOrderPage list(CurrentPrincipal principal, String correlationId, WorkOrderQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        validateLimit(query.limit());
        String clientCode = normalizeCode(query.clientCode(), "clientCode");
        String status = normalizeStatus(query.status());
        String externalOrderCode = normalizeCode(query.externalOrderCode(), "externalOrderCode");
        AuthorizedProjectScope scope = projectScopes.require(principal, READ, "WorkOrder", correlationId);
        if (query.projectId() != null && !scope.tenantWide() && !scope.projectIds().contains(query.projectId())) {
            authorization.require(principal, AuthorizationRequest.projectCapability(READ, principal.tenantId(),
                    "WorkOrder", query.projectId().toString(), query.projectId().toString()), correlationId);
            throw new IllegalStateException("工单项目范围拒绝未能失败关闭");
        }
        String filterDigest = Sha256.digest("clientCode=" + nullable(clientCode) + "|projectId="
                + nullable(query.projectId()) + "|status=" + nullable(status)
                + "|externalOrderCode=" + nullable(externalOrderCode));
        Cursor cursor = decodeCursor(query.cursor(), scope.scopeDigest(), filterDigest);
        List<UUID> projectIds = scope.projectIds().stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        List<WorkOrderView> fetched = queries.findPage(principal.tenantId(), scope.tenantWide(), projectIds,
                clientCode, query.projectId(), status, externalOrderCode,
                cursor == null ? null : cursor.receivedAt(),
                cursor == null ? null : cursor.id(), query.limit() + 1);
        boolean more = fetched.size() > query.limit();
        List<WorkOrderView> selected = more ? fetched.subList(0, query.limit()) : fetched;
        WorkOrderView last = more ? selected.getLast() : null;
        return new WorkOrderPage(selected, last == null ? null : encodeCursor(scope.scopeDigest(),
                filterDigest, last.receivedAt(), last.id()), clock.instant());
    }

    @Override @Transactional(readOnly = true)
    public WorkOrderDetail get(CurrentPrincipal principal, String correlationId, UUID workOrderId) {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        WorkOrderView workOrder = queries.findById(principal.tenantId(), workOrderId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "工单不存在"));
        authorization.require(principal, AuthorizationRequest.projectCapability(READ, principal.tenantId(),
                "WorkOrder", workOrder.id().toString(), workOrder.projectId().toString()), correlationId);
        return new WorkOrderDetail(workOrder, clock.instant());
    }

    private static String normalizeCode(String value, String field) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 128)
            throw new IllegalArgumentException(field + " is invalid");
        return value;
    }
    private static String normalizeStatus(String value) {
        if (value == null) return null;
        if (!STATUSES.contains(value)) throw new IllegalArgumentException("status is invalid");
        return value;
    }
    private static void validateLimit(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
    }
    private static String encodeCursor(String scope, String filter, Instant receivedAt, UUID id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (scope + "|" + filter + "|" + receivedAt + "|" + id).getBytes(StandardCharsets.UTF_8));
    }
    private static Cursor decodeCursor(String value, String scope, String filter) {
        if (value == null) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", -1);
            if (parts.length != 4 || !scope.equals(parts[0]) || !filter.equals(parts[1])) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[2]), UUID.fromString(parts[3]));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("cursor is invalid for the requested work order scope", exception);
        }
    }
    private static String nullable(Object value) { return value == null ? "-" : value.toString(); }
    private record Cursor(Instant receivedAt, UUID id) {}
}
