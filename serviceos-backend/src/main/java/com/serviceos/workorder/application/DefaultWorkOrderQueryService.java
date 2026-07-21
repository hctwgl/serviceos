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
import com.serviceos.workorder.api.WorkOrderMaskedContactView;
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
    private static final String NETWORK_TASK_READ = "networkTask.read";
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
        // M429：列表项已通过项目范围授权；补齐服务端脱敏客户联系（不 soft-omit）。
        List<WorkOrderView> enriched = selected.stream()
                .map(item -> withMaskedContact(principal.tenantId(), item))
                .toList();
        WorkOrderView last = more ? enriched.getLast() : null;
        return new WorkOrderPage(enriched, last == null ? null : encodeCursor(scope.scopeDigest(),
                filterDigest, last.receivedAt(), last.id()), clock.instant());
    }

    @Override @Transactional(readOnly = true)
    public WorkOrderDetail get(CurrentPrincipal principal, String correlationId, UUID workOrderId) {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        WorkOrderView workOrder = queries.findById(principal.tenantId(), workOrderId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "工单不存在"));
        authorization.require(principal, AuthorizationRequest.projectCapability(READ, principal.tenantId(),
                "WorkOrder", workOrder.id().toString(), workOrder.projectId().toString()), correlationId);
        // M429：详情与目录契约对齐，返回脱敏联系、不含原文。
        return new WorkOrderDetail(withMaskedContact(principal.tenantId(), workOrder), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrderMaskedContactView getMaskedContact(
            CurrentPrincipal principal, String correlationId, UUID workOrderId
    ) {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        // 先按非 PII 概要鉴权，失败关闭时不暴露工单是否含联系字段。
        WorkOrderView workOrder = queries.findById(principal.tenantId(), workOrderId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "工单不存在"));
        authorization.require(principal, AuthorizationRequest.projectCapability(READ, principal.tenantId(),
                "WorkOrder", workOrder.id().toString(), workOrder.projectId().toString()), correlationId);
        return maskRawContact(principal.tenantId(), workOrderId);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkOrderMaskedContactView getMaskedContactForNetwork(
            CurrentPrincipal principal, String correlationId, UUID networkId, UUID workOrderId
    ) {
        Objects.requireNonNull(networkId, "networkId must not be null");
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        // 网点范围：强制 NETWORK networkTask.read；ACTIVE 责任由 Network Portal 编排层先证明。
        authorization.require(principal, AuthorizationRequest.networkCapability(
                NETWORK_TASK_READ,
                principal.tenantId(),
                "ServiceNetwork",
                networkId.toString(),
                networkId.toString()), correlationId);
        return maskRawContact(principal.tenantId(), workOrderId);
    }

    private WorkOrderMaskedContactView maskRawContact(String tenantId, UUID workOrderId) {
        WorkOrderQueryRepository.RawCustomerContact raw = queries
                .findRawCustomerContact(tenantId, workOrderId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "工单不存在"));
        return new WorkOrderMaskedContactView(
                raw.workOrderId(),
                maskName(raw.customerName()),
                maskPhone(raw.customerMobile()),
                maskAddress(raw.serviceAddress()));
    }

    /** M429：在已授权的 WorkOrderView 上附着脱敏联系；原文不进入视图。 */
    private WorkOrderView withMaskedContact(String tenantId, WorkOrderView view) {
        WorkOrderMaskedContactView contact = maskRawContact(tenantId, view.id());
        return new WorkOrderView(
                view.id(),
                view.tenantId(),
                view.projectId(),
                view.clientCode(),
                view.brandCode(),
                view.serviceProductCode(),
                view.externalOrderCode(),
                view.status(),
                view.configurationBundleId(),
                view.configurationBundleCode(),
                view.configurationBundleVersion(),
                view.configurationBundleDigest(),
                view.provinceCode(),
                view.cityCode(),
                view.districtCode(),
                view.externalDispatchedAt(),
                view.receivedAt(),
                view.activatedAt(),
                view.fulfilledAt(),
                view.version(),
                contact.maskedCustomerName(),
                contact.maskedCustomerPhone(),
                contact.maskedServiceAddress());
    }

    /** 姓名仅保留首字，其余以 * 代替。 */
    static String maskName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        return trimmed.charAt(0) + "*".repeat(Math.min(trimmed.length() - 1, 3));
    }

    /** 手机号保留后四位。 */
    static String maskPhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.trim();
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.min(digits.length() - 4, 7)) + digits.substring(digits.length() - 4);
    }

    /** 地址仅保留前 6 个可见字符。 */
    static String maskAddress(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 6) {
            return trimmed.charAt(0) + "***";
        }
        return trimmed.substring(0, 6) + "***";
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
