package com.serviceos.readmodel.application;

import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.TechnicianScheduleAppointmentQuery;
import com.serviceos.appointment.api.TechnicianScheduleAppointmentView;
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
import com.serviceos.network.api.ServiceNetworkCoverageQuery;
import com.serviceos.network.api.ServiceNetworkCoverageView;
import com.serviceos.network.api.TechnicianQualificationView;
import com.serviceos.project.api.RegionCatalogNameQuery;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.readmodel.api.NetworkPortalAppointmentCalendarDay;
import com.serviceos.readmodel.api.NetworkPortalAppointmentCalendarView;
import com.serviceos.readmodel.api.NetworkPortalAssignCandidateItem;
import com.serviceos.readmodel.api.NetworkPortalAssignCandidatePage;
import com.serviceos.readmodel.api.NetworkPortalCapacityItem;
import com.serviceos.readmodel.api.NetworkPortalCorrectionItem;
import com.serviceos.readmodel.api.NetworkPortalDirectorySlaRiskSummary;
import com.serviceos.readmodel.api.NetworkPortalExceptionItem;
import com.serviceos.readmodel.api.NetworkPortalMembershipItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQualificationItem;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.readmodel.api.NetworkPortalTechnicianItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchAppointmentItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchTimelineBucket;
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
import com.serviceos.workorder.api.WorkOrderDirectoryHeader;
import com.serviceos.workorder.api.WorkOrderDirectoryHeaderQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
    private static final int WORKBENCH_TODAY_APPOINTMENT_LIMIT = 50;
    private static final int CALENDAR_DEFAULT_SPAN_DAYS = 14;
    private static final int CALENDAR_MAX_SPAN_DAYS = 31;
    private static final int CALENDAR_APPOINTMENT_LIMIT = 200;
    /** Network 工作台运营日边界；现场履约首批区域为中国时区，不得按浏览器本地日猜测。 */
    private static final ZoneId NETWORK_OPERATIONAL_ZONE = ZoneId.of("Asia/Shanghai");
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
    private final TechnicianScheduleAppointmentQuery scheduleAppointments;
    private final WorkOrderDirectoryHeaderQuery workOrderHeaders;
    private final ServiceNetworkCoverageQuery coverages;
    private final RegionCatalogNameQuery regionNames;
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
            TechnicianScheduleAppointmentQuery scheduleAppointments,
            WorkOrderDirectoryHeaderQuery workOrderHeaders,
            ServiceNetworkCoverageQuery coverages,
            RegionCatalogNameQuery regionNames,
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
        this.scheduleAppointments = scheduleAppointments;
        this.workOrderHeaders = workOrderHeaders;
        this.coverages = coverages;
        this.regionNames = regionNames;
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
        Map<UUID, WorkOrderDirectoryHeader> headers = loadWorkOrderHeaders(
                actor.tenantId(), byWorkOrder.keySet());
        List<NetworkPortalWorkOrderItem> workOrderItems = byWorkOrder.values().stream()
                .map(item -> withWorkOrderHeader(item, headers.get(item.workOrderId())))
                .toList();
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
        List<NetworkPortalWorkspaceAppointmentSummary> appointmentSummaries = null;
        List<NetworkPortalWorkspaceContactAttemptSummary> contactAttemptSummaries = null;
        Set<UUID> pageTaskIds = new LinkedHashSet<>();
        for (NetworkPortalWorkOrderItem item : workOrderItems) {
            pageTaskIds.addAll(item.taskIds());
        }
        if (hasNetworkCapability(actor, correlationId, MANAGE_APPOINTMENT, networkId)) {
            appointmentSummaries = loadAppointmentSummaries(
                    actor, correlationId, networkId, pageTaskIds);
            contactAttemptSummaries = loadContactAttemptSummaries(
                    actor, correlationId, pageTaskIds);
        }
        List<NetworkPortalWorkspaceCorrectionCaseSummary> correctionSummaries = null;
        List<NetworkPortalWorkspaceEvidenceSlotSummary> evidenceSlotSummaries = null;
        List<NetworkPortalWorkspaceEvidenceItemSummary> evidenceItemSummaries = null;
        if (hasNetworkCapability(actor, correlationId, EVIDENCE_READ, networkId)) {
            correctionSummaries = loadCorrectionSummaries(actor, correlationId, pageTaskIds);
            evidenceSlotSummaries = loadEvidenceSlotSummaries(
                    actor, correlationId, networkId, pageTaskIds);
            evidenceItemSummaries = loadEvidenceItemSummaries(
                    actor, correlationId, networkId, pageTaskIds);
        }
        List<NetworkPortalDirectorySlaRiskSummary> slaRiskSummaries = null;
        if (hasNetworkCapability(actor, correlationId, SLA_READ, networkId)) {
            slaRiskSummaries = loadDirectorySlaRiskSummariesForWorkOrders(
                    actor, correlationId, networkId, workOrderItems);
        }
        return new NetworkPortalPage<>(
                networkId, workOrderItems, clock.instant(),
                technicianSummaries, appointmentSummaries, contactAttemptSummaries,
                correctionSummaries, evidenceSlotSummaries, evidenceItemSummaries, slaRiskSummaries);
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
     * M223 / M235：NETWORK evidence.read 已 soft-gate；仅 fan-in 给定 taskIds。
     * 工作台传入 ACTIVE taskIds；目录页传入当前页 taskIds。
     * OnNetwork 端口对未解析任务返回空列表，避免污染只读事务。
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
     * M223 / M235：NETWORK evidence.read 已 soft-gate；仅 fan-in 给定 taskIds。
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

    /** M236：为本页 workOrderIds 装载非 PII 工单头；缺失静默跳过。 */
    private Map<UUID, WorkOrderDirectoryHeader> loadWorkOrderHeaders(
            String tenantId,
            Set<UUID> workOrderIds
    ) {
        Map<UUID, WorkOrderDirectoryHeader> headers = new LinkedHashMap<>();
        for (UUID workOrderId : workOrderIds) {
            workOrderHeaders.find(tenantId, workOrderId)
                    .ifPresent(header -> headers.put(workOrderId, header));
        }
        return headers;
    }

    private static NetworkPortalWorkOrderItem withWorkOrderHeader(
            NetworkPortalWorkOrderItem item,
            WorkOrderDirectoryHeader header
    ) {
        if (header == null) {
            return item;
        }
        return new NetworkPortalWorkOrderItem(
                item.workOrderId(),
                item.projectId(),
                item.taskIds(),
                item.businessType(),
                item.technicianId(),
                item.effectiveFrom(),
                header.brandCode(),
                header.serviceProductCode(),
                header.provinceCode(),
                header.cityCode(),
                header.districtCode(),
                header.receivedAt());
    }

    private static NetworkPortalTaskItem withTaskHeader(
            NetworkPortalTaskItem item,
            WorkOrderDirectoryHeader header
    ) {
        if (header == null) {
            return item;
        }
        return new NetworkPortalTaskItem(
                item.taskId(),
                item.workOrderId(),
                item.projectId(),
                item.taskType(),
                item.taskKind(),
                item.stageCode(),
                item.status(),
                item.businessType(),
                item.technicianId(),
                item.effectiveFrom(),
                header.brandCode(),
                header.serviceProductCode(),
                header.provinceCode(),
                header.cityCode(),
                header.districtCode(),
                header.receivedAt());
    }

    /**
     * M225 / M233：NETWORK evidence.read 已 soft-gate；仅 fan-in 给定 taskIds；含全部状态。
     * 工作台传入 ACTIVE taskIds；目录页传入当前页 taskIds。
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
     * M421：摘要复用开放任务/资质计数，避免列表与 fan-in 口径分裂。
     */
    private List<NetworkPortalTechnicianItem> loadTechnicianSummaries(
            String tenantId,
            UUID networkId,
            Set<String> wantedTechnicianIds
    ) {
        if (wantedTechnicianIds.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> openTaskCounts = openTaskCountsForNetwork(tenantId, networkId);
        Map<UUID, int[]> qualificationCounts = qualificationCountsForNetwork(tenantId, networkId);
        return technicians.listActiveTechnicians(tenantId, networkId).stream()
                .filter(row -> wantedTechnicianIds.contains(row.technicianProfileId().toString()))
                .map(row -> toTechnicianItem(row, openTaskCounts, qualificationCounts))
                .sorted(Comparator
                        .comparing(NetworkPortalTechnicianItem::displayName,
                                Comparator.nullsLast(String::compareTo))
                        .thenComparing(NetworkPortalTechnicianItem::technicianProfileId))
                .toList();
    }

    /**
     * 本网点 ACTIVE NETWORK 责任任务上已指派师傅的开放任务计数；键为 technicianProfileId 字符串。
     */
    private Map<String, Integer> openTaskCountsForNetwork(String tenantId, UUID networkId) {
        Map<String, Integer> openTaskCounts = new LinkedHashMap<>();
        for (NetworkActiveAssignmentView row : assignments.listActiveForNetwork(
                tenantId, networkId.toString())) {
            if (row.technicianId() != null && !row.technicianId().isBlank()) {
                openTaskCounts.merge(row.technicianId(), 1, Integer::sum);
            }
        }
        return openTaskCounts;
    }

    /**
     * 本网点 ACTIVE 师傅资质计数：index0=已通过（APPROVED/VALID/ACTIVE），index1=PENDING。
     */
    private Map<UUID, int[]> qualificationCountsForNetwork(String tenantId, UUID networkId) {
        Map<UUID, int[]> qualificationCounts = new LinkedHashMap<>();
        for (TechnicianQualificationView row : qualifications.listForActiveTechnicians(
                tenantId, networkId)) {
            int[] counts = qualificationCounts.computeIfAbsent(
                    row.technicianProfileId(), ignored -> new int[2]);
            if ("APPROVED".equals(row.status())
                    || "VALID".equals(row.status())
                    || "ACTIVE".equals(row.status())) {
                counts[0]++;
            } else if ("PENDING".equals(row.status())) {
                counts[1]++;
            }
        }
        return qualificationCounts;
    }

    private static String formatQualificationSummary(int approved, int pending) {
        if (approved > 0 && pending == 0) {
            return "已通过资质 " + approved + " 项";
        }
        if (approved > 0) {
            return "已通过 " + approved + " 项，待审 " + pending + " 项";
        }
        if (pending > 0) {
            return "仅有待审资质 " + pending + " 项";
        }
        return "无资质记录";
    }

    private static NetworkPortalTechnicianItem toTechnicianItem(
            NetworkPortalTechnicianView row,
            Map<String, Integer> openTaskCounts,
            Map<UUID, int[]> qualificationCounts
    ) {
        int openTasks = openTaskCounts.getOrDefault(row.technicianProfileId().toString(), 0);
        int[] quals = qualificationCounts.getOrDefault(row.technicianProfileId(), new int[2]);
        int approved = quals[0];
        int pending = quals[1];
        return new NetworkPortalTechnicianItem(
                row.membershipId(),
                row.technicianProfileId(),
                row.principalId(),
                row.displayName(),
                row.profileStatus(),
                row.membershipStatus(),
                row.validFrom(),
                row.validTo(),
                row.membershipVersion(),
                openTasks,
                approved,
                pending,
                formatQualificationSummary(approved, pending));
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
        Set<UUID> workOrderIds = new LinkedHashSet<>();
        for (NetworkPortalTaskItem item : items) {
            workOrderIds.add(item.workOrderId());
        }
        Map<UUID, WorkOrderDirectoryHeader> headers = loadWorkOrderHeaders(
                actor.tenantId(), workOrderIds);
        List<NetworkPortalTaskItem> taskItems = items.stream()
                .map(item -> withTaskHeader(item, headers.get(item.workOrderId())))
                .toList();
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
        List<NetworkPortalWorkspaceAppointmentSummary> appointmentSummaries = null;
        List<NetworkPortalWorkspaceContactAttemptSummary> contactAttemptSummaries = null;
        Set<UUID> pageTaskIds = new LinkedHashSet<>();
        for (NetworkPortalTaskItem item : taskItems) {
            pageTaskIds.add(item.taskId());
        }
        if (hasNetworkCapability(actor, correlationId, MANAGE_APPOINTMENT, networkId)) {
            appointmentSummaries = loadAppointmentSummaries(
                    actor, correlationId, networkId, pageTaskIds);
            contactAttemptSummaries = loadContactAttemptSummaries(
                    actor, correlationId, pageTaskIds);
        }
        List<NetworkPortalWorkspaceCorrectionCaseSummary> correctionSummaries = null;
        List<NetworkPortalWorkspaceEvidenceSlotSummary> evidenceSlotSummaries = null;
        List<NetworkPortalWorkspaceEvidenceItemSummary> evidenceItemSummaries = null;
        if (hasNetworkCapability(actor, correlationId, EVIDENCE_READ, networkId)) {
            correctionSummaries = loadCorrectionSummaries(actor, correlationId, pageTaskIds);
            evidenceSlotSummaries = loadEvidenceSlotSummaries(
                    actor, correlationId, networkId, pageTaskIds);
            evidenceItemSummaries = loadEvidenceItemSummaries(
                    actor, correlationId, networkId, pageTaskIds);
        }
        List<NetworkPortalDirectorySlaRiskSummary> slaRiskSummaries = null;
        if (hasNetworkCapability(actor, correlationId, SLA_READ, networkId)) {
            slaRiskSummaries = loadDirectorySlaRiskSummariesForTasks(
                    actor, correlationId, networkId, taskItems);
        }
        return new NetworkPortalPage<>(
                networkId, taskItems, clock.instant(),
                technicianSummaries, appointmentSummaries, contactAttemptSummaries,
                correctionSummaries, evidenceSlotSummaries, evidenceItemSummaries, slaRiskSummaries);
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalTechnicianItem> listTechnicians(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);
        Map<String, Integer> openTaskCounts = openTaskCountsForNetwork(actor.tenantId(), networkId);
        Map<UUID, int[]> qualificationCounts = qualificationCountsForNetwork(actor.tenantId(), networkId);
        List<NetworkPortalTechnicianItem> items = technicians.listActiveTechnicians(actor.tenantId(), networkId)
                .stream()
                .map(row -> toTechnicianItem(row, openTaskCounts, qualificationCounts))
                .toList();
        return new NetworkPortalPage<>(networkId, items, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalAssignCandidatePage listAssignCandidates(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            UUID taskId
    ) {
        Objects.requireNonNull(taskId, "taskId");
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);

        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        NetworkActiveAssignmentView taskAssignment = null;
        Map<String, Integer> openTaskCounts = openTaskCountsForNetwork(actor.tenantId(), networkId);
        for (NetworkActiveAssignmentView row : active) {
            if (taskId.equals(row.taskId())) {
                taskAssignment = row;
                break;
            }
        }
        if (taskAssignment == null) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "任务不在本网点 ACTIVE 责任范围内");
        }

        String businessType = null;
        for (NetworkPortalTaskItem task : listTasks(actor, correlationId, networkContextHeader).items()) {
            if (taskId.equals(task.taskId())) {
                businessType = task.businessType();
                break;
            }
        }

        Map<UUID, int[]> qualificationCounts = qualificationCountsForNetwork(actor.tenantId(), networkId);

        NetworkPortalCapacityItem capacityHint = null;
        for (NetworkPortalCapacityItem row : capacityItems(actor.tenantId(), networkId)) {
            if (businessType != null && businessType.equals(row.businessType())) {
                capacityHint = row;
                break;
            }
            if (capacityHint == null) {
                capacityHint = row;
            }
        }

        // 预约日程：对本网点 ACTIVE 任务 fan-in；按 technicianId 归属统计近期预约与窗口重叠。
        Map<UUID, String> taskTechnician = new LinkedHashMap<>();
        Set<UUID> scheduleTaskIds = new LinkedHashSet<>();
        scheduleTaskIds.add(taskId);
        for (NetworkActiveAssignmentView row : active) {
            scheduleTaskIds.add(row.taskId());
            if (row.technicianId() != null && !row.technicianId().isBlank()) {
                taskTechnician.put(row.taskId(), row.technicianId());
            }
        }
        List<TechnicianScheduleAppointmentView> scheduleRows =
                scheduleAppointments.listForTasks(actor.tenantId(), scheduleTaskIds);
        TechnicianScheduleAppointmentView currentTaskAppointment = null;
        Map<String, List<TechnicianScheduleAppointmentView>> appointmentsByTechnician = new LinkedHashMap<>();
        Instant now = clock.instant();
        for (TechnicianScheduleAppointmentView row : scheduleRows) {
            if (taskId.equals(row.taskId())) {
                currentTaskAppointment = row;
            }
            String technicianKey = taskTechnician.get(row.taskId());
            if (technicianKey == null) {
                continue;
            }
            if (row.windowEnd() != null && row.windowEnd().isBefore(now)) {
                continue;
            }
            appointmentsByTechnician.computeIfAbsent(technicianKey, ignored -> new ArrayList<>()).add(row);
        }

        // 距离：工单省市区 + 网点 Coverage 行政区亲和；再按师傅当前开放任务区域细化。
        WorkOrderDirectoryHeader targetHeader = workOrderHeaders
                .find(actor.tenantId(), taskAssignment.workOrderId())
                .orElse(null);
        Map<UUID, WorkOrderDirectoryHeader> openTaskHeadersByWorkOrder = new LinkedHashMap<>();
        Set<UUID> openWorkOrderIds = new LinkedHashSet<>();
        openWorkOrderIds.add(taskAssignment.workOrderId());
        for (NetworkActiveAssignmentView row : active) {
            openWorkOrderIds.add(row.workOrderId());
        }
        for (UUID workOrderId : openWorkOrderIds) {
            workOrderHeaders.find(actor.tenantId(), workOrderId)
                    .ifPresent(header -> openTaskHeadersByWorkOrder.put(workOrderId, header));
        }
        Set<String> regionCodesNeeded = new LinkedHashSet<>();
        for (WorkOrderDirectoryHeader header : openTaskHeadersByWorkOrder.values()) {
            if (header.provinceCode() != null && !header.provinceCode().isBlank()) {
                regionCodesNeeded.add(header.provinceCode().trim());
            }
            if (header.cityCode() != null && !header.cityCode().isBlank()) {
                regionCodesNeeded.add(header.cityCode().trim());
            }
            if (header.districtCode() != null && !header.districtCode().isBlank()) {
                regionCodesNeeded.add(header.districtCode().trim());
            }
        }
        Map<String, String> regionNameMap = regionNames.findNames(regionCodesNeeded);

        // Coverage 与 DISPATCH 地图一致：brandCode + serviceProductCode；勿用任务 businessType 猜测。
        String brandCode = targetHeader == null ? null : targetHeader.brandCode();
        String coverageProduct = targetHeader == null ? null : targetHeader.serviceProductCode();
        List<ServiceNetworkCoverageView> coverageRows = List.of();
        if (brandCode != null && !brandCode.isBlank()
                && coverageProduct != null && !coverageProduct.isBlank()) {
            coverageRows = coverages.listActiveCoverage(
                    actor.tenantId(),
                    List.of(networkId.toString()),
                    brandCode,
                    coverageProduct,
                    now);
        }

        Map<String, List<WorkOrderDirectoryHeader>> openHeadersByTechnician = new LinkedHashMap<>();
        for (NetworkActiveAssignmentView row : active) {
            if (row.technicianId() == null || row.technicianId().isBlank()) {
                continue;
            }
            WorkOrderDirectoryHeader header = openTaskHeadersByWorkOrder.get(row.workOrderId());
            if (header == null) {
                continue;
            }
            openHeadersByTechnician
                    .computeIfAbsent(row.technicianId(), ignored -> new ArrayList<>())
                    .add(header);
        }

        String workOrderRegionSummary = AssignCandidateDistanceEvaluator.formatWorkOrderRegion(
                targetHeader, regionNameMap);

        List<NetworkPortalAssignCandidateItem> items = new ArrayList<>();
        for (NetworkPortalTechnicianView tech : technicians.listActiveTechnicians(
                actor.tenantId(), networkId)) {
            int openTasks = openTaskCounts.getOrDefault(tech.technicianProfileId().toString(), 0);
            int[] quals = qualificationCounts.getOrDefault(tech.technicianProfileId(), new int[2]);
            int approved = quals[0];
            int pending = quals[1];
            List<String> warnings = new ArrayList<>();
            boolean assignable = "ACTIVE".equals(tech.membershipStatus())
                    && "ACTIVE".equals(tech.profileStatus());
            if (!assignable) {
                warnings.add("师傅关系或档案非 ACTIVE，不可分配");
            }
            String qualificationSummary = formatQualificationSummary(approved, pending);
            if (approved > 0 && pending > 0) {
                warnings.add("存在待审资质，请确认业务是否允许分配");
            } else if (approved == 0 && pending > 0) {
                warnings.add("尚无已通过资质");
            } else if (approved == 0) {
                warnings.add("无资质记录");
            }
            if (capacityHint != null && capacityHint.availableUnits() <= 0) {
                warnings.add("当前业务类型网点产能已满，提交可能被拒绝");
            }

            List<TechnicianScheduleAppointmentView> techAppointments =
                    appointmentsByTechnician.getOrDefault(tech.technicianProfileId().toString(), List.of());
            int upcomingAppointments = techAppointments.size();
            boolean scheduleOverlap = false;
            if (currentTaskAppointment != null
                    && currentTaskAppointment.windowStart() != null
                    && currentTaskAppointment.windowEnd() != null) {
                for (TechnicianScheduleAppointmentView existing : techAppointments) {
                    if (existing.taskId().equals(taskId)) {
                        continue;
                    }
                    if (existing.windowStart() == null || existing.windowEnd() == null) {
                        continue;
                    }
                    boolean overlaps = existing.windowStart().isBefore(currentTaskAppointment.windowEnd())
                            && currentTaskAppointment.windowStart().isBefore(existing.windowEnd());
                    if (overlaps) {
                        scheduleOverlap = true;
                        break;
                    }
                }
            }
            String scheduleConflictSummary;
            if (scheduleOverlap) {
                scheduleConflictSummary = "与现有预约窗口重叠";
                warnings.add("预约窗口可能冲突，请先协调改期");
            } else if (upcomingAppointments > 0) {
                scheduleConflictSummary = "另有 " + upcomingAppointments + " 个未完成预约";
                warnings.add("师傅另有未完成预约，请确认负载");
            } else {
                scheduleConflictSummary = "无近期预约";
            }

            AssignCandidateDistanceEvaluator.DistanceProjection distance =
                    AssignCandidateDistanceEvaluator.evaluate(
                            targetHeader,
                            coverageRows,
                            openHeadersByTechnician.getOrDefault(
                                    tech.technicianProfileId().toString(), List.of()),
                            regionNameMap);
            if (AssignCandidateDistanceEvaluator.TIER_OUTSIDE_COVERAGE.equals(distance.distanceTier())) {
                warnings.add("网点覆盖未命中工单行政区，请确认是否允许跨区派单");
            } else if (AssignCandidateDistanceEvaluator.TIER_UNKNOWN.equals(distance.distanceTier())) {
                warnings.add("服务区域未知，无法给出可靠距离亲和");
            }

            Integer capacityAvailable = capacityHint == null ? null : capacityHint.availableUnits();
            Integer capacityMax = capacityHint == null ? null : capacityHint.maxUnits();
            AssignCandidateRecommendationEvaluator.RecommendationProjection recommendation =
                    AssignCandidateRecommendationEvaluator.evaluate(
                            assignable,
                            approved,
                            pending,
                            openTasks,
                            scheduleOverlap,
                            upcomingAppointments,
                            distance.distanceTier(),
                            distance.coverageMatched(),
                            capacityAvailable);

            items.add(new NetworkPortalAssignCandidateItem(
                    tech.technicianProfileId(),
                    tech.displayName(),
                    tech.membershipStatus(),
                    tech.profileStatus(),
                    openTasks,
                    approved,
                    pending,
                    qualificationSummary,
                    upcomingAppointments,
                    scheduleConflictSummary,
                    scheduleOverlap,
                    distance.distanceTier(),
                    distance.distanceSummary(),
                    distance.coverageMatched(),
                    capacityAvailable,
                    capacityMax,
                    warnings,
                    assignable,
                    recommendation.recommendationTier(),
                    recommendation.recommendationSummary(),
                    recommendation.recommendationReasons()));
        }
        items.sort(Comparator
                .comparing(NetworkPortalAssignCandidateItem::assignable).reversed()
                .thenComparing(item -> AssignCandidateRecommendationEvaluator.tierRank(
                        item.recommendationTier()))
                .thenComparing(item -> AssignCandidateDistanceEvaluator.tierRank(item.distanceTier()))
                .thenComparing(NetworkPortalAssignCandidateItem::openTaskCount)
                .thenComparing(NetworkPortalAssignCandidateItem::displayName));
        String emptyReason = items.isEmpty()
                ? AssignCandidateRecommendationEvaluator.EMPTY_NO_TECHNICIANS
                : null;
        return new NetworkPortalAssignCandidatePage(
                networkId,
                taskId,
                businessType,
                workOrderRegionSummary,
                items,
                clock.instant(),
                AssignCandidateRecommendationEvaluator.RANKING_EXPLANATION,
                emptyReason);
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

        Integer todayAppointmentCount = null;
        List<NetworkPortalWorkbenchAppointmentItem> todayAppointments = null;
        Map<UUID, String> technicianNames = Map.of();
        if (hasNetworkCapability(actor, correlationId, MANAGE_APPOINTMENT, networkId)) {
            if (hasNetworkCapability(actor, correlationId, TECHNICIAN_READ_OWN, networkId)) {
                technicianNames = loadTechnicianDisplayNames(actor.tenantId(), networkId);
            }
            todayAppointments = loadWorkbenchTodayAppointments(
                    actor.tenantId(), active, asOf, technicianNames);
            todayAppointmentCount = todayAppointments.size();
        }

        List<NetworkPortalWorkbenchTimelineBucket> todayTimeline = buildWorkbenchTodayTimeline(
                unassigned, todayAppointments, openCorrectionCaseCount, slaSummary);

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
                slaSummary,
                todayAppointmentCount,
                todayAppointments,
                todayTimeline);
    }

    /**
     * M413：本网点预约日历。硬门禁 manageAppointment；运营日 Asia/Shanghai；默认今天起 14 天。
     */
    @Override
    @Transactional(readOnly = true)
    public NetworkPortalAppointmentCalendarView appointmentCalendar(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            LocalDate fromInclusive,
            LocalDate toInclusive
    ) {
        UUID networkId = requireAuthorizedNetwork(
                actor, correlationId, networkContextHeader, MANAGE_APPOINTMENT);
        Instant asOf = clock.instant();
        LocalDate today = asOf.atZone(NETWORK_OPERATIONAL_ZONE).toLocalDate();
        LocalDate rangeStart = fromInclusive == null ? today : fromInclusive;
        LocalDate rangeEnd = toInclusive == null
                ? rangeStart.plusDays(CALENDAR_DEFAULT_SPAN_DAYS - 1L)
                : toInclusive;
        if (rangeEnd.isBefore(rangeStart)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "预约日历结束日不得早于开始日");
        }
        long spanDays = rangeStart.datesUntil(rangeEnd.plusDays(1)).count();
        if (spanDays > CALENDAR_MAX_SPAN_DAYS) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "预约日历跨度不得超过 " + CALENDAR_MAX_SPAN_DAYS + " 天");
        }

        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        Map<UUID, String> technicianNames = Map.of();
        if (hasNetworkCapability(actor, correlationId, TECHNICIAN_READ_OWN, networkId)) {
            technicianNames = loadTechnicianDisplayNames(actor.tenantId(), networkId);
        }
        List<NetworkPortalWorkbenchAppointmentItem> flat = loadCalendarAppointments(
                actor.tenantId(), active, rangeStart, rangeEnd, technicianNames);
        int totalAppointmentCount = flat.size();
        boolean truncated = totalAppointmentCount > CALENDAR_APPOINTMENT_LIMIT;
        List<NetworkPortalWorkbenchAppointmentItem> visible = truncated
                ? List.copyOf(flat.subList(0, CALENDAR_APPOINTMENT_LIMIT))
                : flat;

        Map<LocalDate, List<NetworkPortalWorkbenchAppointmentItem>> byDay = new LinkedHashMap<>();
        for (LocalDate day = rangeStart; !day.isAfter(rangeEnd); day = day.plusDays(1)) {
            byDay.put(day, new ArrayList<>());
        }
        for (NetworkPortalWorkbenchAppointmentItem item : visible) {
            if (item.windowStart() == null) {
                continue;
            }
            LocalDate day = item.windowStart().atZone(NETWORK_OPERATIONAL_ZONE).toLocalDate();
            List<NetworkPortalWorkbenchAppointmentItem> bucket = byDay.get(day);
            if (bucket != null) {
                bucket.add(item);
            }
        }

        List<NetworkPortalAppointmentCalendarDay> days = new ArrayList<>();
        for (Map.Entry<LocalDate, List<NetworkPortalWorkbenchAppointmentItem>> entry : byDay.entrySet()) {
            List<NetworkPortalWorkbenchAppointmentItem> items = List.copyOf(entry.getValue());
            days.add(new NetworkPortalAppointmentCalendarDay(entry.getKey(), items.size(), items));
        }
        return new NetworkPortalAppointmentCalendarView(
                networkId,
                NETWORK_OPERATIONAL_ZONE.getId(),
                rangeStart,
                rangeEnd,
                totalAppointmentCount,
                truncated,
                days,
                asOf);
    }

    /**
     * M411：本网点 ACTIVE 任务上 PROPOSED/CONFIRMED 预约，按 Asia/Shanghai 运营日过滤窗口重叠。
     */
    private List<NetworkPortalWorkbenchAppointmentItem> loadWorkbenchTodayAppointments(
            String tenantId,
            List<NetworkActiveAssignmentView> active,
            Instant asOf,
            Map<UUID, String> technicianNames
    ) {
        LocalDate today = asOf.atZone(NETWORK_OPERATIONAL_ZONE).toLocalDate();
        List<NetworkPortalWorkbenchAppointmentItem> items = loadCalendarAppointments(
                tenantId, active, today, today, technicianNames);
        if (items.size() <= WORKBENCH_TODAY_APPOINTMENT_LIMIT) {
            return items;
        }
        return List.copyOf(items.subList(0, WORKBENCH_TODAY_APPOINTMENT_LIMIT));
    }

    /**
     * M413：按运营日闭区间过滤 PROPOSED/CONFIRMED 预约窗口；不含客户 PII。
     */
    private List<NetworkPortalWorkbenchAppointmentItem> loadCalendarAppointments(
            String tenantId,
            List<NetworkActiveAssignmentView> active,
            LocalDate rangeStart,
            LocalDate rangeEnd,
            Map<UUID, String> technicianNames
    ) {
        if (active.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> taskTechnician = new LinkedHashMap<>();
        Set<UUID> taskIds = new LinkedHashSet<>();
        for (NetworkActiveAssignmentView row : active) {
            taskIds.add(row.taskId());
            if (row.technicianId() != null && !row.technicianId().isBlank()) {
                taskTechnician.put(row.taskId(), row.technicianId());
            }
        }
        Instant rangeInstantStart = rangeStart.atStartOfDay(NETWORK_OPERATIONAL_ZONE).toInstant();
        Instant rangeInstantEnd = rangeEnd.plusDays(1).atStartOfDay(NETWORK_OPERATIONAL_ZONE).toInstant();
        List<NetworkPortalWorkbenchAppointmentItem> items = new ArrayList<>();
        for (TechnicianScheduleAppointmentView row : scheduleAppointments.listForTasks(tenantId, taskIds)) {
            if (row.windowStart() == null || row.windowEnd() == null) {
                continue;
            }
            boolean overlaps = row.windowStart().isBefore(rangeInstantEnd)
                    && row.windowEnd().isAfter(rangeInstantStart);
            if (!overlaps) {
                continue;
            }
            String technicianId = taskTechnician.get(row.taskId());
            String displayName = null;
            if (technicianId != null) {
                try {
                    displayName = technicianNames.get(UUID.fromString(technicianId));
                } catch (IllegalArgumentException ignored) {
                    displayName = null;
                }
            }
            items.add(new NetworkPortalWorkbenchAppointmentItem(
                    row.appointmentId(),
                    row.taskId(),
                    row.workOrderId(),
                    row.type(),
                    row.status(),
                    row.windowStart(),
                    row.windowEnd(),
                    row.timezone(),
                    technicianId,
                    displayName));
        }
        items.sort(Comparator
                .comparing(NetworkPortalWorkbenchAppointmentItem::windowStart,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(NetworkPortalWorkbenchAppointmentItem::appointmentId));
        return List.copyOf(items);
    }

    private Map<UUID, String> loadTechnicianDisplayNames(String tenantId, UUID networkId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (NetworkPortalTechnicianView tech : technicians.listActiveTechnicians(tenantId, networkId)) {
            names.put(tech.technicianProfileId(), tech.displayName());
        }
        return names;
    }

    private static List<NetworkPortalWorkbenchTimelineBucket> buildWorkbenchTodayTimeline(
            int unassigned,
            List<NetworkPortalWorkbenchAppointmentItem> todayAppointments,
            Integer openCorrectionCaseCount,
            NetworkPortalWorkOrderWorkspaceSlaSummary slaSummary
    ) {
        List<NetworkPortalWorkbenchTimelineBucket> buckets = new ArrayList<>();
        buckets.add(new NetworkPortalWorkbenchTimelineBucket(
                NetworkPortalWorkbenchTimelineBucket.UNASSIGNED,
                "待分配",
                unassigned,
                unassigned == 0 ? "暂无待指派师傅任务" : "待指派师傅任务 " + unassigned + " 个"));

        if (todayAppointments != null) {
            int am = 0;
            int pm = 0;
            int evening = 0;
            for (NetworkPortalWorkbenchAppointmentItem item : todayAppointments) {
                if (item.windowStart() == null) {
                    continue;
                }
                int hour = item.windowStart().atZone(NETWORK_OPERATIONAL_ZONE).getHour();
                if (hour < 12) {
                    am++;
                } else if (hour < 18) {
                    pm++;
                } else {
                    evening++;
                }
            }
            buckets.add(new NetworkPortalWorkbenchTimelineBucket(
                    NetworkPortalWorkbenchTimelineBucket.AM_APPOINTMENTS,
                    "上午预约",
                    am,
                    am == 0 ? "上午无预约窗口" : "上午预约 " + am + " 个"));
            buckets.add(new NetworkPortalWorkbenchTimelineBucket(
                    NetworkPortalWorkbenchTimelineBucket.PM_APPOINTMENTS,
                    "下午预约",
                    pm,
                    pm == 0 ? "下午无预约窗口" : "下午预约 " + pm + " 个"));
            buckets.add(new NetworkPortalWorkbenchTimelineBucket(
                    NetworkPortalWorkbenchTimelineBucket.EVENING_APPOINTMENTS,
                    "晚间预约",
                    evening,
                    evening == 0 ? "晚间无预约窗口" : "晚间预约 " + evening + " 个"));
        }

        if (openCorrectionCaseCount != null) {
            buckets.add(new NetworkPortalWorkbenchTimelineBucket(
                    NetworkPortalWorkbenchTimelineBucket.OPEN_CORRECTIONS,
                    "资料整改",
                    openCorrectionCaseCount,
                    openCorrectionCaseCount == 0
                            ? "无开放整改"
                            : "开放整改 " + openCorrectionCaseCount + " 件"));
        }
        if (slaSummary != null) {
            int atRisk = slaSummary.openCount();
            buckets.add(new NetworkPortalWorkbenchTimelineBucket(
                    NetworkPortalWorkbenchTimelineBucket.SLA_AT_RISK,
                    "SLA 风险",
                    atRisk,
                    atRisk == 0
                            ? "无运行中/已超时 SLA"
                            : "SLA 风险 " + atRisk + " 项（已超时 "
                                    + slaSummary.breachedCount() + "）"));
        }
        return List.copyOf(buckets);
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
     * M234：NETWORK sla.read 已 soft-gate；工单目录按 workOrderId 聚合本页 taskIds 计数。
     * 仅返回 openCount&gt;0 的行（无风险时 UI 显示「暂无」）。
     */
    private List<NetworkPortalDirectorySlaRiskSummary> loadDirectorySlaRiskSummariesForWorkOrders(
            CurrentPrincipal actor,
            String correlationId,
            UUID networkId,
            List<NetworkPortalWorkOrderItem> workOrderItems
    ) {
        List<NetworkPortalDirectorySlaRiskSummary> collected = new ArrayList<>();
        for (NetworkPortalWorkOrderItem item : workOrderItems) {
            Set<UUID> taskIds = Set.copyOf(item.taskIds());
            if (taskIds.isEmpty()) {
                continue;
            }
            UUID projectId = null;
            for (UUID taskId : taskIds) {
                TaskFulfillmentContext task = tasks.find(actor.tenantId(), taskId).orElse(null);
                if (task != null && task.projectId() != null) {
                    projectId = task.projectId();
                    break;
                }
            }
            if (projectId == null) {
                continue;
            }
            NetworkPortalWorkOrderWorkspaceSlaSummary counts = loadSlaSummary(
                    actor, correlationId, item.workOrderId(), projectId, networkId, taskIds);
            if (counts.openCount() > 0) {
                collected.add(new NetworkPortalDirectorySlaRiskSummary(
                        item.workOrderId(), null, counts.openCount(), counts.breachedCount()));
            }
        }
        return List.copyOf(collected);
    }

    /**
     * M234：NETWORK sla.read 已 soft-gate；任务目录按 taskId 展开本页计数。
     * 仅返回 openCount&gt;0 的行。
     */
    private List<NetworkPortalDirectorySlaRiskSummary> loadDirectorySlaRiskSummariesForTasks(
            CurrentPrincipal actor,
            String correlationId,
            UUID networkId,
            List<NetworkPortalTaskItem> taskItems
    ) {
        Map<UUID, UUID> workOrderProjects = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> workOrderTaskIds = new LinkedHashMap<>();
        for (NetworkPortalTaskItem item : taskItems) {
            if (item.workOrderId() == null || item.taskId() == null) {
                continue;
            }
            workOrderTaskIds.computeIfAbsent(item.workOrderId(), ignored -> new LinkedHashSet<>())
                    .add(item.taskId());
            if (item.projectId() != null) {
                workOrderProjects.putIfAbsent(item.workOrderId(), item.projectId());
            } else {
                TaskFulfillmentContext task = tasks.find(actor.tenantId(), item.taskId()).orElse(null);
                if (task != null && task.projectId() != null) {
                    workOrderProjects.putIfAbsent(item.workOrderId(), task.projectId());
                }
            }
        }
        Map<UUID, int[]> perTask = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<UUID>> entry : workOrderTaskIds.entrySet()) {
            UUID workOrderId = entry.getKey();
            UUID projectId = workOrderProjects.get(workOrderId);
            if (projectId == null) {
                continue;
            }
            Set<UUID> taskIds = entry.getValue();
            List<SlaInstanceItem> items = slaQueries.listForWorkOrderOnNetwork(
                    actor, correlationId, workOrderId, projectId, networkId, null, SLA_WORKSPACE_LIMIT)
                    .items();
            for (SlaInstanceItem item : items) {
                if (item.taskId() == null || !taskIds.contains(item.taskId())) {
                    continue;
                }
                int[] counts = perTask.computeIfAbsent(item.taskId(), ignored -> new int[2]);
                if (OPEN_SLA_STATUSES.contains(item.status())) {
                    counts[0]++;
                }
                if ("BREACHED".equals(item.status())) {
                    counts[1]++;
                }
            }
        }
        List<NetworkPortalDirectorySlaRiskSummary> collected = new ArrayList<>();
        for (NetworkPortalTaskItem item : taskItems) {
            int[] counts = perTask.get(item.taskId());
            if (counts == null || counts[0] <= 0) {
                continue;
            }
            collected.add(new NetworkPortalDirectorySlaRiskSummary(
                    item.workOrderId(), item.taskId(), counts[0], counts[1]));
        }
        return List.copyOf(collected);
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
