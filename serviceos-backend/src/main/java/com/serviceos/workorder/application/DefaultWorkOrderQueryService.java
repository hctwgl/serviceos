package com.serviceos.workorder.application;

import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.AuthorizedProjectScope;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderDirectoryAssigneeQuery;
import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskQuery;
import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskSummary;
import com.serviceos.workorder.api.WorkOrderDirectoryStageQuery;
import com.serviceos.workorder.api.WorkOrderMaskedContactView;
import com.serviceos.workorder.api.WorkOrderPage;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import com.serviceos.workorder.api.WorkOrderView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 实时授权范围与稳定游标绑定的 WorkOrder 查询实现。 */
@Service
final class DefaultWorkOrderQueryService implements WorkOrderQueryService {
    private static final String READ = "workOrder.read";
    private static final String SLA_READ = "sla.read";
    private static final String NETWORK_TASK_READ = "networkTask.read";
    private static final Set<String> STATUSES = Set.of(
            "RECEIVED", "ACTIVE", "SUSPENDED", "FULFILLED", "CANCELLED", "CLOSED");
    private final WorkOrderQueryRepository queries;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final ObjectProvider<WorkOrderDirectoryStageQuery> stageQuery;
    private final ObjectProvider<WorkOrderDirectoryAssigneeQuery> assigneeQuery;
    private final ObjectProvider<WorkOrderDirectorySlaRiskQuery> slaRiskQuery;
    private final PrincipalPersonaQuery personas;
    private final Clock clock;

    DefaultWorkOrderQueryService(WorkOrderQueryRepository queries, AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes,
            ObjectProvider<WorkOrderDirectoryStageQuery> stageQuery,
            ObjectProvider<WorkOrderDirectoryAssigneeQuery> assigneeQuery,
            ObjectProvider<WorkOrderDirectorySlaRiskQuery> slaRiskQuery,
            PrincipalPersonaQuery personas, Clock clock) {
        this.queries = queries; this.authorization = authorization;
        this.projectScopes = projectScopes; this.stageQuery = stageQuery;
        this.assigneeQuery = assigneeQuery; this.slaRiskQuery = slaRiskQuery;
        this.personas = personas; this.clock = clock;
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
        // M429/M432/M433：列表项已授权；补齐脱敏联系、阶段码与当前责任人（task SPI + Persona）。
        List<UUID> ids = selected.stream().map(WorkOrderView::id).toList();
        DirectorySideCars sideCars = loadDirectorySideCars(principal.tenantId(), ids);
        List<WorkOrderView> enriched = selected.stream()
                .map(item -> withDirectoryEnrichment(principal.tenantId(), item, sideCars.forWorkOrder(item.id())))
                .toList();
        WorkOrderView last = more ? enriched.getLast() : null;
        // M434：页级 SLA 风险旁载；缺 sla.read 时省略属性（null），不伪造空成功 0。
        List<WorkOrderDirectorySlaRiskSummary> slaRiskSummaries = loadSlaRiskSummaries(
                principal, correlationId, enriched);
        return new WorkOrderPage(
                enriched,
                last == null ? null : encodeCursor(scope.scopeDigest(),
                        filterDigest, last.receivedAt(), last.id()),
                clock.instant(),
                slaRiskSummaries);
    }

    @Override @Transactional(readOnly = true)
    public WorkOrderDetail get(CurrentPrincipal principal, String correlationId, UUID workOrderId) {
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        WorkOrderView workOrder = queries.findById(principal.tenantId(), workOrderId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "工单不存在"));
        authorization.require(principal, AuthorizationRequest.projectCapability(READ, principal.tenantId(),
                "WorkOrder", workOrder.id().toString(), workOrder.projectId().toString()), correlationId);
        // M429/M432/M433：详情与目录契约对齐，返回脱敏联系、阶段与责任人、不含原文。
        DirectorySideCars sideCars = loadDirectorySideCars(principal.tenantId(), List.of(workOrderId));
        return new WorkOrderDetail(
                withDirectoryEnrichment(principal.tenantId(), workOrder, sideCars.forWorkOrder(workOrderId)),
                clock.instant());
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

    /**
     * M429/M432/M433：在已授权的 WorkOrderView 上附着脱敏联系、阶段与责任人；原文不进入视图。
     * 旁载字段由调用方批量查询后传入，缺任务/认领/档案时保持 null。
     */
    private WorkOrderView withDirectoryEnrichment(
            String tenantId, WorkOrderView view, DirectoryEnrichment enrichment
    ) {
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
                contact.maskedServiceAddress(),
                enrichment.currentStageCode(),
                enrichment.currentClaimedBy(),
                enrichment.currentAssigneeDisplayName());
    }

    private DirectorySideCars loadDirectorySideCars(String tenantId, List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return DirectorySideCars.empty();
        }
        Map<UUID, String> stages = Map.of();
        WorkOrderDirectoryStageQuery stagesPort = stageQuery.getIfAvailable();
        if (stagesPort != null) {
            stages = stagesPort.findCurrentStageCodes(tenantId, workOrderIds);
        }
        Map<UUID, String> claimedBy = Map.of();
        WorkOrderDirectoryAssigneeQuery assigneesPort = assigneeQuery.getIfAvailable();
        if (assigneesPort != null) {
            claimedBy = assigneesPort.findCurrentClaimedBy(tenantId, workOrderIds);
        }
        Set<UUID> principalIds = new HashSet<>();
        for (String raw : claimedBy.values()) {
            UUID parsed = tryParseUuid(raw);
            if (parsed != null) {
                principalIds.add(parsed);
            }
        }
        Map<UUID, String> displayNames = principalIds.isEmpty()
                ? Map.of()
                : personas.displayNames(tenantId, new ArrayList<>(principalIds));
        return new DirectorySideCars(stages, claimedBy, displayNames);
    }

    /**
     * M434：按本页工单所属项目 soft-gate PROJECT sla.read（authorize，不抛 ACCESS_DENIED）。
     * 本页任一项目允许则返回摘要列表（可空）；全部拒绝则返回 null 以省略属性。
     */
    private List<WorkOrderDirectorySlaRiskSummary> loadSlaRiskSummaries(
            CurrentPrincipal principal, String correlationId, List<WorkOrderView> items
    ) {
        if (items.isEmpty()) {
            return List.of();
        }
        Set<UUID> allowedProjects = new HashSet<>();
        for (UUID projectId : items.stream()
                .map(WorkOrderView::projectId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))) {
            AuthorizationDecision decision = authorization.authorize(
                    principal,
                    AuthorizationRequest.projectCapability(
                            SLA_READ,
                            principal.tenantId(),
                            "SlaInstance",
                            projectId.toString(),
                            projectId.toString()),
                    correlationId);
            if (decision.effect() == AuthorizationDecision.Effect.ALLOW) {
                allowedProjects.add(projectId);
            }
        }
        if (allowedProjects.isEmpty()) {
            return null;
        }
        WorkOrderDirectorySlaRiskQuery query = slaRiskQuery.getIfAvailable();
        if (query == null) {
            return List.of();
        }
        List<UUID> eligibleIds = items.stream()
                .filter(item -> allowedProjects.contains(item.projectId()))
                .map(WorkOrderView::id)
                .toList();
        return query.findOpenRisks(principal.tenantId(), eligibleIds);
    }

    private static UUID tryParseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private record DirectoryEnrichment(
            String currentStageCode,
            String currentClaimedBy,
            String currentAssigneeDisplayName
    ) {}

    private record DirectorySideCars(
            Map<UUID, String> stages,
            Map<UUID, String> claimedBy,
            Map<UUID, String> displayNames
    ) {
        static DirectorySideCars empty() {
            return new DirectorySideCars(Map.of(), Map.of(), Map.of());
        }

        DirectoryEnrichment forWorkOrder(UUID workOrderId) {
            String claimed = claimedBy.get(workOrderId);
            String displayName = null;
            UUID principalId = tryParseUuid(claimed);
            if (principalId != null) {
                displayName = displayNames.get(principalId);
            }
            return new DirectoryEnrichment(stages.get(workOrderId), claimed, displayName);
        }
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
