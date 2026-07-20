package com.serviceos.readmodel.application;

import com.serviceos.dispatch.api.ServiceAssignmentQueryService;
import com.serviceos.dispatch.api.ServiceAssignmentSummary;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSetSnapshotMemberView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.FinalReviewWorkspaceQueryService;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewAllowedAction;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewCaseSummary;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewGateCheck;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewRejectionReason;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewSlaSummary;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewTarget;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewTargetGroup;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewTargetRef;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewTaskSummary;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewWorkOrderSummary;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewWorkspaceData;
import com.serviceos.readmodel.api.FinalReviewWorkspaceSectionResponse.FinalReviewWorkspaceMeta;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.sla.api.SlaInstanceItem;
import com.serviceos.sla.api.SlaInstancePage;
import com.serviceos.sla.api.SlaQueryService;
import com.serviceos.task.api.TaskAllowedActionQueryService;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.WorkOrderTaskQueryService;
import com.serviceos.task.api.WorkOrderTaskSummary;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderMaskedContactView;
import com.serviceos.workorder.api.WorkOrderQueryService;
import com.serviceos.workorder.api.WorkOrderView;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * M351 平台终审工作区只读 Fan-in。
 *
 * <p>授权先校验工单可读；再批量装载 Snapshot 成员、Slot、Item/Revision/Validation，
 * 禁止按 target 逐条查询。本服务不推进审核/任务状态。</p>
 */
@Service
final class DefaultFinalReviewWorkspaceQueryService implements FinalReviewWorkspaceQueryService {
    private static final List<FinalReviewRejectionReason> REJECTION_REASONS = List.of(
            new FinalReviewRejectionReason("IMAGE.BLUR", "图片模糊", false),
            new FinalReviewRejectionReason("IMAGE.WRONG_SN", "铭牌/SN 与工单不一致", true),
            new FinalReviewRejectionReason("IMAGE.INCOMPLETE", "资料不完整", true),
            new FinalReviewRejectionReason("IMAGE.WRONG_SCENE", "拍摄场景不符合要求", true),
            new FinalReviewRejectionReason("DOC.ILLEGIBLE", "文字无法辨认", true)
    );

    private static final Set<String> ACTIONABLE_TASK = Set.of(
            "READY", "CLAIMED", "IN_PROGRESS", "BLOCKED");
    private static final Set<String> OPEN_CORRECTION = Set.of(
            "OPEN", "IN_PROGRESS", "RESUBMITTED");

    private final WorkOrderQueryService workOrders;
    private final WorkOrderTaskQueryService workOrderTasks;
    private final ReviewCaseService reviews;
    private final CorrectionCaseService corrections;
    private final EvidenceSetSnapshotService snapshots;
    private final EvidenceSlotQueryService evidenceSlots;
    private final EvidenceCommandService evidenceItems;
    private final SlaQueryService slaQueries;
    private final ServiceAssignmentQueryService serviceAssignments;
    private final TaskFulfillmentContextService taskContexts;
    private final TaskAllowedActionQueryService taskAllowedActions;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultFinalReviewWorkspaceQueryService(
            WorkOrderQueryService workOrders,
            WorkOrderTaskQueryService workOrderTasks,
            ReviewCaseService reviews,
            CorrectionCaseService corrections,
            EvidenceSetSnapshotService snapshots,
            EvidenceSlotQueryService evidenceSlots,
            EvidenceCommandService evidenceItems,
            SlaQueryService slaQueries,
            ServiceAssignmentQueryService serviceAssignments,
            TaskFulfillmentContextService taskContexts,
            TaskAllowedActionQueryService taskAllowedActions,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.workOrderTasks = workOrderTasks;
        this.reviews = reviews;
        this.corrections = corrections;
        this.snapshots = snapshots;
        this.evidenceSlots = evidenceSlots;
        this.evidenceItems = evidenceItems;
        this.slaQueries = slaQueries;
        this.serviceAssignments = serviceAssignments;
        this.taskContexts = taskContexts;
        this.taskAllowedActions = taskAllowedActions;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public FinalReviewWorkspaceSectionResponse get(
            CurrentPrincipal principal, String correlationId, UUID workOrderId
    ) {
        // 与 DefaultWorkOrderWorkspaceQueryService 一致：本方法不加外层事务，
        // 以便次级 Fan-in 的 ACCESS_DENIED 可被局部捕获而不污染整体只读查询。
        Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        WorkOrderDetail detail = workOrders.get(principal, correlationId, workOrderId);
        WorkOrderView header = detail.workOrder();
        WorkOrderMaskedContactView contact = workOrders.getMaskedContact(
                principal, correlationId, workOrderId);

        List<WorkOrderTaskSummary> tasks = workOrderTasks.list(
                principal, correlationId, workOrderId, null, 100).items();

        ReviewCaseView reviewCase = selectReviewCase(principal, correlationId, tasks);
        FinalReviewTaskSummary reviewTask = null;
        FinalReviewSlaSummary sla = null;
        List<FinalReviewTargetGroup> targetGroups = List.of();
        FinalReviewCaseSummary caseSummary = null;
        FinalReviewTargetRef defaultTarget = null;
        boolean authorizationValid = true;
        boolean evidenceReadable = true;

        if (reviewCase != null) {
            // M364：动作面优先独立审核 Task；证据/Snapshot 扇入仍用源提交 taskId。
            UUID actionTaskId = reviewCase.reviewTaskId() != null
                    ? reviewCase.reviewTaskId()
                    : reviewCase.taskId();
            WorkOrderTaskSummary taskRow = tasks.stream()
                    .filter(task -> task.id().equals(actionTaskId))
                    .findFirst()
                    .orElse(null);
            TaskFulfillmentContext taskContext = taskContexts
                    .find(principal.tenantId(), actionTaskId)
                    .orElse(null);
            String assigneeDisplay = resolveAssigneeDisplay(principal, correlationId, taskRow, taskContext);
            boolean guarded = taskContext != null && taskContext.executionGuarded();
            long version = taskContext != null
                    ? taskContext.version()
                    : (taskRow == null ? 1L : taskRow.version());
            String taskStatus = taskContext != null
                    ? taskContext.status()
                    : (taskRow == null ? "UNKNOWN" : taskRow.status());
            reviewTask = new FinalReviewTaskSummary(
                    actionTaskId,
                    taskStatus,
                    taskStatusLabel(taskStatus),
                    assigneeDisplay,
                    version,
                    guarded);

            sla = loadSla(principal, correlationId, workOrderId, actionTaskId);
            if (sla == null && !actionTaskId.equals(reviewCase.taskId())) {
                sla = loadSla(principal, correlationId, workOrderId, reviewCase.taskId());
            }

            try {
                EvidenceSetSnapshotView snapshot = snapshots.get(
                        principal, correlationId, reviewCase.evidenceSetSnapshotId());
                List<EvidenceSlotView> slots = evidenceSlots.listForTask(
                        principal, correlationId, reviewCase.taskId());
                List<EvidenceItemView> items = evidenceItems.listForTask(
                        principal, correlationId, reviewCase.taskId());
                targetGroups = buildTargetGroups(snapshot, slots, items);
                defaultTarget = targetGroups.stream()
                        .flatMap(group -> group.targets().stream())
                        .findFirst()
                        .map(target -> new FinalReviewTargetRef(target.targetType(), target.targetId()))
                        .orElse(null);
                caseSummary = new FinalReviewCaseSummary(
                        reviewCase.reviewCaseId(),
                        reviewCase.origin(),
                        reviewCase.status(),
                        reviewCase.aggregateVersion(),
                        reviewCase.evidenceSetSnapshotId(),
                        reviewCase.snapshotContentDigest(),
                        reviewCase.policyVersion(),
                        (int) targetGroups.stream().mapToLong(g -> g.targets().size()).sum());
            } catch (BusinessProblem problem) {
                if (problem.code() == ProblemCode.ACCESS_DENIED) {
                    evidenceReadable = false;
                    authorizationValid = false;
                } else {
                    throw problem;
                }
            }
        }

        // 项目/网点/师傅显示名：避免在只读事务内调用可能 ACCESS_DENIED 的二级查询
        // （嵌套事务失败会把外层标为 rollback-only）。V1 仅返回可安全获得的标识，名称可空。
        String projectName = null;
        String networkName = null;
        String technicianName = null;
        if (reviewCase != null) {
            Optional<ServiceAssignmentSummary> assignment = safeAssignment(
                    principal, correlationId, reviewCase.taskId());
            if (assignment.isPresent()) {
                networkName = assignment.get().networkId();
                technicianName = assignment.get().technicianId();
            }
        }

        List<CorrectionCaseView> openCorrections = listOpenCorrections(principal, correlationId, tasks);
        UUID openCorrectionCaseId = null;
        if (reviewCase != null) {
            openCorrectionCaseId = openCorrections.stream()
                    .filter(c -> reviewCase.reviewCaseId().equals(c.sourceReviewCaseId()))
                    .map(CorrectionCaseView::correctionCaseId)
                    .findFirst()
                    .orElse(null);
        }
        List<FinalReviewGateCheck> gates = buildGates(
                reviewCase, reviewTask, targetGroups, openCorrections, authorizationValid, evidenceReadable);
        List<FinalReviewAllowedAction> actions = buildAllowedActions(
                principal, correlationId, reviewCase, reviewTask, gates, openCorrectionCaseId);

        FinalReviewWorkOrderSummary workOrderSummary = new FinalReviewWorkOrderSummary(
                header.id(),
                header.externalOrderCode(),
                header.projectId(),
                projectName,
                header.status(),
                workOrderStatusLabel(header.status()),
                header.serviceProductCode(),
                header.serviceProductCode(),
                contact.maskedCustomerName(),
                contact.maskedCustomerPhone(),
                contact.maskedServiceAddress(),
                networkName,
                technicianName,
                null,
                nextActionLabel(reviewCase, actions));

        Instant asOf = clock.instant();
        FinalReviewWorkspaceData data = new FinalReviewWorkspaceData(
                workOrderSummary,
                reviewTask,
                caseSummary,
                sla,
                gates,
                targetGroups,
                REJECTION_REASONS,
                actions,
                defaultTarget,
                openCorrectionCaseId);
        FinalReviewWorkspaceMeta meta = new FinalReviewWorkspaceMeta(
                asOf,
                "final-review.v1:live",
                "FRESH",
                header.version(),
                "frq-" + UUID.randomUUID());
        return new FinalReviewWorkspaceSectionResponse(data, meta);
    }

    private ReviewCaseView selectReviewCase(
            CurrentPrincipal principal, String correlationId, List<WorkOrderTaskSummary> tasks
    ) {
        List<ReviewCaseView> collected = new ArrayList<>();
        for (WorkOrderTaskSummary task : tasks) {
            try {
                collected.addAll(reviews.listForTask(principal, correlationId, task.id()));
            } catch (BusinessProblem problem) {
                if (problem.code() == ProblemCode.ACCESS_DENIED) {
                    continue;
                }
                throw problem;
            }
        }
        Optional<ReviewCaseView> openInternal = collected.stream()
                .filter(c -> "INTERNAL".equals(c.origin()) && "OPEN".equals(c.status()))
                .max(Comparator.comparing(ReviewCaseView::createdAt)
                        .thenComparing(ReviewCaseView::reviewCaseId));
        if (openInternal.isPresent()) {
            return openInternal.get();
        }
        return collected.stream()
                .max(Comparator.comparing(ReviewCaseView::createdAt)
                        .thenComparing(ReviewCaseView::reviewCaseId))
                .orElse(null);
    }

    private List<FinalReviewTargetGroup> buildTargetGroups(
            EvidenceSetSnapshotView snapshot,
            List<EvidenceSlotView> slots,
            List<EvidenceItemView> items
    ) {
        Map<UUID, EvidenceSlotView> slotById = slots.stream()
                .collect(Collectors.toMap(EvidenceSlotView::slotId, Function.identity(), (a, b) -> a));
        Map<UUID, EvidenceRevisionView> revisionById = new HashMap<>();
        for (EvidenceItemView item : items) {
            for (EvidenceRevisionView revision : item.revisions()) {
                revisionById.put(revision.evidenceRevisionId(), revision);
            }
        }
        Map<String, List<FinalReviewTarget>> grouped = new LinkedHashMap<>();
        int order = 0;
        List<EvidenceSetSnapshotMemberView> members = snapshot.members().stream()
                .sorted(Comparator.comparingInt(EvidenceSetSnapshotMemberView::memberOrdinal))
                .toList();
        for (EvidenceSetSnapshotMemberView member : members) {
            EvidenceSlotView slot = slotById.get(member.evidenceSlotId());
            EvidenceRevisionView revision = revisionById.get(member.evidenceRevisionId());
            if (slot == null || revision == null) {
                continue;
            }
            String groupCode = groupCode(slot);
            String groupLabel = groupLabel(slot);
            CaptureProjection capture = projectCapture(revision.captureMetadataJson());
            ValidationProjection validation = projectValidation(revision);
            FinalReviewTarget target = new FinalReviewTarget(
                    "EvidenceRevision",
                    revision.evidenceRevisionId(),
                    revision.revisionNumber(),
                    slot.requirementCode(),
                    slot.requirementName() == null ? slot.requirementCode() : slot.requirementName(),
                    null,
                    groupCode,
                    groupLabel,
                    order++,
                    slot.required(),
                    slot.slotId(),
                    revision.evidenceItemId(),
                    revision.evidenceRevisionId(),
                    revision.revisionNumber(),
                    revision.mimeType(),
                    revision.status(),
                    capture.capturedAt(),
                    capture.captureSource(),
                    revision.createdBy(),
                    capture.offline(),
                    capture.locationVerdict(),
                    validation.readiness(),
                    validation.result(),
                    validation.codes(),
                    validation.messages(),
                    Map.of());
            grouped.computeIfAbsent(groupCode, key -> new ArrayList<>()).add(target);
        }
        int groupOrder = 0;
        List<FinalReviewTargetGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<FinalReviewTarget>> entry : grouped.entrySet()) {
            String label = entry.getValue().isEmpty()
                    ? entry.getKey()
                    : entry.getValue().getFirst().groupLabel();
            groups.add(new FinalReviewTargetGroup(entry.getKey(), label, groupOrder++, entry.getValue()));
        }
        return groups;
    }

    private List<FinalReviewGateCheck> buildGates(
            ReviewCaseView reviewCase,
            FinalReviewTaskSummary reviewTask,
            List<FinalReviewTargetGroup> targetGroups,
            List<CorrectionCaseView> openCorrections,
            boolean authorizationValid,
            boolean evidenceReadable
    ) {
        List<FinalReviewTarget> targets = targetGroups.stream()
                .flatMap(g -> g.targets().stream())
                .toList();
        boolean hasOpenInternal = reviewCase != null
                && "INTERNAL".equals(reviewCase.origin())
                && "OPEN".equals(reviewCase.status());
        boolean taskActionable = reviewTask != null && ACTIONABLE_TASK.contains(reviewTask.status());
        boolean snapshotComplete = !targets.isEmpty();
        boolean requiredReady = targets.stream()
                .filter(FinalReviewTarget::required)
                .allMatch(t -> "VALIDATED".equals(t.lifecycleStatus())
                        || "STORED".equals(t.lifecycleStatus())
                        || "VALIDATING".equals(t.lifecycleStatus()));
        boolean noQuarantine = targets.stream()
                .noneMatch(t -> "QUARANTINED".equals(t.lifecycleStatus()));
        boolean noOpenCorrection = openCorrections.isEmpty();

        List<FinalReviewGateCheck> gates = new ArrayList<>();
        gates.add(gate("REVIEW_CASE_OPEN", "审核案例待审",
                hasOpenInternal ? "PASS" : "FAIL", true,
                hasOpenInternal ? null : "当前无 OPEN 的平台审核案例"));
        gates.add(gate("TASK_ACTIONABLE", "审核任务可执行",
                taskActionable ? "PASS" : "FAIL", true,
                taskActionable ? null : "关联审核任务不可执行"));
        gates.add(gate("SNAPSHOT_COMPLETE", "提交快照完整",
                snapshotComplete ? "PASS" : "FAIL", true,
                snapshotComplete ? null : "冻结 Snapshot 无审核目标"));
        gates.add(gate("REQUIRED_EVIDENCE_READY", "必需资料就绪",
                !evidenceReadable ? "FAIL" : (requiredReady ? "PASS" : "FAIL"), true,
                requiredReady ? null : "存在未就绪的必需资料"));
        gates.add(gate("NO_QUARANTINED_EVIDENCE", "无安全隔离资料",
                noQuarantine ? "PASS" : "FAIL", true,
                noQuarantine ? null : "存在被安全隔离的资料版本"));
        gates.add(gate("ALL_TARGETS_DECIDED", "全部目标已决定",
                "PENDING", false, "正式提交前由客户端与服务端共同校验"));
        gates.add(gate("REJECTED_TARGET_COMPLETE", "驳回目标信息完整",
                "PENDING", false, "正式提交前由客户端与服务端共同校验"));
        gates.add(gate("NO_OPEN_CORRECTION", "无未关闭整改",
                noOpenCorrection ? "PASS" : "FAIL", true,
                noOpenCorrection ? null : "存在未关闭的整改案例"));
        gates.add(gate("AUTHORIZATION_VALID", "授权有效",
                authorizationValid ? "PASS" : "FAIL", true,
                authorizationValid ? null : "当前主体缺少终审所需能力"));
        return gates;
    }

    private List<FinalReviewAllowedAction> buildAllowedActions(
            CurrentPrincipal principal,
            String correlationId,
            ReviewCaseView reviewCase,
            FinalReviewTaskSummary reviewTask,
            List<FinalReviewGateCheck> gates,
            UUID openCorrectionCaseId
    ) {
        boolean blockingFail = gates.stream().anyMatch(g -> g.blocking() && "FAIL".equals(g.status()));
        boolean canDecide = reviewCase != null
                && "INTERNAL".equals(reviewCase.origin())
                && "OPEN".equals(reviewCase.status())
                && !blockingFail;
        boolean preview = reviewCase != null;
        boolean viewOnly = reviewCase != null && "CLIENT".equals(reviewCase.origin());
        if (canDecide && reviewTask != null) {
            try {
                taskAllowedActions.get(principal, correlationId, reviewTask.taskId());
            } catch (BusinessProblem ignored) {
                // allowed-actions 失败不阻断只读投影；正式 decide 仍由命令侧重新授权。
            }
        }
        List<FinalReviewAllowedAction> actions = new ArrayList<>();
        if (viewOnly) {
            actions.add(new FinalReviewAllowedAction("VIEW_ONLY", true, "车企审核案例只读"));
            actions.add(new FinalReviewAllowedAction("DECIDE", false, "CLIENT ReviewCase 不可普通决定"));
        } else {
            actions.add(new FinalReviewAllowedAction(
                    "DECIDE", canDecide, canDecide ? null : "当前状态不允许提交终审"));
            actions.add(new FinalReviewAllowedAction(
                    "VIEW_ONLY", !canDecide && reviewCase != null, null));
        }
        actions.add(new FinalReviewAllowedAction(
                "PREVIEW_EVIDENCE", preview, preview ? null : "无可预览资料"));
        actions.add(new FinalReviewAllowedAction(
                "OPEN_CORRECTION",
                openCorrectionCaseId != null,
                openCorrectionCaseId != null ? "打开关联整改案例" : "驳回后由服务端自动创建"));
        return actions;
    }

    private FinalReviewSlaSummary loadSla(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, UUID taskId
    ) {
        try {
            SlaInstancePage page = slaQueries.listForWorkOrder(
                    principal, correlationId, workOrderId, null, 50);
            SlaInstanceItem match = page.items().stream()
                    .filter(item -> taskId.equals(item.taskId()))
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                return null;
            }
            return new FinalReviewSlaSummary(
                    match.status(),
                    match.startedAt(),
                    match.deadlineAt(),
                    slaDisplay(match));
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return null;
            }
            throw problem;
        }
    }

    private List<CorrectionCaseView> listOpenCorrections(
            CurrentPrincipal principal, String correlationId, List<WorkOrderTaskSummary> tasks
    ) {
        List<CorrectionCaseView> open = new ArrayList<>();
        for (WorkOrderTaskSummary task : tasks) {
            try {
                for (CorrectionCaseView correction : corrections.listForTask(
                        principal, correlationId, task.id())) {
                    if (OPEN_CORRECTION.contains(correction.status())) {
                        open.add(correction);
                    }
                }
            } catch (BusinessProblem problem) {
                if (problem.code() == ProblemCode.ACCESS_DENIED) {
                    continue;
                }
                throw problem;
            }
        }
        return open;
    }

    private Optional<ServiceAssignmentSummary> safeAssignment(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        try {
            return serviceAssignments.findActiveForTask(principal, correlationId, taskId);
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return Optional.empty();
            }
            throw problem;
        }
    }

    private String resolveAssigneeDisplay(
            CurrentPrincipal principal,
            String correlationId,
            WorkOrderTaskSummary taskRow,
            TaskFulfillmentContext context
    ) {
        if (taskRow != null && taskRow.claimedBy() != null && !taskRow.claimedBy().isBlank()) {
            return taskRow.claimedBy();
        }
        if (context != null && context.responsiblePrincipalId() != null) {
            return context.responsiblePrincipalId();
        }
        return null;
    }

    private static FinalReviewGateCheck gate(
            String code, String label, String status, boolean blocking, String detail
    ) {
        return new FinalReviewGateCheck(code, label, status, blocking, detail);
    }

    private static String groupCode(EvidenceSlotView slot) {
        String code = slot.requirementCode();
        int dot = code.indexOf('.');
        return dot > 0 ? code.substring(0, dot) : "GENERAL";
    }

    private static String groupLabel(EvidenceSlotView slot) {
        return switch (groupCode(slot).toUpperCase(Locale.ROOT)) {
            case "INSTALLATION" -> "安装资料";
            case "CHARGER" -> "设备资料";
            case "SURVEY" -> "勘测资料";
            case "DOC" -> "单据资料";
            default -> "资料目标";
        };
    }

    private CaptureProjection projectCapture(String captureMetadataJson) {
        try {
            JsonNode root = objectMapper.readTree(
                    captureMetadataJson == null || captureMetadataJson.isBlank()
                            ? "{}" : captureMetadataJson);
            Instant capturedAt = null;
            if (root.path("capturedAt").isTextual()) {
                capturedAt = Instant.parse(root.path("capturedAt").asText());
            }
            String source = textOrNull(root, "source");
            if (source == null) {
                source = textOrNull(root, "captureSource");
            }
            Boolean offline = null;
            if (root.path("offlineFlag").isBoolean()) {
                offline = root.path("offlineFlag").asBoolean();
            } else if (root.path("offline").isBoolean()) {
                offline = root.path("offline").asBoolean();
            }
            String locationVerdict = "未提供定位";
            if (root.path("location").isObject() || root.has("latitude") || root.has("longitude")) {
                // 不回传原始坐标，仅给出脱敏判定结论。
                locationVerdict = "已采集（坐标已脱敏）";
            }
            return new CaptureProjection(capturedAt, source, offline, locationVerdict);
        } catch (Exception exception) {
            return new CaptureProjection(null, null, null, "采集元数据不可用");
        }
    }

    private ValidationProjection projectValidation(EvidenceRevisionView revision) {
        List<EvidenceValidationView> validations = revision.validations();
        if (validations == null || validations.isEmpty()) {
            String readiness = switch (revision.status()) {
                case "VALIDATING", "STORED" -> "PROCESSING";
                case "QUARANTINED" -> "QUARANTINED";
                case "VALIDATED" -> "READY";
                default -> "UNKNOWN";
            };
            return new ValidationProjection(readiness, null, List.of(), List.of());
        }
        List<String> codes = validations.stream()
                .map(EvidenceValidationView::reasonCode)
                .filter(Objects::nonNull)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        List<String> messages = validations.stream()
                .map(EvidenceValidationView::message)
                .filter(Objects::nonNull)
                .filter(message -> !message.isBlank())
                .distinct()
                .limit(10)
                .toList();
        boolean hasFail = validations.stream()
                .anyMatch(v -> "FAIL".equalsIgnoreCase(v.result()) || "FAILED".equalsIgnoreCase(v.result()));
        boolean hasWarn = validations.stream()
                .anyMatch(v -> "WARN".equalsIgnoreCase(v.result()) || "WARNING".equalsIgnoreCase(v.result()));
        String result = hasFail ? "FAIL" : (hasWarn ? "WARN" : "PASS");
        return new ValidationProjection("READY", result, codes, messages);
    }

    private static String textOrNull(JsonNode root, String field) {
        JsonNode node = root.path(field);
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private static String slaDisplay(SlaInstanceItem item) {
        if ("BREACHED".equals(item.status())) {
            return "已超时";
        }
        if (item.deadlineAt() == null) {
            return statusLabel(item.status());
        }
        Duration remaining = Duration.between(Instant.now(), item.deadlineAt());
        if (remaining.isNegative()) {
            return "已超时";
        }
        long hours = remaining.toHours();
        if (hours >= 24) {
            return "剩余约 " + (hours / 24) + " 天";
        }
        if (hours >= 1) {
            return "剩余约 " + hours + " 小时";
        }
        return "剩余约 " + Math.max(1, remaining.toMinutes()) + " 分钟";
    }

    private static String nextActionLabel(ReviewCaseView reviewCase, List<FinalReviewAllowedAction> actions) {
        boolean decide = actions.stream().anyMatch(a -> "DECIDE".equals(a.action()) && a.enabled());
        if (decide) {
            return "提交平台终审";
        }
        if (reviewCase == null) {
            return "暂无待终审案例";
        }
        if ("CLIENT".equals(reviewCase.origin())) {
            return "查看车企审核";
        }
        return "查看审核结果";
    }

    private static String workOrderStatusLabel(String status) {
        return switch (status) {
            case "RECEIVED" -> "已接收";
            case "ACTIVE" -> "履约中";
            case "SUSPENDED" -> "已暂停";
            case "FULFILLED" -> "已履约";
            case "CANCELLED" -> "已取消";
            case "CLOSED" -> "已关闭";
            default -> statusLabel(status);
        };
    }

    private static String taskStatusLabel(String status) {
        return switch (status) {
            case "READY" -> "待领取";
            case "CLAIMED" -> "已领取";
            case "IN_PROGRESS" -> "进行中";
            case "BLOCKED" -> "已阻塞";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> statusLabel(status);
        };
    }

    private static String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "未知";
        }
        return status;
    }

    private record CaptureProjection(
            Instant capturedAt, String captureSource, Boolean offline, String locationVerdict
    ) {
    }

    private record ValidationProjection(
            String readiness, String result, List<String> codes, List<String> messages
    ) {
    }
}
