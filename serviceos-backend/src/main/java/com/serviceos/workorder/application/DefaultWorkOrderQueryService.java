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
import com.serviceos.workorder.api.WorkOrderDirectoryServiceResponsibility;
import com.serviceos.workorder.api.WorkOrderDirectoryServiceResponsibilityQuery;
import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskQuery;
import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskSummary;
import com.serviceos.workorder.api.WorkOrderDirectoryStageQuery;
import com.serviceos.workorder.api.WorkOrderDirectoryReviewCorrectionQuery;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
    private static final String EVIDENCE_READ = "evidence.read";
    private static final String NETWORK_TASK_READ = "networkTask.read";
    /** 目录创建日筛选使用运营时区自然日，与 Network 预约日历一致。 */
    private static final ZoneId RECEIVED_DAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_RECEIVED_RANGE_DAYS = 366;
    private static final Set<String> STATUSES = Set.of(
            "RECEIVED", "ACTIVE", "SUSPENDED", "FULFILLED", "CANCELLED", "CLOSED");
    /** M446：与目录当前任务旁载一致的 ACTIVE 任务状态集。 */
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "READY", "PENDING", "CLAIMED", "RUNNING", "RETRY_WAIT", "MANUAL_INTERVENTION");
    /** M447：审核/整改运营桶。 */
    private static final Set<String> REVIEW_CORRECTION_STATUSES = Set.of(
            "REVIEW_OPEN", "CORRECTION_ACTIVE");
    private final WorkOrderQueryRepository queries;
    private final AuthorizationService authorization;
    private final ProjectScopeAuthorizationService projectScopes;
    private final ObjectProvider<WorkOrderDirectoryStageQuery> stageQuery;
    private final ObjectProvider<WorkOrderDirectoryAssigneeQuery> assigneeQuery;
    private final ObjectProvider<WorkOrderDirectoryServiceResponsibilityQuery> responsibilityQuery;
    private final ObjectProvider<WorkOrderDirectorySlaRiskQuery> slaRiskQuery;
    private final ObjectProvider<WorkOrderDirectoryReviewCorrectionQuery> reviewCorrectionQuery;
    private final PrincipalPersonaQuery personas;
    private final Clock clock;

    DefaultWorkOrderQueryService(WorkOrderQueryRepository queries, AuthorizationService authorization,
            ProjectScopeAuthorizationService projectScopes,
            ObjectProvider<WorkOrderDirectoryStageQuery> stageQuery,
            ObjectProvider<WorkOrderDirectoryAssigneeQuery> assigneeQuery,
            ObjectProvider<WorkOrderDirectoryServiceResponsibilityQuery> responsibilityQuery,
            ObjectProvider<WorkOrderDirectorySlaRiskQuery> slaRiskQuery,
            ObjectProvider<WorkOrderDirectoryReviewCorrectionQuery> reviewCorrectionQuery,
            PrincipalPersonaQuery personas, Clock clock) {
        this.queries = queries; this.authorization = authorization;
        this.projectScopes = projectScopes; this.stageQuery = stageQuery;
        this.assigneeQuery = assigneeQuery; this.responsibilityQuery = responsibilityQuery;
        this.slaRiskQuery = slaRiskQuery;
        this.reviewCorrectionQuery = reviewCorrectionQuery;
        this.personas = personas; this.clock = clock;
    }

    @Override @Transactional(readOnly = true)
    public WorkOrderPage list(CurrentPrincipal principal, String correlationId, WorkOrderQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        validateLimit(query.limit());
        String clientCode = normalizeCode(query.clientCode(), "clientCode");
        String status = normalizeStatus(query.status());
        String externalOrderCode = normalizeCode(query.externalOrderCode(), "externalOrderCode");
        // M437：区域国标码精确匹配；非法码失败关闭，不静默忽略。
        String provinceCode = normalizeRegionCode(query.provinceCode(), "provinceCode");
        String cityCode = normalizeRegionCode(query.cityCode(), "cityCode");
        String districtCode = normalizeRegionCode(query.districtCode(), "districtCode");
        // M438：当前阶段码与目录列同口径；非法码失败关闭。
        String currentStageCode = normalizeStageCode(query.currentStageCode());
        // M446：当前任务状态与目录列同口径；非法枚举失败关闭。
        String currentTaskStatus = normalizeTaskStatus(query.currentTaskStatus());
        // M440/M441：网点/师傅 ID 与目录列同口径；非法 UUID 由绑定层失败，此处仅参与 digest。
        UUID currentNetworkId = query.currentNetworkId();
        UUID currentTechnicianId = query.currentTechnicianId();
        // M442：SLA 风险口径与目录列一致；非法枚举失败关闭。
        String slaRisk = normalizeSlaRisk(query.slaRisk());
        // M443：创建日闭区间 → Asia/Shanghai 半开时间窗；非法跨度失败关闭。
        ReceivedBounds receivedBounds = normalizeReceivedBounds(query.receivedFrom(), query.receivedTo());
        // M447：审核/整改运营桶；非法枚举失败关闭。
        String reviewCorrectionStatus = normalizeReviewCorrectionStatus(query.reviewCorrectionStatus());
        AuthorizedProjectScope scope = projectScopes.require(principal, READ, "WorkOrder", correlationId);
        if (query.projectId() != null && !scope.tenantWide() && !scope.projectIds().contains(query.projectId())) {
            authorization.require(principal, AuthorizationRequest.projectCapability(READ, principal.tenantId(),
                    "WorkOrder", query.projectId().toString(), query.projectId().toString()), correlationId);
            throw new IllegalStateException("工单项目范围拒绝未能失败关闭");
        }
        String filterDigest = Sha256.digest("clientCode=" + nullable(clientCode) + "|projectId="
                + nullable(query.projectId()) + "|status=" + nullable(status)
                + "|externalOrderCode=" + nullable(externalOrderCode)
                + "|provinceCode=" + nullable(provinceCode)
                + "|cityCode=" + nullable(cityCode)
                + "|districtCode=" + nullable(districtCode)
                + "|currentStageCode=" + nullable(currentStageCode)
                + "|currentTaskStatus=" + nullable(currentTaskStatus)
                + "|currentNetworkId=" + nullable(currentNetworkId)
                + "|currentTechnicianId=" + nullable(currentTechnicianId)
                + "|slaRisk=" + nullable(slaRisk)
                + "|receivedFrom=" + nullable(query.receivedFrom())
                + "|receivedTo=" + nullable(query.receivedTo())
                + "|reviewCorrectionStatus=" + nullable(reviewCorrectionStatus));
        Cursor cursor = decodeCursor(query.cursor(), scope.scopeDigest(), filterDigest);
        List<UUID> projectIds = scope.projectIds().stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        // M438：阶段筛选经 task SPI 解析为工单 ID，再在授权 SQL 中 IN 收敛。
        boolean applyStageFilter = currentStageCode != null;
        List<UUID> stageWorkOrderIds = List.of();
        if (applyStageFilter) {
            WorkOrderDirectoryStageQuery stagesPort = stageQuery.getIfAvailable();
            if (stagesPort == null) {
                throw new IllegalStateException("工单目录阶段筛选端口不可用");
            }
            stageWorkOrderIds = stagesPort.findWorkOrderIdsByCurrentStageCode(
                    principal.tenantId(), currentStageCode, scope.tenantWide(), projectIds);
        }
        // M446：任务状态筛选经同一 task SPI，口径为最早 ACTIVE 任务 status。
        boolean applyTaskStatusFilter = currentTaskStatus != null;
        List<UUID> taskStatusWorkOrderIds = List.of();
        if (applyTaskStatusFilter) {
            WorkOrderDirectoryStageQuery stagesPort = stageQuery.getIfAvailable();
            if (stagesPort == null) {
                throw new IllegalStateException("工单目录任务状态筛选端口不可用");
            }
            taskStatusWorkOrderIds = stagesPort.findWorkOrderIdsByCurrentTaskStatus(
                    principal.tenantId(), currentTaskStatus, scope.tenantWide(), projectIds);
        }
        // M440/M441：网点/师傅筛选经 dispatch SPI 解析为工单 ID；项目范围仍由授权 SQL 收敛。
        boolean applyNetworkFilter = currentNetworkId != null;
        boolean applyTechnicianFilter = currentTechnicianId != null;
        List<UUID> networkWorkOrderIds = List.of();
        List<UUID> technicianWorkOrderIds = List.of();
        if (applyNetworkFilter || applyTechnicianFilter) {
            WorkOrderDirectoryServiceResponsibilityQuery responsibilityPort =
                    responsibilityQuery.getIfAvailable();
            if (responsibilityPort == null) {
                throw new IllegalStateException("工单目录网点/师傅筛选端口不可用");
            }
            if (applyNetworkFilter) {
                networkWorkOrderIds = responsibilityPort.findWorkOrderIdsByActiveNetworkId(
                        principal.tenantId(), currentNetworkId);
            }
            if (applyTechnicianFilter) {
                technicianWorkOrderIds = responsibilityPort.findWorkOrderIdsByActiveTechnicianId(
                        principal.tenantId(), currentTechnicianId);
            }
        }
        // M442：SLA 筛选仅在具备 sla.read 的范围内解析；无权限 → 空集失败关闭，不泄露 SLA 事实。
        boolean applySlaRiskFilter = slaRisk != null;
        List<UUID> slaRiskWorkOrderIds = List.of();
        if (applySlaRiskFilter) {
            WorkOrderDirectorySlaRiskQuery slaPort = slaRiskQuery.getIfAvailable();
            if (slaPort == null) {
                throw new IllegalStateException("工单目录 SLA 筛选端口不可用");
            }
            if (scope.tenantWide() && hasTenantSlaRead(principal, correlationId)) {
                slaRiskWorkOrderIds = slaPort.findWorkOrderIdsBySlaRisk(
                        principal.tenantId(), slaRisk, true, List.of(), clock.instant());
            } else {
                List<UUID> slaReadableProjects = projectsWithSlaRead(
                        principal, correlationId, projectIds);
                if (slaReadableProjects.isEmpty()) {
                    slaRiskWorkOrderIds = List.of();
                } else {
                    slaRiskWorkOrderIds = slaPort.findWorkOrderIdsBySlaRisk(
                            principal.tenantId(), slaRisk, false, slaReadableProjects, clock.instant());
                }
            }
        }
        // M447：审核/整改筛选仅在具备 evidence.read 的范围内解析；无权限 → 空集失败关闭。
        boolean applyReviewCorrectionFilter = reviewCorrectionStatus != null;
        List<UUID> reviewCorrectionWorkOrderIds = List.of();
        if (applyReviewCorrectionFilter) {
            WorkOrderDirectoryReviewCorrectionQuery evidencePort = reviewCorrectionQuery.getIfAvailable();
            if (evidencePort == null) {
                throw new IllegalStateException("工单目录审核/整改筛选端口不可用");
            }
            if (scope.tenantWide() && hasTenantEvidenceRead(principal, correlationId)) {
                reviewCorrectionWorkOrderIds = evidencePort.findWorkOrderIdsByReviewCorrectionStatus(
                        principal.tenantId(), reviewCorrectionStatus, true, List.of());
            } else {
                List<UUID> evidenceReadableProjects = projectsWithEvidenceRead(
                        principal, correlationId, projectIds);
                if (evidenceReadableProjects.isEmpty()) {
                    reviewCorrectionWorkOrderIds = List.of();
                } else {
                    reviewCorrectionWorkOrderIds = evidencePort.findWorkOrderIdsByReviewCorrectionStatus(
                            principal.tenantId(), reviewCorrectionStatus, false, evidenceReadableProjects);
                }
            }
        }
        List<WorkOrderView> fetched = queries.findPage(principal.tenantId(), scope.tenantWide(), projectIds,
                clientCode, query.projectId(), status, externalOrderCode,
                provinceCode, cityCode, districtCode,
                applyStageFilter, stageWorkOrderIds,
                applyTaskStatusFilter, taskStatusWorkOrderIds,
                applyNetworkFilter, networkWorkOrderIds,
                applyTechnicianFilter, technicianWorkOrderIds,
                applySlaRiskFilter, slaRiskWorkOrderIds,
                applyReviewCorrectionFilter, reviewCorrectionWorkOrderIds,
                receivedBounds.fromInclusive(), receivedBounds.toExclusive(),
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
        // M444：同筛选精确全量 COUNT（无 cursor）；totalCountTruncated 恒 false。
        int totalCount = queries.countMatching(
                principal.tenantId(), scope.tenantWide(), projectIds,
                clientCode, query.projectId(), status, externalOrderCode,
                provinceCode, cityCode, districtCode,
                applyStageFilter, stageWorkOrderIds,
                applyTaskStatusFilter, taskStatusWorkOrderIds,
                applyNetworkFilter, networkWorkOrderIds,
                applyTechnicianFilter, technicianWorkOrderIds,
                applySlaRiskFilter, slaRiskWorkOrderIds,
                applyReviewCorrectionFilter, reviewCorrectionWorkOrderIds,
                receivedBounds.fromInclusive(), receivedBounds.toExclusive());
        return new WorkOrderPage(
                enriched,
                last == null ? null : encodeCursor(scope.scopeDigest(),
                        filterDigest, last.receivedAt(), last.id()),
                clock.instant(),
                slaRiskSummaries,
                totalCount,
                false);
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
     * M429/M432/M433/M435/M439：在已授权的 WorkOrderView 上附着脱敏联系、阶段、责任人与网点/师傅，
     * 并透传独立 updatedAt；原文不进入视图。旁载字段由调用方批量查询后传入，缺任务/认领/档案/责任时保持 null。
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
                view.updatedAt(),
                view.activatedAt(),
                view.fulfilledAt(),
                view.version(),
                contact.maskedCustomerName(),
                contact.maskedCustomerPhone(),
                contact.maskedServiceAddress(),
                enrichment.currentStageCode(),
                enrichment.currentTaskStatus(),
                enrichment.currentClaimedBy(),
                enrichment.currentAssigneeDisplayName(),
                enrichment.currentNetworkId(),
                enrichment.currentNetworkDisplayName(),
                enrichment.currentTechnicianId(),
                enrichment.currentTechnicianDisplayName());
    }

    private DirectorySideCars loadDirectorySideCars(String tenantId, List<UUID> workOrderIds) {
        if (workOrderIds.isEmpty()) {
            return DirectorySideCars.empty();
        }
        Map<UUID, String> stages = Map.of();
        Map<UUID, String> taskStatuses = Map.of();
        WorkOrderDirectoryStageQuery stagesPort = stageQuery.getIfAvailable();
        if (stagesPort != null) {
            stages = stagesPort.findCurrentStageCodes(tenantId, workOrderIds);
            taskStatuses = stagesPort.findCurrentTaskStatuses(tenantId, workOrderIds);
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
        Map<UUID, WorkOrderDirectoryServiceResponsibility> responsibilities = Map.of();
        WorkOrderDirectoryServiceResponsibilityQuery responsibilityPort =
                responsibilityQuery.getIfAvailable();
        if (responsibilityPort != null) {
            responsibilities = responsibilityPort.findActive(tenantId, workOrderIds);
        }
        return new DirectorySideCars(stages, taskStatuses, claimedBy, displayNames, responsibilities);
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
            String currentTaskStatus,
            String currentClaimedBy,
            String currentAssigneeDisplayName,
            String currentNetworkId,
            String currentNetworkDisplayName,
            String currentTechnicianId,
            String currentTechnicianDisplayName
    ) {}

    private record DirectorySideCars(
            Map<UUID, String> stages,
            Map<UUID, String> taskStatuses,
            Map<UUID, String> claimedBy,
            Map<UUID, String> displayNames,
            Map<UUID, WorkOrderDirectoryServiceResponsibility> responsibilities
    ) {
        static DirectorySideCars empty() {
            return new DirectorySideCars(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        DirectoryEnrichment forWorkOrder(UUID workOrderId) {
            String claimed = claimedBy.get(workOrderId);
            String displayName = null;
            UUID principalId = tryParseUuid(claimed);
            if (principalId != null) {
                displayName = displayNames.get(principalId);
            }
            WorkOrderDirectoryServiceResponsibility responsibility = responsibilities.get(workOrderId);
            return new DirectoryEnrichment(
                    stages.get(workOrderId),
                    taskStatuses.get(workOrderId),
                    claimed,
                    displayName,
                    responsibility == null ? null : responsibility.networkId(),
                    responsibility == null ? null : responsibility.networkDisplayName(),
                    responsibility == null ? null : responsibility.technicianId(),
                    responsibility == null ? null : responsibility.technicianDisplayName());
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

    /** M442/M445：SLA 风险筛选枚举（OPEN/BREACHED/NEAR）。 */
    private static String normalizeSlaRisk(String value) {
        if (value == null) {
            return null;
        }
        if (!"OPEN".equals(value) && !"BREACHED".equals(value) && !"NEAR".equals(value)) {
            throw new IllegalArgumentException("slaRisk is invalid");
        }
        return value;
    }

    /** M447：审核/整改运营桶枚举。 */
    private static String normalizeReviewCorrectionStatus(String value) {
        if (value == null) {
            return null;
        }
        if (!REVIEW_CORRECTION_STATUSES.contains(value)) {
            throw new IllegalArgumentException("reviewCorrectionStatus is invalid");
        }
        return value;
    }

    /**
     * M443：自然日闭区间转为 timestamptz 半开区间。
     * from 日 00:00 Asia/Shanghai ≤ received_at &lt; to 日次日 00:00。
     */
    private static ReceivedBounds normalizeReceivedBounds(LocalDate receivedFrom, LocalDate receivedTo) {
        if (receivedFrom != null && receivedTo != null) {
            if (receivedTo.isBefore(receivedFrom)) {
                throw new IllegalArgumentException("receivedTo must not be before receivedFrom");
            }
            long days = ChronoUnit.DAYS.between(receivedFrom, receivedTo) + 1;
            if (days > MAX_RECEIVED_RANGE_DAYS) {
                throw new IllegalArgumentException("received date range must not exceed 366 days");
            }
        }
        Instant fromInclusive = receivedFrom == null
                ? null
                : receivedFrom.atStartOfDay(RECEIVED_DAY_ZONE).toInstant();
        Instant toExclusive = receivedTo == null
                ? null
                : receivedTo.plusDays(1).atStartOfDay(RECEIVED_DAY_ZONE).toInstant();
        return new ReceivedBounds(fromInclusive, toExclusive);
    }

    private boolean hasTenantSlaRead(CurrentPrincipal principal, String correlationId) {
        AuthorizationDecision decision = authorization.authorize(
                principal,
                AuthorizationRequest.tenantCapability(
                        SLA_READ, principal.tenantId(), "SlaInstance", principal.tenantId()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private List<UUID> projectsWithSlaRead(
            CurrentPrincipal principal, String correlationId, List<UUID> projectIds
    ) {
        List<UUID> allowed = new ArrayList<>();
        for (UUID projectId : projectIds) {
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
                allowed.add(projectId);
            }
        }
        return List.copyOf(allowed);
    }

    private boolean hasTenantEvidenceRead(CurrentPrincipal principal, String correlationId) {
        AuthorizationDecision decision = authorization.authorize(
                principal,
                AuthorizationRequest.tenantCapability(
                        EVIDENCE_READ, principal.tenantId(), "CorrectionCase", principal.tenantId()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private List<UUID> projectsWithEvidenceRead(
            CurrentPrincipal principal, String correlationId, List<UUID> projectIds
    ) {
        List<UUID> allowed = new ArrayList<>();
        for (UUID projectId : projectIds) {
            AuthorizationDecision decision = authorization.authorize(
                    principal,
                    AuthorizationRequest.projectCapability(
                            EVIDENCE_READ,
                            principal.tenantId(),
                            "CorrectionCase",
                            projectId.toString(),
                            projectId.toString()),
                    correlationId);
            if (decision.effect() == AuthorizationDecision.Effect.ALLOW) {
                allowed.add(projectId);
            }
        }
        return List.copyOf(allowed);
    }

    private static String normalizeCode(String value, String field) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 128)
            throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    /** M437：国标行政区码；长度与 wo_work_order 列一致（≤16）。 */
    private static String normalizeRegionCode(String value, String field) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 16)
            throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    /** M438：阶段码与 tsk_task.stage_code CHECK 一致（^[A-Z][A-Z0-9_]*$）。 */
    private static String normalizeStageCode(String value) {
        if (value == null) return null;
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 64
                || !value.matches("^[A-Z][A-Z0-9_]*$")) {
            throw new IllegalArgumentException("currentStageCode is invalid");
        }
        return value;
    }

    /** M446：当前 ACTIVE 任务状态枚举。 */
    private static String normalizeTaskStatus(String value) {
        if (value == null) {
            return null;
        }
        if (!ACTIVE_TASK_STATUSES.contains(value)) {
            throw new IllegalArgumentException("currentTaskStatus is invalid");
        }
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
    private record ReceivedBounds(Instant fromInclusive, Instant toExclusive) {}
}
