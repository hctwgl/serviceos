package com.serviceos.readmodel.application;

import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentView;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.ContactAttemptView;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.dispatch.api.NetworkActiveAssignmentQuery;
import com.serviceos.dispatch.api.NetworkActiveAssignmentView;
import com.serviceos.dispatch.api.NetworkCapacityCounterView;
import com.serviceos.dispatch.api.NetworkCapacitySummaryQuery;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.api.EvidenceItemQueryService;
import com.serviceos.evidence.api.EvidenceItemSummaryView;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkPortalMembershipQuery;
import com.serviceos.network.api.NetworkPortalQualificationQuery;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.readmodel.api.NetworkPortalCapacityItem;
import com.serviceos.readmodel.api.NetworkPortalCorrectionItem;
import com.serviceos.readmodel.api.NetworkPortalExceptionItem;
import com.serviceos.readmodel.api.NetworkPortalMembershipItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQualificationItem;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.readmodel.api.NetworkPortalTechnicianItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderWorkspace;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderWorkspaceSlaSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceAppointmentSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceContactAttemptSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceCorrectionCaseSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceCorrectionResubmissionSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceEvidenceItemSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceEvidenceSlotSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceFormSubmissionSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceReviewCaseSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceReviewDecisionSummary;
import com.serviceos.readmodel.api.NetworkPortalWorkspaceVisitSummary;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.sla.api.SlaInstanceItem;
import com.serviceos.sla.api.SlaQueryService;
import com.serviceos.fieldwork.api.VisitService;
import com.serviceos.fieldwork.api.VisitView;
import com.serviceos.forms.api.FormSubmissionQueryService;
import com.serviceos.forms.api.FormSubmissionSummaryView;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Network Portal 只读编排。
 *
 * <p>事务边界：只读；不写 Outbox/领域事实。鉴权顺序：解析可信上下文 → ACTIVE
 * NetworkMembership → NETWORK scope capability → 按 networkId 收敛数据。跨网点失败关闭。</p>
 */
@Service
final class DefaultNetworkPortalQueryService implements NetworkPortalQueryService {
    private static final String NETWORK_TASK_READ = "networkTask.read";
    private static final String TECHNICIAN_READ_OWN = "technician.readOwnNetwork";
    private static final String EVIDENCE_READ = "evidence.read";
    private static final String EXCEPTION_READ = "operations.exception.read";
    private static final String SLA_READ = "sla.read";
    private static final String VISIT_READ = "visit.read";
    private static final String FORM_READ = "form.read";
    private static final String MANAGE_APPOINTMENT = "networkPortal.manageAppointment";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";
    private static final int DEFAULT_CORRECTION_LIMIT = 50;
    private static final int MAX_CORRECTION_LIMIT = 100;
    private static final int DEFAULT_EXCEPTION_LIMIT = 50;
    private static final int MAX_EXCEPTION_LIMIT = 100;
    private static final int DEFAULT_QUALIFICATION_LIMIT = 50;
    private static final int MAX_QUALIFICATION_LIMIT = 100;
    private static final int DEFAULT_MEMBERSHIP_LIMIT = 50;
    private static final int MAX_MEMBERSHIP_LIMIT = 100;
    private static final int SLA_WORKSPACE_LIMIT = 100;
    private static final int WORKSPACE_VISIT_LIMIT = 100;
    private static final int WORKSPACE_FORM_LIMIT = 100;
    private static final int WORKSPACE_EVIDENCE_LIMIT = 100;
    private static final int WORKSPACE_CORRECTION_LIMIT = 100;
    private static final int WORKSPACE_REVIEW_LIMIT = 100;
    private static final int WORKSPACE_EXCEPTION_LIMIT = 100;
    private static final int WORKSPACE_APPOINTMENT_LIMIT = 100;
    private static final int WORKSPACE_CONTACT_LIMIT = 100;
    private static final Set<String> OPEN_SLA_STATUSES = Set.of("RUNNING", "BREACHED");

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final NetworkActiveAssignmentQuery assignments;
    private final NetworkCapacitySummaryQuery capacity;
    private final NetworkPortalTechnicianQuery technicians;
    private final NetworkPortalQualificationQuery qualifications;
    private final NetworkPortalMembershipQuery memberships;
    private final TaskFulfillmentContextService tasks;
    private final CorrectionCaseService corrections;
    private final ReviewCaseService reviews;
    private final OperationalExceptionWorkbenchService exceptions;
    private final ActiveServiceResponsibilityService responsibilities;
    private final SlaQueryService slaQueries;
    private final VisitService visits;
    private final FormSubmissionQueryService formSubmissions;
    private final EvidenceSlotQueryService evidenceSlots;
    private final EvidenceItemQueryService evidenceItems;
    private final AppointmentService appointments;
    private final Clock clock;

    DefaultNetworkPortalQueryService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            NetworkActiveAssignmentQuery assignments,
            NetworkCapacitySummaryQuery capacity,
            NetworkPortalTechnicianQuery technicians,
            NetworkPortalQualificationQuery qualifications,
            NetworkPortalMembershipQuery memberships,
            TaskFulfillmentContextService tasks,
            CorrectionCaseService corrections,
            ReviewCaseService reviews,
            OperationalExceptionWorkbenchService exceptions,
            ActiveServiceResponsibilityService responsibilities,
            SlaQueryService slaQueries,
            VisitService visits,
            FormSubmissionQueryService formSubmissions,
            EvidenceSlotQueryService evidenceSlots,
            EvidenceItemQueryService evidenceItems,
            AppointmentService appointments,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.assignments = assignments;
        this.capacity = capacity;
        this.technicians = technicians;
        this.qualifications = qualifications;
        this.memberships = memberships;
        this.tasks = tasks;
        this.corrections = corrections;
        this.reviews = reviews;
        this.exceptions = exceptions;
        this.responsibilities = responsibilities;
        this.slaQueries = slaQueries;
        this.visits = visits;
        this.formSubmissions = formSubmissions;
        this.evidenceSlots = evidenceSlots;
        this.evidenceItems = evidenceItems;
        this.appointments = appointments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalWorkOrderItem> listWorkOrders(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        Map<UUID, NetworkPortalWorkOrderItem> byWorkOrder = new LinkedHashMap<>();
        for (NetworkActiveAssignmentView row : active) {
            TaskFulfillmentContext task = tasks.find(actor.tenantId(), row.taskId()).orElse(null);
            UUID projectId = task == null ? null : task.projectId();
            NetworkPortalWorkOrderItem existing = byWorkOrder.get(row.workOrderId());
            if (existing == null) {
                byWorkOrder.put(row.workOrderId(), new NetworkPortalWorkOrderItem(
                        row.workOrderId(),
                        projectId,
                        List.of(row.taskId()),
                        row.businessType(),
                        row.technicianId(),
                        row.effectiveFrom()));
            } else {
                List<UUID> taskIds = new ArrayList<>(existing.taskIds());
                if (!taskIds.contains(row.taskId())) {
                    taskIds.add(row.taskId());
                }
                byWorkOrder.put(row.workOrderId(), new NetworkPortalWorkOrderItem(
                        existing.workOrderId(),
                        existing.projectId() != null ? existing.projectId() : projectId,
                        taskIds,
                        existing.businessType(),
                        existing.technicianId() != null ? existing.technicianId() : row.technicianId(),
                        existing.effectiveFrom() != null ? existing.effectiveFrom() : row.effectiveFrom()));
            }
        }
        List<NetworkPortalWorkOrderItem> workOrderItems = List.copyOf(byWorkOrder.values());
        List<NetworkPortalTechnicianItem> technicianSummaries = null;
        if (hasNetworkCapability(actor, correlationId, TECHNICIAN_READ_OWN, networkId)) {
            Set<String> wantedTechnicianIds = new LinkedHashSet<>();
            for (NetworkPortalWorkOrderItem item : workOrderItems) {
                if (item.technicianId() != null && !item.technicianId().isBlank()) {
                    wantedTechnicianIds.add(item.technicianId());
                }
            }
            technicianSummaries = loadTechnicianSummaries(
                    actor.tenantId(), networkId, wantedTechnicianIds);
        }
        return new NetworkPortalPage<>(
                networkId, workOrderItems, clock.instant(), technicianSummaries);
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalWorkOrderWorkspace getWorkOrderWorkspace(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID workOrderId
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        Objects.requireNonNull(workOrderId, "workOrderId");
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        List<NetworkActiveAssignmentView> forWorkOrder = active.stream()
                .filter(row -> workOrderId.equals(row.workOrderId()))
                .toList();
        if (forWorkOrder.isEmpty()) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "工单不在本网点 ACTIVE 责任范围");
        }
        List<UUID> taskIds = new ArrayList<>();
        List<NetworkPortalTaskItem> taskItems = new ArrayList<>();
        UUID projectId = null;
        String businessType = null;
        String technicianId = null;
        Instant effectiveFrom = null;
        for (NetworkActiveAssignmentView row : forWorkOrder) {
            if (!taskIds.contains(row.taskId())) {
                taskIds.add(row.taskId());
            }
            if (businessType == null) {
                businessType = row.businessType();
            }
            if (technicianId == null) {
                technicianId = row.technicianId();
            }
            if (effectiveFrom == null) {
                effectiveFrom = row.effectiveFrom();
            }
            TaskFulfillmentContext task = tasks.find(actor.tenantId(), row.taskId()).orElse(null);
            if (projectId == null && task != null) {
                projectId = task.projectId();
            }
            taskItems.add(new NetworkPortalTaskItem(
                    row.taskId(),
                    row.workOrderId(),
                    task == null ? null : task.projectId(),
                    task == null ? null : task.taskType(),
                    task == null ? null : task.taskKind(),
                    task == null ? null : task.stageCode(),
                    task == null ? null : task.status(),
                    row.businessType(),
                    row.technicianId(),
                    row.effectiveFrom()));
        }
        Set<UUID> activeTaskIds = Set.copyOf(taskIds);
        NetworkPortalWorkOrderWorkspaceSlaSummary slaSummary = null;
        if (projectId != null && hasNetworkCapability(actor, correlationId, SLA_READ, networkId)) {
            slaSummary = loadSlaSummary(
                    actor, correlationId, workOrderId, projectId, networkId, activeTaskIds);
        }
        List<NetworkPortalWorkspaceVisitSummary> visitSummaries = null;
        if (hasNetworkCapability(actor, correlationId, VISIT_READ, networkId)) {
            visitSummaries = loadVisitSummaries(
                    actor, correlationId, workOrderId, networkId, activeTaskIds);
        }
        List<NetworkPortalWorkspaceFormSubmissionSummary> formSubmissionSummaries = null;
        if (hasNetworkCapability(actor, correlationId, FORM_READ, networkId)) {
            formSubmissionSummaries = loadFormSubmissionSummaries(
                    actor, correlationId, networkId, activeTaskIds);
        }
        List<NetworkPortalWorkspaceEvidenceSlotSummary> evidenceSlotSummaries = null;
        List<NetworkPortalWorkspaceEvidenceItemSummary> evidenceItemSummaries = null;
        List<NetworkPortalWorkspaceCorrectionCaseSummary> correctionSummaries = null;
        List<NetworkPortalWorkspaceReviewCaseSummary> reviewSummaries = null;
        if (hasNetworkCapability(actor, correlationId, EVIDENCE_READ, networkId)) {
            evidenceSlotSummaries = loadEvidenceSlotSummaries(
                    actor, correlationId, networkId, activeTaskIds);
            evidenceItemSummaries = loadEvidenceItemSummaries(
                    actor, correlationId, networkId, activeTaskIds);
            correctionSummaries = loadCorrectionSummaries(actor, correlationId, activeTaskIds);
            reviewSummaries = loadReviewSummaries(actor, correlationId, activeTaskIds);
        }
        List<NetworkPortalExceptionItem> exceptionSummaries = null;
        if (hasNetworkCapability(actor, correlationId, EXCEPTION_READ, networkId)) {
            exceptionSummaries = loadExceptionSummaries(actor, correlationId, activeTaskIds);
        }
        List<NetworkPortalWorkspaceAppointmentSummary> appointmentSummaries = null;
        List<NetworkPortalWorkspaceContactAttemptSummary> contactAttemptSummaries = null;
        if (hasNetworkCapability(actor, correlationId, MANAGE_APPOINTMENT, networkId)) {
            appointmentSummaries = loadAppointmentSummaries(
                    actor, correlationId, networkId, activeTaskIds);
            contactAttemptSummaries = loadContactAttemptSummaries(
                    actor, correlationId, activeTaskIds);
        }
        List<NetworkPortalTechnicianItem> technicianSummaries = null;
        if (hasNetworkCapability(actor, correlationId, TECHNICIAN_READ_OWN, networkId)) {
            Set<String> wantedTechnicianIds = new LinkedHashSet<>();
            if (technicianId != null && !technicianId.isBlank()) {
                wantedTechnicianIds.add(technicianId);
            }
            for (NetworkPortalTaskItem task : taskItems) {
                if (task.technicianId() != null && !task.technicianId().isBlank()) {
                    wantedTechnicianIds.add(task.technicianId());
                }
            }
            technicianSummaries = loadTechnicianSummaries(
                    actor.tenantId(), networkId, wantedTechnicianIds);
        }
        return new NetworkPortalWorkOrderWorkspace(
                networkId,
                workOrderId,
                projectId,
                taskIds,
                businessType,
                technicianId,
                effectiveFrom,
                taskItems,
                slaSummary,
                visitSummaries,
                formSubmissionSummaries,
                evidenceSlotSummaries,
                evidenceItemSummaries,
                correctionSummaries,
                reviewSummaries,
                exceptionSummaries,
                appointmentSummaries,
                contactAttemptSummaries,
                technicianSummaries,
                clock.instant());
    }

    /**
     * M221：NETWORK sla.read 已 soft-gate；按本网点 ACTIVE taskIds 过滤后计数。
     */
    private NetworkPortalWorkOrderWorkspaceSlaSummary loadSlaSummary(
            CurrentPrincipal actor,
            String correlationId,
            UUID workOrderId,
            UUID projectId,
            UUID networkId,
            Set<UUID> activeTaskIds
    ) {
        List<SlaInstanceItem> items = slaQueries.listForWorkOrderOnNetwork(
                actor, correlationId, workOrderId, projectId, networkId, null, SLA_WORKSPACE_LIMIT)
                .items();
        int open = 0;
        int breached = 0;
        for (SlaInstanceItem item : items) {
            if (item.taskId() == null || !activeTaskIds.contains(item.taskId())) {
                continue;
            }
            if (OPEN_SLA_STATUSES.contains(item.status())) {
                open++;
            }
            if ("BREACHED".equals(item.status())) {
                breached++;
            }
        }
        return new NetworkPortalWorkOrderWorkspaceSlaSummary(open, breached);
    }

    /**
     * M222：NETWORK visit.read 已 soft-gate；按本网点 + ACTIVE taskIds 过滤后投影摘要。
     */
    private List<NetworkPortalWorkspaceVisitSummary> loadVisitSummaries(
            CurrentPrincipal actor,
            String correlationId,
            UUID workOrderId,
            UUID networkId,
            Set<UUID> activeTaskIds
    ) {
        return visits.listByWorkOrderOnNetwork(actor, correlationId, workOrderId, networkId).stream()
                .filter(visit -> visit.taskId() != null && activeTaskIds.contains(visit.taskId()))
                .sorted(Comparator.comparingInt(VisitView::visitSequence)
                        .thenComparing(VisitView::visitId))
                .limit(WORKSPACE_VISIT_LIMIT)
                .map(this::toVisitSummary)
                .toList();
    }

    /**
     * M222：NETWORK form.read 已 soft-gate；仅 fan-in 本网点 ACTIVE taskIds。
     */
    private List<NetworkPortalWorkspaceFormSubmissionSummary> loadFormSubmissionSummaries(
            CurrentPrincipal actor,
            String correlationId,
            UUID networkId,
            Set<UUID> activeTaskIds
    ) {
        List<NetworkPortalWorkspaceFormSubmissionSummary> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            for (FormSubmissionSummaryView row : formSubmissions.listForTaskOnNetwork(
                    actor, correlationId, taskId, networkId)) {
                collected.add(toFormSubmissionSummary(row));
            }
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalWorkspaceFormSubmissionSummary::submittedAt)
                        .thenComparing(NetworkPortalWorkspaceFormSubmissionSummary::submissionId))
                .limit(WORKSPACE_FORM_LIMIT)
                .toList();
    }

    private NetworkPortalWorkspaceVisitSummary toVisitSummary(VisitView visit) {
        return new NetworkPortalWorkspaceVisitSummary(
                visit.visitId(),
                visit.taskId(),
                visit.appointmentId(),
                visit.visitSequence(),
                visit.technicianId(),
                visit.networkId(),
                visit.status(),
                visit.checkInCapturedAt(),
                visit.checkInReceivedAt(),
                visit.geofenceResult(),
                visit.policyDecision(),
                visit.checkOutCapturedAt(),
                visit.checkOutReceivedAt(),
                visit.resultCode(),
                visit.exceptionCode(),
                visit.aggregateVersion());
    }

    private NetworkPortalWorkspaceFormSubmissionSummary toFormSubmissionSummary(
            FormSubmissionSummaryView submission
    ) {
        return new NetworkPortalWorkspaceFormSubmissionSummary(
                submission.submissionId(),
                submission.taskId(),
                submission.projectId(),
                submission.formVersionId(),
                submission.formKey(),
                submission.submissionVersion(),
                submission.contentDigest(),
                submission.validationStatus(),
                submission.errorCount(),
                submission.warningCount(),
                submission.submittedAt());
    }

    /**
     * M223：NETWORK evidence.read 已 soft-gate；仅 fan-in ACTIVE taskIds。
     * OnNetwork 端口对未解析任务返回空列表，避免污染工作区只读事务。
     */
    private List<NetworkPortalWorkspaceEvidenceSlotSummary> loadEvidenceSlotSummaries(
            CurrentPrincipal actor,
            String correlationId,
            UUID networkId,
            Set<UUID> activeTaskIds
    ) {
        List<NetworkPortalWorkspaceEvidenceSlotSummary> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            for (EvidenceSlotView slot : evidenceSlots.listForTaskOnNetwork(
                    actor, correlationId, taskId, networkId)) {
                collected.add(toEvidenceSlotSummary(slot));
            }
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalWorkspaceEvidenceSlotSummary::templateKey)
                        .thenComparing(NetworkPortalWorkspaceEvidenceSlotSummary::requirementCode)
                        .thenComparing(NetworkPortalWorkspaceEvidenceSlotSummary::slotId))
                .limit(WORKSPACE_EVIDENCE_LIMIT)
                .toList();
    }

    /**
     * M223：NETWORK evidence.read 已 soft-gate；仅 fan-in ACTIVE taskIds。
     */
    private List<NetworkPortalWorkspaceEvidenceItemSummary> loadEvidenceItemSummaries(
            CurrentPrincipal actor,
            String correlationId,
            UUID networkId,
            Set<UUID> activeTaskIds
    ) {
        List<NetworkPortalWorkspaceEvidenceItemSummary> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            evidenceItems.listSummariesForTaskOnNetwork(
                            actor, correlationId, taskId, networkId)
                    .stream()
                    .map(this::toEvidenceItemSummary)
                    .forEach(collected::add);
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalWorkspaceEvidenceItemSummary::evidenceSlotId)
                        .thenComparingInt(NetworkPortalWorkspaceEvidenceItemSummary::itemOrdinal)
                        .thenComparing(NetworkPortalWorkspaceEvidenceItemSummary::evidenceItemId))
                .limit(WORKSPACE_EVIDENCE_LIMIT)
                .toList();
    }

    private NetworkPortalWorkspaceEvidenceSlotSummary toEvidenceSlotSummary(EvidenceSlotView slot) {
        return new NetworkPortalWorkspaceEvidenceSlotSummary(
                slot.slotId(),
                slot.taskId(),
                slot.projectId(),
                slot.templateKey(),
                slot.templateVersion(),
                slot.requirementCode(),
                slot.occurrenceKey(),
                slot.requirementName(),
                slot.mediaType(),
                slot.required(),
                slot.minCount(),
                slot.maxCount(),
                slot.status(),
                slot.resolvedAt(),
                slot.slotGeneration(),
                slot.active(),
                slot.transition(),
                slot.requiredDisposition());
    }

    private NetworkPortalWorkspaceEvidenceItemSummary toEvidenceItemSummary(
            EvidenceItemSummaryView item
    ) {
        return new NetworkPortalWorkspaceEvidenceItemSummary(
                item.evidenceItemId(),
                item.taskId(),
                item.projectId(),
                item.evidenceSlotId(),
                item.itemOrdinal(),
                item.status(),
                item.revisionCount(),
                item.latestRevisionNumber(),
                item.latestRevisionStatus());
    }

    /**
     * M225：NETWORK evidence.read 已 soft-gate；仅 fan-in ACTIVE taskIds；含全部状态。
     */
    private List<NetworkPortalWorkspaceCorrectionCaseSummary> loadCorrectionSummaries(
            CurrentPrincipal actor,
            String correlationId,
            Set<UUID> activeTaskIds
    ) {
        List<NetworkPortalWorkspaceCorrectionCaseSummary> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            for (CorrectionCaseView row : corrections.listForTask(actor, correlationId, taskId)) {
                collected.add(toCorrectionCaseSummary(row));
            }
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalWorkspaceCorrectionCaseSummary::createdAt)
                        .thenComparing(NetworkPortalWorkspaceCorrectionCaseSummary::correctionCaseId))
                .limit(WORKSPACE_CORRECTION_LIMIT)
                .toList();
    }

    /**
     * M229：NETWORK evidence.read 已 soft-gate；仅 fan-in ACTIVE taskIds；含全部状态。
     */
    private List<NetworkPortalWorkspaceReviewCaseSummary> loadReviewSummaries(
            CurrentPrincipal actor,
            String correlationId,
            Set<UUID> activeTaskIds
    ) {
        List<NetworkPortalWorkspaceReviewCaseSummary> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            for (ReviewCaseView row : reviews.listForTask(actor, correlationId, taskId)) {
                collected.add(toReviewCaseSummary(row));
            }
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalWorkspaceReviewCaseSummary::createdAt)
                        .thenComparing(NetworkPortalWorkspaceReviewCaseSummary::reviewCaseId))
                .limit(WORKSPACE_REVIEW_LIMIT)
                .toList();
    }

    /**
     * M226：NETWORK operations.exception.read 已 soft-gate；仅 fan-in ACTIVE taskIds；含全部状态。
     */
    private List<NetworkPortalExceptionItem> loadExceptionSummaries(
            CurrentPrincipal actor,
            String correlationId,
            Set<UUID> activeTaskIds
    ) {
        List<NetworkPortalExceptionItem> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            for (OperationalExceptionItem row : exceptions.listForTask(actor, correlationId, taskId)) {
                collected.add(toExceptionItem(row));
            }
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalExceptionItem::openedAt)
                        .thenComparing(NetworkPortalExceptionItem::exceptionId))
                .limit(WORKSPACE_EXCEPTION_LIMIT)
                .toList();
    }

    /**
     * M227：NETWORK networkPortal.manageAppointment 已 soft-gate；仅 fan-in ACTIVE taskIds；
     * 另按可信 networkId 过滤 assignedNetworkId。
     */
    private List<NetworkPortalWorkspaceAppointmentSummary> loadAppointmentSummaries(
            CurrentPrincipal actor,
            String correlationId,
            UUID networkId,
            Set<UUID> activeTaskIds
    ) {
        String trustedNetwork = networkId.toString();
        List<NetworkPortalWorkspaceAppointmentSummary> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            for (AppointmentView row : appointments.listByTask(actor, correlationId, taskId)) {
                if (!trustedNetwork.equals(row.assignedNetworkId())) {
                    continue;
                }
                collected.add(toAppointmentSummary(row));
            }
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalWorkspaceAppointmentSummary::createdAt)
                        .thenComparing(NetworkPortalWorkspaceAppointmentSummary::appointmentId))
                .limit(WORKSPACE_APPOINTMENT_LIMIT)
                .toList();
    }

    /**
     * M227：NETWORK networkPortal.manageAppointment 已 soft-gate；仅 fan-in ACTIVE taskIds。
     */
    private List<NetworkPortalWorkspaceContactAttemptSummary> loadContactAttemptSummaries(
            CurrentPrincipal actor,
            String correlationId,
            Set<UUID> activeTaskIds
    ) {
        List<NetworkPortalWorkspaceContactAttemptSummary> collected = new ArrayList<>();
        for (UUID taskId : activeTaskIds) {
            for (ContactAttemptView row : appointments.listContactAttempts(actor, correlationId, taskId)) {
                collected.add(toContactAttemptSummary(row));
            }
        }
        return collected.stream()
                .sorted(Comparator
                        .comparing(NetworkPortalWorkspaceContactAttemptSummary::startedAt,
                                Comparator.reverseOrder())
                        .thenComparing(NetworkPortalWorkspaceContactAttemptSummary::contactAttemptId))
                .limit(WORKSPACE_CONTACT_LIMIT)
                .toList();
    }

    private static NetworkPortalWorkspaceAppointmentSummary toAppointmentSummary(
            AppointmentView appointment
    ) {
        AppointmentRevisionView current = appointment.revisions().stream()
                .filter(revision -> revision.revisionNo() == appointment.currentRevisionNo())
                .findFirst()
                .orElse(appointment.revisions().isEmpty() ? null : appointment.revisions().getLast());
        AppointmentWindow window = current == null ? null : current.window();
        return new NetworkPortalWorkspaceAppointmentSummary(
                appointment.appointmentId(),
                appointment.taskId(),
                appointment.type().name(),
                appointment.status(),
                appointment.assignedNetworkId(),
                appointment.technicianId(),
                appointment.currentRevisionNo(),
                window == null ? null : window.start(),
                window == null ? null : window.end(),
                window == null ? null : window.timezone(),
                window == null ? null : window.estimatedDurationMinutes(),
                appointment.aggregateVersion(),
                appointment.createdAt());
    }

    private static NetworkPortalWorkspaceContactAttemptSummary toContactAttemptSummary(
            ContactAttemptView attempt
    ) {
        return new NetworkPortalWorkspaceContactAttemptSummary(
                attempt.contactAttemptId(),
                attempt.taskId(),
                attempt.projectId(),
                attempt.workOrderId(),
                attempt.channel(),
                attempt.startedAt(),
                attempt.endedAt(),
                attempt.resultCode().name(),
                attempt.nextContactAt(),
                attempt.createdAt());
    }

    /**
     * M228：NETWORK technician.readOwnNetwork 已 soft-gate；仅返回工作区 technicianId 命中项。
     */
    private List<NetworkPortalTechnicianItem> loadTechnicianSummaries(
            String tenantId,
            UUID networkId,
            Set<String> wantedTechnicianIds
    ) {
        if (wantedTechnicianIds.isEmpty()) {
            return List.of();
        }
        return technicians.listActiveTechnicians(tenantId, networkId).stream()
                .filter(row -> wantedTechnicianIds.contains(row.technicianProfileId().toString()))
                .map(row -> new NetworkPortalTechnicianItem(
                        row.membershipId(),
                        row.technicianProfileId(),
                        row.principalId(),
                        row.displayName(),
                        row.profileStatus(),
                        row.membershipStatus(),
                        row.validFrom(),
                        row.validTo(),
                        row.membershipVersion()))
                .sorted(Comparator
                        .comparing(NetworkPortalTechnicianItem::displayName,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(NetworkPortalTechnicianItem::technicianProfileId))
                .toList();
    }

    private NetworkPortalWorkspaceCorrectionCaseSummary toCorrectionCaseSummary(
            CorrectionCaseView correction
    ) {
        return new NetworkPortalWorkspaceCorrectionCaseSummary(
                correction.correctionCaseId(),
                correction.taskId(),
                correction.projectId(),
                correction.sourceReviewCaseId(),
                correction.sourceReviewDecisionId(),
                correction.reasonCodes(),
                correction.correctionTaskId(),
                correction.status(),
                correction.createdAt(),
                correction.latestResubmissionSnapshotId(),
                correction.closedAt(),
                correction.waivedAt(),
                correction.resubmissions().stream()
                        .map(this::toCorrectionResubmissionSummary)
                        .toList());
    }

    private NetworkPortalWorkspaceReviewCaseSummary toReviewCaseSummary(ReviewCaseView review) {
        return new NetworkPortalWorkspaceReviewCaseSummary(
                review.reviewCaseId(),
                review.taskId(),
                review.projectId(),
                review.evidenceSetSnapshotId(),
                review.scopeType(),
                review.origin(),
                review.policyVersion(),
                review.status(),
                review.createdAt(),
                review.decidedAt(),
                review.sourceReviewCaseId(),
                review.externalSubmissionRef(),
                review.callbackBatchRef(),
                review.mappingVersionId(),
                review.reopenedFromReviewCaseId(),
                review.reopenTriggerRef(),
                review.decisions() == null
                        ? List.of()
                        : review.decisions().stream().map(this::toReviewDecisionSummary).toList());
    }

    private NetworkPortalWorkspaceReviewDecisionSummary toReviewDecisionSummary(
            ReviewDecisionView decision
    ) {
        // note / approvalRef / decidedBy 不进入工作区摘要，避免自由文本和操作者信息扩散。
        return new NetworkPortalWorkspaceReviewDecisionSummary(
                decision.reviewDecisionId(),
                decision.decisionOrdinal(),
                decision.decision(),
                decision.decisionSource(),
                decision.reasonCodes(),
                decision.decidedAt());
    }

    private NetworkPortalWorkspaceCorrectionResubmissionSummary toCorrectionResubmissionSummary(
            CorrectionResubmissionView resubmission
    ) {
        return new NetworkPortalWorkspaceCorrectionResubmissionSummary(
                resubmission.correctionResubmissionId(),
                resubmission.resubmissionOrdinal(),
                resubmission.evidenceSetSnapshotId(),
                resubmission.submittedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalTaskItem> listTasks(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        List<NetworkPortalTaskItem> items = new ArrayList<>();
        for (NetworkActiveAssignmentView row : active) {
            TaskFulfillmentContext task = tasks.find(actor.tenantId(), row.taskId()).orElse(null);
            items.add(new NetworkPortalTaskItem(
                    row.taskId(),
                    row.workOrderId(),
                    task == null ? null : task.projectId(),
                    task == null ? null : task.taskType(),
                    task == null ? null : task.taskKind(),
                    task == null ? null : task.stageCode(),
                    task == null ? null : task.status(),
                    row.businessType(),
                    row.technicianId(),
                    row.effectiveFrom()));
        }
        List<NetworkPortalTaskItem> taskItems = List.copyOf(items);
        List<NetworkPortalTechnicianItem> technicianSummaries = null;
        if (hasNetworkCapability(actor, correlationId, TECHNICIAN_READ_OWN, networkId)) {
            Set<String> wantedTechnicianIds = new LinkedHashSet<>();
            for (NetworkPortalTaskItem item : taskItems) {
                if (item.technicianId() != null && !item.technicianId().isBlank()) {
                    wantedTechnicianIds.add(item.technicianId());
                }
            }
            technicianSummaries = loadTechnicianSummaries(
                    actor.tenantId(), networkId, wantedTechnicianIds);
        }
        return new NetworkPortalPage<>(
                networkId, taskItems, clock.instant(), technicianSummaries);
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalTechnicianItem> listTechnicians(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);
        List<NetworkPortalTechnicianView> rows = technicians.listActiveTechnicians(actor.tenantId(), networkId);
        List<NetworkPortalTechnicianItem> items = rows.stream()
                .map(row -> new NetworkPortalTechnicianItem(
                        row.membershipId(),
                        row.technicianProfileId(),
                        row.principalId(),
                        row.displayName(),
                        row.profileStatus(),
                        row.membershipStatus(),
                        row.validFrom(),
                        row.validTo(),
                        row.membershipVersion()))
                .toList();
        return new NetworkPortalPage<>(networkId, items, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalCapacityItem> listCapacity(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        return new NetworkPortalPage<>(networkId, capacityItems(actor.tenantId(), networkId), clock.instant());
    }

    /**
     * 本网点工作台。
     * <p>
     * 基座门禁：ACTIVE NetworkMembership + NETWORK {@code networkTask.read}。
     * enrichment 能力用 {@link AuthorizationService#authorize}（非 require）：缺能力仅省略字段，
     * 不导致整页失败。有能力时 fan-in 全量计数，不受 list limit 截断。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalWorkbenchView workbench(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        Set<UUID> workOrders = new LinkedHashSet<>();
        Set<UUID> activeTaskIds = new LinkedHashSet<>();
        int unassigned = 0;
        for (NetworkActiveAssignmentView row : active) {
            workOrders.add(row.workOrderId());
            activeTaskIds.add(row.taskId());
            if (row.technicianId() == null || row.technicianId().isBlank()) {
                unassigned++;
            }
        }
        int technicianCount = technicians.listActiveTechnicians(actor.tenantId(), networkId).size();
        Instant asOf = clock.instant();

        Integer openCorrectionCaseCount = null;
        if (hasNetworkCapability(actor, correlationId, EVIDENCE_READ, networkId)) {
            openCorrectionCaseCount = countOpenCorrections(actor, correlationId, activeTaskIds);
        }
        Integer openOperationalExceptionCount = null;
        if (hasNetworkCapability(actor, correlationId, EXCEPTION_READ, networkId)) {
            openOperationalExceptionCount = countOpenExceptions(actor, correlationId, activeTaskIds);
        }
        Integer pendingQualificationCount = null;
        if (hasNetworkCapability(actor, correlationId, TECHNICIAN_READ_OWN, networkId)) {
            pendingQualificationCount = countPendingQualifications(actor.tenantId(), networkId);
        }
        NetworkPortalWorkOrderWorkspaceSlaSummary slaSummary = null;
        if (hasNetworkCapability(actor, correlationId, SLA_READ, networkId)) {
            slaSummary = loadWorkbenchSlaSummary(
                    actor, correlationId, networkId, workOrders, activeTaskIds);
        }

        return new NetworkPortalWorkbenchView(
                networkId,
                workOrders.size(),
                active.size(),
                technicianCount,
                capacityItems(actor.tenantId(), networkId),
                asOf,
                unassigned,
                openCorrectionCaseCount,
                openOperationalExceptionCount,
                pendingQualificationCount,
                slaSummary);
    }

    /**
     * M224：NETWORK sla.read 已 soft-gate；跨本网点 ACTIVE 工单 fan-in 后按 ACTIVE taskIds 计数。
     */
    private NetworkPortalWorkOrderWorkspaceSlaSummary loadWorkbenchSlaSummary(
            CurrentPrincipal actor,
            String correlationId,
            UUID networkId,
            Set<UUID> workOrderIds,
            Set<UUID> activeTaskIds
    ) {
        Map<UUID, UUID> workOrderProjects = new LinkedHashMap<>();
        for (UUID taskId : activeTaskIds) {
            TaskFulfillmentContext task = tasks.find(actor.tenantId(), taskId).orElse(null);
            if (task == null || task.projectId() == null || task.workOrderId() == null) {
                continue;
            }
            if (!workOrderIds.contains(task.workOrderId())) {
                continue;
            }
            workOrderProjects.putIfAbsent(task.workOrderId(), task.projectId());
        }
        int open = 0;
        int breached = 0;
        for (Map.Entry<UUID, UUID> entry : workOrderProjects.entrySet()) {
            List<SlaInstanceItem> items = slaQueries.listForWorkOrderOnNetwork(
                    actor,
                    correlationId,
                    entry.getKey(),
                    entry.getValue(),
                    networkId,
                    null,
                    SLA_WORKSPACE_LIMIT).items();
            for (SlaInstanceItem item : items) {
                if (item.taskId() == null || !activeTaskIds.contains(item.taskId())) {
                    continue;
                }
                if (OPEN_SLA_STATUSES.contains(item.status())) {
                    open++;
                }
                if ("BREACHED".equals(item.status())) {
                    breached++;
                }
            }
        }
        return new NetworkPortalWorkOrderWorkspaceSlaSummary(open, breached);
    }

    /**
     * enrichment 门禁：authorize + ALLOW，缺能力返回 false，不抛 ACCESS_DENIED。
     */
    private boolean hasNetworkCapability(
            CurrentPrincipal actor, String correlationId, String capability, UUID networkId
    ) {
        AuthorizationDecision decision = authorization.authorize(
                actor,
                AuthorizationRequest.networkCapability(
                        capability,
                        actor.tenantId(),
                        "ServiceNetwork",
                        networkId.toString(),
                        networkId.toString()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private int countOpenCorrections(
            CurrentPrincipal actor, String correlationId, Set<UUID> activeTaskIds
    ) {
        int count = 0;
        for (UUID taskId : activeTaskIds) {
            for (CorrectionCaseView row : corrections.listForTask(actor, correlationId, taskId)) {
                if ("OPEN".equals(row.status())) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countOpenExceptions(
            CurrentPrincipal actor, String correlationId, Set<UUID> activeTaskIds
    ) {
        int count = 0;
        for (UUID taskId : activeTaskIds) {
            for (OperationalExceptionItem row : exceptions.listForTask(actor, correlationId, taskId)) {
                if ("OPEN".equals(row.status())) {
                    count++;
                }
            }
        }
        return count;
    }

    private int countPendingQualifications(String tenantId, UUID networkId) {
        int count = 0;
        for (TechnicianQualificationView row : qualifications.listForActiveTechnicians(tenantId, networkId)) {
            if ("PENDING".equals(row.status())) {
                count++;
            }
        }
        return count;
    }

    /**
     * 本网点整改队列。
     * <p>
     * 事务边界：只读。先门禁再按 ACTIVE NETWORK 任务 fan-in {@code listForTask}；
     * taskId 过滤不在 ACTIVE 集合时返回空页（不泄露他网点任务存在性）。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalCorrectionItem> listCorrections(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID taskId,
            Integer limit
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, EVIDENCE_READ);
        String effectiveStatus = (status == null || status.isBlank()) ? "OPEN" : status.trim();
        int effectiveLimit = normalizeCorrectionLimit(limit);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        Set<UUID> activeTaskIds = new LinkedHashSet<>();
        for (NetworkActiveAssignmentView row : active) {
            activeTaskIds.add(row.taskId());
        }
        List<UUID> taskIds;
        if (taskId != null) {
            if (!activeTaskIds.contains(taskId)) {
                return new NetworkPortalPage<>(networkId, List.of(), clock.instant());
            }
            taskIds = List.of(taskId);
        } else {
            taskIds = List.copyOf(activeTaskIds);
        }
        List<NetworkPortalCorrectionItem> items = new ArrayList<>();
        for (UUID candidateTaskId : taskIds) {
            for (CorrectionCaseView row : corrections.listForTask(actor, correlationId, candidateTaskId)) {
                if (!effectiveStatus.equals(row.status())) {
                    continue;
                }
                items.add(toCorrectionItem(row));
            }
        }
        items.sort(Comparator
                .comparing(NetworkPortalCorrectionItem::createdAt)
                .thenComparing(NetworkPortalCorrectionItem::correctionCaseId));
        if (items.size() > effectiveLimit) {
            items = items.subList(0, effectiveLimit);
        }
        return new NetworkPortalPage<>(networkId, List.copyOf(items), clock.instant());
    }

    /**
     * 本网点整改详情。
     * <p>
     * 失败关闭：CorrectionCaseService.get 鉴权后，ACTIVE NETWORK 责任必须等于上下文网点。
     */
    @Override
    @Transactional(readOnly = true)
    public CorrectionCaseView getCorrection(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID correctionCaseId
    ) {
        Objects.requireNonNull(correctionCaseId, "correctionCaseId");
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, EVIDENCE_READ);
        CorrectionCaseView correction = corrections.get(actor, correlationId, correctionCaseId);
        requireNetworkOwnedTask(actor.tenantId(), correction.taskId(), networkId);
        return correction;
    }

    /**
     * 本网点运营异常队列。
     * <p>
     * 事务边界：只读。先门禁再按 ACTIVE NETWORK 任务 fan-in {@code listForTask}；
     * 可选 severity 过滤；taskId 不在 ACTIVE 集合时返回空页（不泄露他网点任务存在性）。
     * Portal {@code allowedActions} 恒为空（本切片不接受 ACK/resolve）。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalExceptionItem> listExceptions(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID taskId,
            String severity,
            Integer limit
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, EXCEPTION_READ);
        String effectiveStatus = (status == null || status.isBlank()) ? "OPEN" : status.trim();
        String effectiveSeverity = normalizeOptionalSeverity(severity);
        int effectiveLimit = normalizeExceptionLimit(limit);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        Set<UUID> activeTaskIds = new LinkedHashSet<>();
        for (NetworkActiveAssignmentView row : active) {
            activeTaskIds.add(row.taskId());
        }
        List<UUID> taskIds;
        if (taskId != null) {
            if (!activeTaskIds.contains(taskId)) {
                return new NetworkPortalPage<>(networkId, List.of(), clock.instant());
            }
            taskIds = List.of(taskId);
        } else {
            taskIds = List.copyOf(activeTaskIds);
        }
        List<NetworkPortalExceptionItem> items = new ArrayList<>();
        for (UUID candidateTaskId : taskIds) {
            for (OperationalExceptionItem row : exceptions.listForTask(actor, correlationId, candidateTaskId)) {
                if (!effectiveStatus.equals(row.status())) {
                    continue;
                }
                if (effectiveSeverity != null && !effectiveSeverity.equals(row.severity())) {
                    continue;
                }
                items.add(toExceptionItem(row));
            }
        }
        items.sort(Comparator
                .comparing(NetworkPortalExceptionItem::openedAt)
                .thenComparing(NetworkPortalExceptionItem::exceptionId));
        if (items.size() > effectiveLimit) {
            items = items.subList(0, effectiveLimit);
        }
        return new NetworkPortalPage<>(networkId, List.copyOf(items), clock.instant());
    }

    /**
     * 本网点运营异常详情。
     * <p>
     * 失败关闭：Workbench get 鉴权后，ACTIVE NETWORK 责任必须等于上下文网点；
     * Portal allowedActions 恒为空。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalExceptionItem getException(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID exceptionId
    ) {
        Objects.requireNonNull(exceptionId, "exceptionId");
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, EXCEPTION_READ);
        OperationalExceptionItem item = exceptions.get(actor, correlationId, exceptionId);
        if (item.taskId() == null) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "任务对本网点没有 ACTIVE NETWORK 责任");
        }
        requireNetworkOwnedTask(actor.tenantId(), item.taskId(), networkId);
        return toExceptionItem(item);
    }

    /**
     * 本网点师傅资质列表。
     * <p>
     * 事务边界：只读。先门禁再 fan-in ACTIVE 师傅资质；可选 status / technicianProfileId 过滤；
     * 按 submittedAt/id 正序，内存 limit（1～100，默认 50）。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalQualificationItem> listQualifications(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID technicianProfileId,
            Integer limit
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);
        String effectiveStatus = normalizeOptionalQualificationStatus(status);
        int effectiveLimit = normalizeQualificationLimit(limit);
        List<NetworkPortalQualificationItem> items = new ArrayList<>();
        for (TechnicianQualificationView row :
                qualifications.listForActiveTechnicians(actor.tenantId(), networkId)) {
            if (effectiveStatus != null && !effectiveStatus.equals(row.status())) {
                continue;
            }
            if (technicianProfileId != null && !technicianProfileId.equals(row.technicianProfileId())) {
                continue;
            }
            items.add(toQualificationItem(row));
        }
        items.sort(Comparator
                .comparing(NetworkPortalQualificationItem::submittedAt)
                .thenComparing(NetworkPortalQualificationItem::id));
        if (items.size() > effectiveLimit) {
            items = items.subList(0, effectiveLimit);
        }
        return new NetworkPortalPage<>(networkId, List.copyOf(items), clock.instant());
    }

    /**
     * 本网点师傅资质详情。
     * <p>
     * 失败关闭：资质不存在 RESOURCE_NOT_FOUND；所属师傅非本网点 ACTIVE 则 ACCESS_DENIED。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalQualificationItem getQualification(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID qualificationId
    ) {
        Objects.requireNonNull(qualificationId, "qualificationId");
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);
        TechnicianQualificationView view = qualifications.findById(actor.tenantId(), qualificationId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "资质记录不存在"));
        boolean activeOnNetwork = technicians.listActiveTechnicians(actor.tenantId(), networkId).stream()
                .anyMatch(tech -> tech.technicianProfileId().equals(view.technicianProfileId()));
        if (!activeOnNetwork) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "资质不属于本网点 ACTIVE 师傅");
        }
        return toQualificationItem(view);
    }

    private void requireNetworkOwnedTask(String tenantId, UUID taskId, UUID networkId) {
        ActiveServiceResponsibility responsibility = responsibilities.find(tenantId, taskId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.ACCESS_DENIED,
                        "任务对本网点没有 ACTIVE NETWORK 责任"));
        if (responsibility.networkId() == null
                || !responsibility.networkId().equals(networkId.toString())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "任务对本网点没有 ACTIVE NETWORK 责任");
        }
    }

    /**
     * 本网点师傅关系列表。
     * <p>
     * 事务边界：只读。先门禁再 fan-in 本网点关系；status 默认 ACTIVE；
     * 可选 technicianProfileId；按 createdAt/id 正序，内存 limit（1～100，默认 50）。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalMembershipItem> listMemberships(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String status,
            UUID technicianProfileId,
            Integer limit
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);
        String effectiveStatus = normalizeMembershipStatus(status);
        int effectiveLimit = normalizeMembershipLimit(limit);
        List<NetworkPortalMembershipItem> items = new ArrayList<>();
        for (NetworkTechnicianMembershipView row : memberships.listForNetwork(actor.tenantId(), networkId)) {
            if (!effectiveStatus.equals(row.status())) {
                continue;
            }
            if (technicianProfileId != null && !technicianProfileId.equals(row.technicianProfileId())) {
                continue;
            }
            items.add(toMembershipItem(row));
        }
        items.sort(Comparator
                .comparing(NetworkPortalMembershipItem::createdAt)
                .thenComparing(NetworkPortalMembershipItem::id));
        if (items.size() > effectiveLimit) {
            items = items.subList(0, effectiveLimit);
        }
        return new NetworkPortalPage<>(networkId, List.copyOf(items), clock.instant());
    }

    /**
     * 本网点师傅关系详情。
     * <p>
     * 失败关闭：不存在 RESOURCE_NOT_FOUND；serviceNetworkId ≠ 上下文则 ACCESS_DENIED。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalMembershipItem getMembership(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID membershipId
    ) {
        Objects.requireNonNull(membershipId, "membershipId");
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);
        NetworkTechnicianMembershipView view = memberships.findById(actor.tenantId(), membershipId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "师傅服务关系不存在"));
        if (!networkId.equals(view.serviceNetworkId())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "师傅服务关系不属于本网点");
        }
        return toMembershipItem(view);
    }

    private static NetworkPortalQualificationItem toQualificationItem(TechnicianQualificationView row) {
        return new NetworkPortalQualificationItem(
                row.id(),
                row.technicianProfileId(),
                row.qualificationCode(),
                row.status(),
                row.validFrom(),
                row.validTo(),
                row.submittedBy(),
                row.submittedAt(),
                row.decidedBy(),
                row.decidedAt(),
                row.decisionReason(),
                row.version());
    }

    private static NetworkPortalMembershipItem toMembershipItem(NetworkTechnicianMembershipView row) {
        return new NetworkPortalMembershipItem(
                row.id(),
                row.serviceNetworkId(),
                row.technicianProfileId(),
                row.status(),
                row.validFrom(),
                row.validTo(),
                row.version(),
                row.createdAt(),
                row.terminatedAt(),
                row.terminateReason());
    }

    private static int normalizeQualificationLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_QUALIFICATION_LIMIT;
        }
        if (limit < 1 || limit > MAX_QUALIFICATION_LIMIT) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "limit 须在 1～100 之间");
        }
        return limit;
    }

    private static int normalizeMembershipLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_MEMBERSHIP_LIMIT;
        }
        if (limit < 1 || limit > MAX_MEMBERSHIP_LIMIT) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "limit 须在 1～100 之间");
        }
        return limit;
    }

    private static String normalizeOptionalQualificationStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("PENDING", "APPROVED", "REJECTED", "EXPIRED").contains(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "status is invalid");
        }
        return normalized;
    }

    /** status 缺省 ACTIVE；非法值失败关闭。 */
    private static String normalizeMembershipStatus(String status) {
        if (status == null || status.isBlank()) {
            return "ACTIVE";
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "TERMINATED").contains(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "status is invalid");
        }
        return normalized;
    }

    private static NetworkPortalCorrectionItem toCorrectionItem(CorrectionCaseView row) {
        int resubmissionCount = row.resubmissions() == null ? 0 : row.resubmissions().size();
        return new NetworkPortalCorrectionItem(
                row.correctionCaseId(),
                row.projectId(),
                row.taskId(),
                row.sourceReviewCaseId(),
                row.sourceReviewDecisionId(),
                row.reasonCodes(),
                row.correctionTaskId(),
                row.status(),
                row.createdAt(),
                row.latestResubmissionSnapshotId(),
                row.closedAt(),
                row.waivedAt(),
                resubmissionCount);
    }

    private static NetworkPortalExceptionItem toExceptionItem(OperationalExceptionItem row) {
        return new NetworkPortalExceptionItem(
                row.exceptionId(),
                row.projectId(),
                row.sourceType(),
                row.category(),
                row.severity(),
                row.errorCode(),
                row.status(),
                row.workOrderId(),
                row.taskId(),
                row.handlingTaskId(),
                row.occurrenceCount(),
                row.openedAt(),
                row.lastDetectedAt(),
                row.resolvedAt(),
                row.resolutionCode(),
                List.of());
    }

    private static int normalizeCorrectionLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_CORRECTION_LIMIT;
        }
        if (limit < 1 || limit > MAX_CORRECTION_LIMIT) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "limit 须在 1～100 之间");
        }
        return limit;
    }

    private static int normalizeExceptionLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_EXCEPTION_LIMIT;
        }
        if (limit < 1 || limit > MAX_EXCEPTION_LIMIT) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "limit 须在 1～100 之间");
        }
        return limit;
    }

    private static String normalizeOptionalSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return null;
        }
        String normalized = severity.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("P0", "P1", "P2", "P3").contains(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "severity is invalid");
        }
        return normalized;
    }

    private List<NetworkPortalCapacityItem> capacityItems(String tenantId, UUID networkId) {
        List<NetworkCapacityCounterView> rows = capacity.listForNetwork(tenantId, networkId.toString());
        return rows.stream()
                .map(row -> new NetworkPortalCapacityItem(
                        row.capacityCounterId(),
                        row.businessType(),
                        row.maxUnits(),
                        row.occupiedUnits(),
                        row.availableUnits(),
                        row.version(),
                        row.updatedAt()))
                .toList();
    }

    /**
     * 解析并校验可信网点上下文。membership 无效用 PORTAL_CONTEXT_INVALID；
     * 成员有效但缺能力用 ACCESS_DENIED（authorization.require）。
     */
    private UUID requireAuthorizedNetwork(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String capability
    ) {
        UUID networkId = parseNetworkContext(networkContextHeader);
        UUID principalId = requirePrincipalUuid(actor);
        Instant at = clock.instant();
        boolean member = affiliations.listActiveNetworkMemberships(actor.tenantId(), principalId, at).stream()
                .map(NetworkMembershipView::serviceNetworkId)
                .anyMatch(networkId::equals);
        if (!member) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体不能使用请求的 Network Portal 上下文");
        }
        authorization.require(actor, AuthorizationRequest.networkCapability(
                capability, actor.tenantId(), "ServiceNetwork", networkId.toString(), networkId.toString()),
                correlationId);
        return networkId;
    }

    private static UUID parseNetworkContext(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "缺少 X-Network-Context");
        }
        String raw = header.trim();
        String uuidPart = raw;
        if (raw.startsWith(CONTEXT_PREFIX)) {
            uuidPart = raw.substring(CONTEXT_PREFIX.length());
        } else if (raw.contains("|")) {
            // 拒绝 TECHNICIAN|NETWORK|... 或其他 Portal 形态伪装
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Network Portal 上下文形态无效");
        }
        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Network Portal 上下文形态无效");
        }
    }

    private static UUID requirePrincipalUuid(CurrentPrincipal actor) {
        try {
            return UUID.fromString(actor.principalId());
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "当前主体无法形成 Network Portal 上下文");
        }
    }
}
