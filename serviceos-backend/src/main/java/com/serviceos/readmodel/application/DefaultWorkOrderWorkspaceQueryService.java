package com.serviceos.readmodel.application;

import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentView;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.ContactAttemptView;
import com.serviceos.dispatch.api.ServiceAssignmentQueryService;
import com.serviceos.dispatch.api.ServiceAssignmentSummary;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceItemQueryService;
import com.serviceos.evidence.api.EvidenceItemSummaryView;
import com.serviceos.evidence.api.ReviewCaseService;
import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;
import com.serviceos.fieldwork.api.VisitService;
import com.serviceos.fieldwork.api.VisitView;
import com.serviceos.forms.api.TaskFormDefinition;
import com.serviceos.forms.api.TaskFormQueryService;
import com.serviceos.forms.api.FormSubmissionQueryService;
import com.serviceos.forms.api.FormSubmissionSummaryView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.integration.api.DeliveryAttemptView;
import com.serviceos.integration.api.DeliveryReplayRequestView;
import com.serviceos.integration.api.ExternalAcknowledgementView;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.api.InboundMessageQueryService;
import com.serviceos.integration.api.OutboundDeliveryService;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.api.OperationalExceptionQuery;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.readmodel.api.WorkOrderTimelineQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceExceptionSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceMeta;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceServiceAssignmentSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceSlaSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceTaskSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceAppointmentSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceAppointmentsVisitsSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceCorrectionCaseSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceCorrectionResubmissionSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceContactAttemptSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceEvidenceSlotSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceFormSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceFormSubmissionSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceFormsEvidenceSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceEvidenceItemSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceDeliveryAttemptSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceDeliveryReplaySummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceExternalAcknowledgementSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceInboundEnvelopeSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceIntegrationSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceOutboundDeliverySummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceReviewCaseSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceReviewDecisionSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceReviewsCorrectionsSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceTasksSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceTimelineSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceVisitSummary;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.sla.api.SlaInstanceItem;
import com.serviceos.sla.api.SlaQueryService;
import com.serviceos.task.api.WorkOrderTaskPage;
import com.serviceos.task.api.WorkOrderTaskQueryService;
import com.serviceos.task.api.WorkOrderTaskSummary;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderMaskedContactView;
import com.serviceos.workorder.api.WorkOrderQueryService;
import com.serviceos.workflow.api.StageInstanceView;
import com.serviceos.workflow.api.WorkflowExecutionProjection;
import com.serviceos.workflow.api.WorkflowExecutionQueryService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 实时组合工单工作区顶层快照与按需区块。
 * <p>不落工作区投影表；SLA/异常/预约到访/表单资料缺权时标记 UNAVAILABLE，不把整个工作区打成 403。</p>
 * <p>故意不加外层事务：次级只读端口缺权会抛 {@code BusinessProblem}，若加入同一事务会
 * 把事务标成 rollback-only，即使外层捕获也会在提交时失败。</p>
 */
@Service
final class DefaultWorkOrderWorkspaceQueryService implements WorkOrderWorkspaceQueryService {
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "READY", "PENDING", "CLAIMED", "RUNNING", "RETRY_WAIT", "MANUAL_INTERVENTION");
    private static final Set<String> OPEN_SLA_STATUSES = Set.of("RUNNING", "BREACHED");
    private static final Set<String> ACCEPTED_SECTIONS = Set.of(
            "TASKS", "TIMELINE_AUDIT", "APPOINTMENTS_VISITS", "FORMS_EVIDENCE",
            "REVIEWS_CORRECTIONS", "INTEGRATION");

    private final WorkOrderQueryService workOrders;
    private final WorkOrderTaskQueryService workOrderTasks;
    private final WorkflowExecutionQueryService workflowExecutions;
    private final WorkOrderTimelineQueryService timelines;
    private final WorkOrderTimelineProjectionRuntime projectionRuntime;
    private final SlaQueryService slaQueries;
    private final OperationalExceptionWorkbenchService exceptions;
    private final VisitService visits;
    private final AppointmentService appointments;
    private final TaskFormQueryService taskForms;
    private final FormSubmissionQueryService formSubmissions;
    private final EvidenceSlotQueryService evidenceSlots;
    private final EvidenceItemQueryService evidenceItems;
    private final ReviewCaseService reviews;
    private final CorrectionCaseService corrections;
    private final InboundMessageQueryService inboundMessages;
    private final OutboundDeliveryService outboundDeliveries;
    private final ServiceAssignmentQueryService serviceAssignments;
    private final Clock clock;

    DefaultWorkOrderWorkspaceQueryService(
            WorkOrderQueryService workOrders,
            WorkOrderTaskQueryService workOrderTasks,
            WorkflowExecutionQueryService workflowExecutions,
            WorkOrderTimelineQueryService timelines,
            WorkOrderTimelineProjectionRuntime projectionRuntime,
            SlaQueryService slaQueries,
            OperationalExceptionWorkbenchService exceptions,
            VisitService visits,
            AppointmentService appointments,
            TaskFormQueryService taskForms,
            FormSubmissionQueryService formSubmissions,
            EvidenceSlotQueryService evidenceSlots,
            EvidenceItemQueryService evidenceItems,
            ReviewCaseService reviews,
            CorrectionCaseService corrections,
            InboundMessageQueryService inboundMessages,
            OutboundDeliveryService outboundDeliveries,
            ServiceAssignmentQueryService serviceAssignments,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.workOrderTasks = workOrderTasks;
        this.workflowExecutions = workflowExecutions;
        this.timelines = timelines;
        this.projectionRuntime = projectionRuntime;
        this.slaQueries = slaQueries;
        this.exceptions = exceptions;
        this.visits = visits;
        this.appointments = appointments;
        this.taskForms = taskForms;
        this.formSubmissions = formSubmissions;
        this.evidenceSlots = evidenceSlots;
        this.evidenceItems = evidenceItems;
        this.reviews = reviews;
        this.corrections = corrections;
        this.inboundMessages = inboundMessages;
        this.outboundDeliveries = outboundDeliveries;
        this.serviceAssignments = serviceAssignments;
        this.clock = clock;
    }

    @Override
    public WorkOrderWorkspace get(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId
    ) {
        WorkOrderDetail detail = workOrders.get(principal, correlationId, workOrderId);
        // M423：与终审工作区同源脱敏端口；同 workOrder.read，不返回原文 PII。
        WorkOrderMaskedContactView contact = workOrders.getMaskedContact(
                principal, correlationId, workOrderId);
        List<WorkOrderTaskSummary> tasks = workOrderTasks.list(
                principal, correlationId, workOrderId, null, 100).items();
        WorkOrderTaskSummary current = tasks.stream()
                .filter(task -> ACTIVE_TASK_STATUSES.contains(task.status()))
                .findFirst()
                .orElse(null);
        WorkflowExecutionProjection workflow =
                workflowExecutions.get(principal, correlationId, workOrderId);

        var timelinePage = timelines.list(principal, correlationId, workOrderId, null, 1);
        String timelineFreshness = timelinePage.freshnessStatus();

        Map<String, String> availability = new LinkedHashMap<>();
        availability.put("TASKS", tasks.isEmpty() ? "EMPTY" : "AVAILABLE");
        availability.put("TIMELINE_AUDIT",
                timelinePage.lastProjectedAt() == null ? "EMPTY" : "AVAILABLE");
        availability.put("CUSTOMER_LOCATION", "UNAVAILABLE");
        availability.put(
                "APPOINTMENTS_VISITS",
                probeAppointmentsVisitsAvailability(principal, correlationId, workOrderId, tasks));
        availability.put(
                "FORMS_EVIDENCE",
                probeFormsEvidenceAvailability(principal, correlationId, tasks));
        availability.put(
                "REVIEWS_CORRECTIONS",
                probeReviewsCorrectionsAvailability(principal, correlationId, tasks));
        availability.put(
                "FINAL_REVIEW",
                probeFinalReviewAvailability(principal, correlationId, tasks));
        availability.put(
                "INTEGRATION",
                probeIntegrationAvailability(principal, correlationId, workOrderId));
        availability.put("FACTS_CALCULATIONS", "UNAVAILABLE");
        WorkOrderWorkspaceServiceAssignmentSummary serviceAssignmentSummary =
                loadServiceAssignmentSummary(principal, correlationId, current, availability);

        WorkOrderWorkspaceSlaSummary slaSummary = loadSlaSummary(
                principal, correlationId, workOrderId, availability);
        WorkOrderWorkspaceExceptionSummary exceptionSummary = loadExceptionSummary(
                principal, correlationId, workOrderId, availability);

        return new WorkOrderWorkspace(
                detail.workOrder(),
                current == null ? null : toTaskSummary(current),
                workflow.stages().stream()
                        .map(DefaultWorkOrderWorkspaceQueryService::toStageSummary)
                        .toList(),
                availability,
                current == null ? null : "/api/v1/tasks/" + current.id() + "/allowed-actions",
                serviceAssignmentSummary,
                slaSummary,
                exceptionSummary,
                workOrders.getProjectPersonnel(principal, correlationId, workOrderId),
                timelineFreshness,
                new WorkOrderWorkspaceSourceVersions(detail.workOrder().version()),
                meta(timelineFreshness),
                contact.maskedCustomerName(),
                contact.maskedCustomerPhone(),
                contact.maskedServiceAddress());
    }

    private static WorkOrderWorkspace.WorkOrderWorkspaceStageSummary toStageSummary(
            StageInstanceView stage
    ) {
        return new WorkOrderWorkspace.WorkOrderWorkspaceStageSummary(
                stage.stageCode(),
                stage.sequenceNo(),
                stage.status(),
                stage.activatedAt(),
                stage.completedAt());
    }

    @Override
    public WorkOrderWorkspaceSection getSection(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            String section,
            String cursor,
            int limit
    ) {
        String normalized = normalizeSection(section);
        if (limit < 1 || limit > 100) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "limit must be between 1 and 100");
        }
        // 先鉴权工单，再装载区块，避免未授权主体探测 section 实现边界。
        WorkOrderDetail detail = workOrders.get(principal, correlationId, workOrderId);
        WorkOrderWorkspaceSourceVersions versions =
                new WorkOrderWorkspaceSourceVersions(detail.workOrder().version());
        return switch (normalized) {
            case "TASKS" -> {
                WorkOrderTaskPage page = workOrderTasks.list(
                        principal, correlationId, workOrderId, cursor, limit);
                yield new WorkOrderWorkspaceSection(
                        normalized,
                        versions,
                        meta("FRESH"),
                        new WorkOrderWorkspaceTasksSectionData(
                                page.items().stream().map(this::toTaskSummary).toList(),
                                page.nextCursor()),
                        null,
                        null,
                        null,
                        null,
                        null);
            }
            case "TIMELINE_AUDIT" -> {
                var page = timelines.list(principal, correlationId, workOrderId, cursor, limit);
                yield new WorkOrderWorkspaceSection(
                        normalized,
                        versions,
                        meta(page.freshnessStatus()),
                        null,
                        new WorkOrderWorkspaceTimelineSectionData(
                                page.items(),
                                page.nextCursor(),
                                page.lastProjectedAt(),
                                page.freshnessStatus()),
                        null,
                        null,
                        null,
                        null);
            }
            case "APPOINTMENTS_VISITS" -> {
                if (cursor != null && !cursor.isBlank()) {
                    throw new BusinessProblem(
                            ProblemCode.VALIDATION_FAILED,
                            "APPOINTMENTS_VISITS cursor paging is not accepted in this slice");
                }
                WorkOrderWorkspaceAppointmentsVisitsSectionData payload =
                        loadAppointmentsVisits(principal, correlationId, workOrderId, limit);
                yield new WorkOrderWorkspaceSection(
                        normalized, versions, meta("FRESH"), null, null, payload, null, null, null);
            }
            case "FORMS_EVIDENCE" -> {
                if (cursor != null && !cursor.isBlank()) {
                    throw new BusinessProblem(
                            ProblemCode.VALIDATION_FAILED,
                            "FORMS_EVIDENCE cursor paging is not accepted in this slice");
                }
                WorkOrderWorkspaceFormsEvidenceSectionData payload =
                        loadFormsEvidence(principal, correlationId, workOrderId, limit);
                yield new WorkOrderWorkspaceSection(
                        normalized, versions, meta("FRESH"), null, null, null, payload, null, null);
            }
            case "REVIEWS_CORRECTIONS" -> {
                if (cursor != null && !cursor.isBlank()) {
                    throw new BusinessProblem(
                            ProblemCode.VALIDATION_FAILED,
                            "REVIEWS_CORRECTIONS cursor paging is not accepted in this slice");
                }
                WorkOrderWorkspaceReviewsCorrectionsSectionData payload =
                        loadReviewsCorrections(principal, correlationId, workOrderId, limit);
                yield new WorkOrderWorkspaceSection(
                        normalized, versions, meta("FRESH"), null, null, null, null, payload, null);
            }
            case "INTEGRATION" -> {
                if (cursor != null && !cursor.isBlank()) {
                    throw new BusinessProblem(
                            ProblemCode.VALIDATION_FAILED,
                            "INTEGRATION cursor paging is not accepted in this slice");
                }
                WorkOrderWorkspaceIntegrationSectionData payload =
                        loadIntegration(principal, correlationId, workOrderId, limit);
                yield new WorkOrderWorkspaceSection(
                        normalized, versions, meta("FRESH"), null, null, null, null, null, payload);
            }
            default -> throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED, "workspace section is not accepted: " + normalized);
        };
    }

    private String probeAppointmentsVisitsAvailability(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            List<WorkOrderTaskSummary> tasks
    ) {
        WorkOrderWorkspaceAppointmentsVisitsSectionData loaded =
                loadAppointmentsVisits(principal, correlationId, workOrderId, tasks, 1);
        boolean visitDenied = loaded.visits() == null;
        boolean appointmentDenied = loaded.appointments() == null;
        boolean contactDenied = loaded.contactAttempts() == null;
        boolean anyData = (!visitDenied && !loaded.visits().isEmpty())
                || (!appointmentDenied && !loaded.appointments().isEmpty())
                || (!contactDenied && !loaded.contactAttempts().isEmpty());
        if (anyData) {
            return "AVAILABLE";
        }
        return visitDenied || appointmentDenied || contactDenied ? "UNAVAILABLE" : "EMPTY";
    }

    private String probeFormsEvidenceAvailability(
            CurrentPrincipal principal,
            String correlationId,
            List<WorkOrderTaskSummary> tasks
    ) {
        WorkOrderWorkspaceFormsEvidenceSectionData loaded =
                loadFormsEvidence(principal, correlationId, tasks, 1);
        boolean formsDenied = loaded.forms() == null || loaded.formSubmissions() == null;
        boolean evidenceDenied = loaded.evidenceSlots() == null || loaded.evidenceItems() == null;
        boolean anyData = (loaded.forms() != null && !loaded.forms().isEmpty())
                || (loaded.formSubmissions() != null && !loaded.formSubmissions().isEmpty())
                || (loaded.evidenceSlots() != null && !loaded.evidenceSlots().isEmpty())
                || (loaded.evidenceItems() != null && !loaded.evidenceItems().isEmpty());
        if (anyData) {
            return "AVAILABLE";
        }
        return formsDenied || evidenceDenied ? "UNAVAILABLE" : "EMPTY";
    }

    private String probeReviewsCorrectionsAvailability(
            CurrentPrincipal principal,
            String correlationId,
            List<WorkOrderTaskSummary> tasks
    ) {
        WorkOrderWorkspaceReviewsCorrectionsSectionData loaded =
                loadReviewsCorrections(principal, correlationId, tasks, 1);
        boolean reviewsDenied = loaded.reviews() == null;
        boolean correctionsDenied = loaded.corrections() == null;
        return dualHalfAvailability(
                reviewsDenied,
                correctionsDenied,
                reviewsDenied || loaded.reviews().isEmpty(),
                correctionsDenied || loaded.corrections().isEmpty());
    }

    /** M351：有 OPEN INTERNAL ReviewCase 为 AVAILABLE；有权但无案例为 EMPTY；完全无 evidence.read 为 UNAVAILABLE。 */
    private String probeFinalReviewAvailability(
            CurrentPrincipal principal,
            String correlationId,
            List<WorkOrderTaskSummary> tasks
    ) {
        boolean denied = false;
        boolean any = false;
        boolean openInternal = false;
        for (WorkOrderTaskSummary task : tasks) {
            try {
                for (var review : reviews.listForTask(principal, correlationId, task.id())) {
                    any = true;
                    if ("INTERNAL".equals(review.origin()) && "OPEN".equals(review.status())) {
                        openInternal = true;
                    }
                }
            } catch (BusinessProblem problem) {
                if (problem.code() == ProblemCode.ACCESS_DENIED) {
                    denied = true;
                    continue;
                }
                throw problem;
            }
        }
        if (openInternal) {
            return "AVAILABLE";
        }
        if (any) {
            return "EMPTY";
        }
        return denied ? "UNAVAILABLE" : "EMPTY";
    }

    private String probeIntegrationAvailability(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId
    ) {
        WorkOrderWorkspaceIntegrationSectionData loaded =
                loadIntegration(principal, correlationId, workOrderId, 1);
        boolean inboundDenied = loaded.inboundEnvelopes() == null;
        boolean outboundDenied = loaded.outboundDeliveries() == null;
        return dualHalfAvailability(
                inboundDenied,
                outboundDenied,
                inboundDenied || loaded.inboundEnvelopes().isEmpty(),
                outboundDenied || loaded.outboundDeliveries().isEmpty());
    }

    private static String dualHalfAvailability(
            boolean leftDenied,
            boolean rightDenied,
            boolean leftEmpty,
            boolean rightEmpty
    ) {
        if (leftDenied && rightDenied) {
            return "UNAVAILABLE";
        }
        if (leftDenied && rightEmpty) {
            return "UNAVAILABLE";
        }
        if (rightDenied && leftEmpty) {
            return "UNAVAILABLE";
        }
        if (leftEmpty && rightEmpty) {
            return "EMPTY";
        }
        return "AVAILABLE";
    }

    private WorkOrderWorkspaceAppointmentsVisitsSectionData loadAppointmentsVisits(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            int limit
    ) {
        List<WorkOrderTaskSummary> tasks = workOrderTasks.list(
                principal, correlationId, workOrderId, null, 100).items();
        return loadAppointmentsVisits(principal, correlationId, workOrderId, tasks, limit);
    }

    private WorkOrderWorkspaceAppointmentsVisitsSectionData loadAppointmentsVisits(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            List<WorkOrderTaskSummary> tasks,
            int limit
    ) {
        List<WorkOrderWorkspaceVisitSummary> visitSummaries;
        try {
            visitSummaries = visits.listByWorkOrder(principal, correlationId, workOrderId).stream()
                    .sorted(Comparator.comparingInt(VisitView::visitSequence)
                            .thenComparing(VisitView::visitId))
                    .limit(limit)
                    .map(this::toVisitSummary)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                visitSummaries = null;
            } else {
                throw problem;
            }
        }

        List<WorkOrderWorkspaceAppointmentSummary> appointmentSummaries;
        try {
            List<WorkOrderWorkspaceAppointmentSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                for (AppointmentView appointment : appointments.listByTask(
                        principal, correlationId, task.id())) {
                    collected.add(toAppointmentSummary(appointment));
                }
            }
            appointmentSummaries = collected.stream()
                    .sorted(Comparator
                            .comparing(WorkOrderWorkspaceAppointmentSummary::createdAt)
                            .thenComparing(WorkOrderWorkspaceAppointmentSummary::appointmentId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                appointmentSummaries = null;
            } else {
                throw problem;
            }
        }

        List<WorkOrderWorkspaceContactAttemptSummary> contactAttemptSummaries;
        try {
            List<WorkOrderWorkspaceContactAttemptSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                appointments.listContactAttempts(principal, correlationId, task.id()).stream()
                        .map(this::toContactAttemptSummary)
                        .forEach(collected::add);
            }
            contactAttemptSummaries = collected.stream()
                    .sorted(Comparator.comparing(
                                    WorkOrderWorkspaceContactAttemptSummary::startedAt,
                                    Comparator.reverseOrder())
                            .thenComparing(WorkOrderWorkspaceContactAttemptSummary::contactAttemptId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                contactAttemptSummaries = null;
            } else {
                throw problem;
            }
        }

        return new WorkOrderWorkspaceAppointmentsVisitsSectionData(
                visitSummaries, appointmentSummaries, contactAttemptSummaries, null);
    }

    private WorkOrderWorkspaceFormsEvidenceSectionData loadFormsEvidence(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            int limit
    ) {
        List<WorkOrderTaskSummary> tasks = workOrderTasks.list(
                principal, correlationId, workOrderId, null, 100).items();
        return loadFormsEvidence(principal, correlationId, tasks, limit);
    }

    private WorkOrderWorkspaceFormsEvidenceSectionData loadFormsEvidence(
            CurrentPrincipal principal,
            String correlationId,
            List<WorkOrderTaskSummary> tasks,
            int limit
    ) {
        List<WorkOrderWorkspaceFormSummary> formSummaries;
        try {
            List<WorkOrderWorkspaceFormSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                for (TaskFormDefinition form : taskForms.listForTask(
                        principal, correlationId, task.id())) {
                    collected.add(toFormSummary(form));
                }
            }
            formSummaries = collected.stream()
                    .sorted(Comparator
                            .comparing(WorkOrderWorkspaceFormSummary::formKey)
                            .thenComparing(WorkOrderWorkspaceFormSummary::taskId)
                            .thenComparing(WorkOrderWorkspaceFormSummary::formVersionId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                formSummaries = null;
            } else {
                throw problem;
            }
        }

        List<WorkOrderWorkspaceFormSubmissionSummary> submissionSummaries;
        try {
            List<WorkOrderWorkspaceFormSubmissionSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                formSubmissions.listForTask(principal, correlationId, task.id()).stream()
                        .map(this::toFormSubmissionSummary)
                        .forEach(collected::add);
            }
            submissionSummaries = collected.stream()
                    .sorted(Comparator.comparing(
                                    WorkOrderWorkspaceFormSubmissionSummary::submittedAt)
                            .thenComparing(WorkOrderWorkspaceFormSubmissionSummary::submissionId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                submissionSummaries = null;
            } else {
                throw problem;
            }
        }

        List<WorkOrderWorkspaceEvidenceSlotSummary> slotSummaries;
        try {
            List<WorkOrderWorkspaceEvidenceSlotSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                try {
                    for (EvidenceSlotView slot : evidenceSlots.listForTask(
                            principal, correlationId, task.id())) {
                        collected.add(toEvidenceSlotSummary(slot));
                    }
                } catch (BusinessProblem problem) {
                    // 未完成可靠解析不能伪装成“无需资料”，但也不能把整个工作区顶层打成冲突。
                    if (problem.code() == ProblemCode.TASK_STATE_CONFLICT) {
                        continue;
                    }
                    throw problem;
                }
            }
            slotSummaries = collected.stream()
                    .sorted(Comparator
                            .comparing(WorkOrderWorkspaceEvidenceSlotSummary::templateKey)
                            .thenComparing(WorkOrderWorkspaceEvidenceSlotSummary::requirementCode)
                            .thenComparing(WorkOrderWorkspaceEvidenceSlotSummary::slotId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                slotSummaries = null;
            } else {
                throw problem;
            }
        }

        List<WorkOrderWorkspaceEvidenceItemSummary> itemSummaries;
        try {
            List<WorkOrderWorkspaceEvidenceItemSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                try {
                    evidenceItems.listSummariesForTask(principal, correlationId, task.id()).stream()
                            .map(this::toEvidenceItemSummary)
                            .forEach(collected::add);
                } catch (BusinessProblem problem) {
                    if (problem.code() == ProblemCode.TASK_STATE_CONFLICT) {
                        continue;
                    }
                    throw problem;
                }
            }
            itemSummaries = collected.stream()
                    .sorted(Comparator
                            .comparing(WorkOrderWorkspaceEvidenceItemSummary::evidenceSlotId)
                            .thenComparingInt(WorkOrderWorkspaceEvidenceItemSummary::itemOrdinal)
                            .thenComparing(WorkOrderWorkspaceEvidenceItemSummary::evidenceItemId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                itemSummaries = null;
            } else {
                throw problem;
            }
        }

        return new WorkOrderWorkspaceFormsEvidenceSectionData(
                formSummaries, submissionSummaries, slotSummaries, itemSummaries, null);
    }

    private WorkOrderWorkspaceReviewsCorrectionsSectionData loadReviewsCorrections(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            int limit
    ) {
        List<WorkOrderTaskSummary> tasks = workOrderTasks.list(
                principal, correlationId, workOrderId, null, 100).items();
        return loadReviewsCorrections(principal, correlationId, tasks, limit);
    }

    private WorkOrderWorkspaceReviewsCorrectionsSectionData loadReviewsCorrections(
            CurrentPrincipal principal,
            String correlationId,
            List<WorkOrderTaskSummary> tasks,
            int limit
    ) {
        List<WorkOrderWorkspaceReviewCaseSummary> reviewSummaries;
        try {
            List<WorkOrderWorkspaceReviewCaseSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                reviews.listForTask(principal, correlationId, task.id()).stream()
                        .map(this::toReviewCaseSummary)
                        .forEach(collected::add);
            }
            reviewSummaries = collected.stream()
                    .sorted(Comparator.comparing(WorkOrderWorkspaceReviewCaseSummary::createdAt)
                            .thenComparing(WorkOrderWorkspaceReviewCaseSummary::reviewCaseId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                reviewSummaries = null;
            } else {
                throw problem;
            }
        }

        List<WorkOrderWorkspaceCorrectionCaseSummary> correctionSummaries;
        try {
            List<WorkOrderWorkspaceCorrectionCaseSummary> collected = new ArrayList<>();
            for (WorkOrderTaskSummary task : tasks) {
                corrections.listForTask(principal, correlationId, task.id()).stream()
                        .map(this::toCorrectionCaseSummary)
                        .forEach(collected::add);
            }
            correctionSummaries = collected.stream()
                    .sorted(Comparator.comparing(WorkOrderWorkspaceCorrectionCaseSummary::createdAt)
                            .thenComparing(WorkOrderWorkspaceCorrectionCaseSummary::correctionCaseId))
                    .limit(limit)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                correctionSummaries = null;
            } else {
                throw problem;
            }
        }
        return new WorkOrderWorkspaceReviewsCorrectionsSectionData(
                reviewSummaries, correctionSummaries, null);
    }

    private WorkOrderWorkspaceIntegrationSectionData loadIntegration(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            int limit
    ) {
        List<WorkOrderWorkspaceInboundEnvelopeSummary> inboundSummaries;
        try {
            inboundSummaries = inboundMessages.listForWorkOrder(
                            principal, correlationId, workOrderId, limit)
                    .stream()
                    .map(this::toInboundEnvelopeSummary)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                inboundSummaries = null;
            } else {
                throw problem;
            }
        }

        List<WorkOrderWorkspaceOutboundDeliverySummary> outboundSummaries;
        try {
            outboundSummaries = outboundDeliveries.listForWorkOrder(
                            principal, correlationId, workOrderId, limit)
                    .stream()
                    .map(this::toOutboundDeliverySummary)
                    .toList();
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                outboundSummaries = null;
            } else {
                throw problem;
            }
        }
        return new WorkOrderWorkspaceIntegrationSectionData(
                inboundSummaries, outboundSummaries, null);
    }

    private WorkOrderWorkspaceVisitSummary toVisitSummary(VisitView visit) {
        // 不投影 GPS、device、note、operation/evidence refs，避免工作区泄露现场敏感细节。
        return new WorkOrderWorkspaceVisitSummary(
                visit.visitId(), visit.taskId(), visit.appointmentId(), visit.visitSequence(),
                visit.technicianId(), visit.networkId(), visit.status(),
                visit.checkInCapturedAt(), visit.checkInReceivedAt(),
                visit.geofenceResult(), visit.policyDecision(),
                visit.checkOutCapturedAt(), visit.checkOutReceivedAt(),
                visit.resultCode(), visit.exceptionCode(), visit.aggregateVersion());
    }

    private WorkOrderWorkspaceAppointmentSummary toAppointmentSummary(AppointmentView appointment) {
        AppointmentRevisionView current = appointment.revisions().stream()
                .filter(revision -> revision.revisionNo() == appointment.currentRevisionNo())
                .findFirst()
                .orElse(appointment.revisions().isEmpty() ? null : appointment.revisions().getLast());
        AppointmentWindow window = current == null ? null : current.window();
        return new WorkOrderWorkspaceAppointmentSummary(
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

    private WorkOrderWorkspaceContactAttemptSummary toContactAttemptSummary(ContactAttemptView attempt) {
        // 联系对象、自由文本、录音引用与操作者只在 Appointment 专用 API 中按需读取。
        return new WorkOrderWorkspaceContactAttemptSummary(
                attempt.contactAttemptId(), attempt.taskId(), attempt.projectId(),
                attempt.workOrderId(), attempt.channel(), attempt.startedAt(), attempt.endedAt(),
                attempt.resultCode().name(), attempt.nextContactAt(), attempt.createdAt());
    }

    private WorkOrderWorkspaceFormSummary toFormSummary(TaskFormDefinition form) {
        // 故意不投影 definitionJson：工作区只要绑定摘要，完整 schema 仍走 Task forms API。
        return new WorkOrderWorkspaceFormSummary(
                form.taskId(), form.formVersionId(), form.formKey(),
                form.semanticVersion(), form.schemaVersion(), form.contentDigest());
    }

    private WorkOrderWorkspaceFormSubmissionSummary toFormSubmissionSummary(
            FormSubmissionSummaryView submission
    ) {
        return new WorkOrderWorkspaceFormSubmissionSummary(
                submission.submissionId(), submission.taskId(), submission.projectId(),
                submission.formVersionId(), submission.formKey(), submission.submissionVersion(),
                submission.contentDigest(), submission.validationStatus(),
                submission.errorCount(), submission.warningCount(), submission.submittedAt());
    }

    private WorkOrderWorkspaceEvidenceSlotSummary toEvidenceSlotSummary(EvidenceSlotView slot) {
        // 故意不投影 requirementDefinition / resolutionExplanation JSON。
        return new WorkOrderWorkspaceEvidenceSlotSummary(
                slot.slotId(), slot.taskId(), slot.projectId(),
                slot.templateKey(), slot.templateVersion(),
                slot.requirementCode(), slot.occurrenceKey(), slot.requirementName(),
                slot.mediaType(), slot.required(), slot.minCount(), slot.maxCount(),
                slot.status(), slot.resolvedAt(), slot.slotGeneration(),
                slot.active(), slot.transition(), slot.requiredDisposition());
    }

    private WorkOrderWorkspaceEvidenceItemSummary toEvidenceItemSummary(EvidenceItemSummaryView item) {
        return new WorkOrderWorkspaceEvidenceItemSummary(
                item.evidenceItemId(), item.taskId(), item.projectId(), item.evidenceSlotId(),
                item.itemOrdinal(), item.status(), item.revisionCount(),
                item.latestRevisionNumber(), item.latestRevisionStatus(),
                item.latestRevisionId(), item.latestMimeType());
    }

    private WorkOrderWorkspaceReviewCaseSummary toReviewCaseSummary(ReviewCaseView review) {
        return new WorkOrderWorkspaceReviewCaseSummary(
                review.reviewCaseId(), review.taskId(), review.projectId(),
                review.evidenceSetSnapshotId(), review.scopeType(), review.origin(),
                review.policyVersion(), review.status(), review.createdAt(), review.decidedAt(),
                review.sourceReviewCaseId(), review.externalSubmissionRef(),
                review.callbackBatchRef(), review.mappingVersionId(),
                review.reopenedFromReviewCaseId(), review.reopenTriggerRef(),
                review.decisions().stream().map(this::toReviewDecisionSummary).toList());
    }

    private WorkOrderWorkspaceReviewDecisionSummary toReviewDecisionSummary(ReviewDecisionView decision) {
        // note / approvalRef / decidedBy 不进入工作区摘要，避免自由文本和操作者信息扩散。
        return new WorkOrderWorkspaceReviewDecisionSummary(
                decision.reviewDecisionId(), decision.decisionOrdinal(), decision.decision(),
                decision.decisionSource(), decision.reasonCodes(), decision.decidedAt());
    }

    private WorkOrderWorkspaceCorrectionCaseSummary toCorrectionCaseSummary(CorrectionCaseView correction) {
        return new WorkOrderWorkspaceCorrectionCaseSummary(
                correction.correctionCaseId(), correction.taskId(), correction.projectId(),
                correction.sourceReviewCaseId(), correction.sourceReviewDecisionId(),
                correction.reasonCodes(), correction.correctionTaskId(), correction.status(),
                correction.createdAt(), correction.latestResubmissionSnapshotId(),
                correction.closedAt(), correction.waivedAt(),
                correction.resubmissions().stream().map(this::toCorrectionResubmissionSummary).toList());
    }

    private WorkOrderWorkspaceCorrectionResubmissionSummary toCorrectionResubmissionSummary(
            CorrectionResubmissionView resubmission
    ) {
        return new WorkOrderWorkspaceCorrectionResubmissionSummary(
                resubmission.correctionResubmissionId(), resubmission.resubmissionOrdinal(),
                resubmission.evidenceSetSnapshotId(), resubmission.submittedAt());
    }

    private WorkOrderWorkspaceInboundEnvelopeSummary toInboundEnvelopeSummary(InboundEnvelopeView envelope) {
        return new WorkOrderWorkspaceInboundEnvelopeSummary(
                envelope.inboundEnvelopeId(), envelope.projectId(), envelope.connectorVersionId(),
                envelope.messageType(), envelope.externalMessageId(), envelope.signatureStatus(),
                envelope.processingStatus(), envelope.mappingVersionId(), envelope.canonicalMessageId(),
                envelope.resultCode(), envelope.resultType(), envelope.resultId(),
                envelope.receivedAt(), envelope.completedAt(), envelope.correlationId());
    }

    private WorkOrderWorkspaceOutboundDeliverySummary toOutboundDeliverySummary(
            OutboundDeliveryView delivery
    ) {
        return new WorkOrderWorkspaceOutboundDeliverySummary(
                delivery.deliveryId(), delivery.projectId(), delivery.connectorVersionId(),
                delivery.mappingVersionId(), delivery.businessMessageType(), delivery.businessKey(),
                delivery.sourceReviewCaseId(), delivery.sourceTaskId(), delivery.sourceWorkOrderId(),
                delivery.sourceSnapshotId(), delivery.externalOrderCode(), delivery.executionTaskId(),
                delivery.status(), delivery.clientReviewCaseId(), delivery.reviewRouteId(),
                delivery.aggregateVersion(), delivery.createdAt(), delivery.deliveredAt(),
                delivery.acknowledgedAt(),
                delivery.attempts().stream().map(this::toDeliveryAttemptSummary).toList(),
                delivery.acknowledgements().stream().map(this::toAcknowledgementSummary).toList(),
                delivery.replayRequests().stream().map(this::toDeliveryReplaySummary).toList());
    }

    private WorkOrderWorkspaceDeliveryAttemptSummary toDeliveryAttemptSummary(DeliveryAttemptView attempt) {
        // request/response digest 不进入工作区；详细取证仍走 Integration 专用 API。
        return new WorkOrderWorkspaceDeliveryAttemptSummary(
                attempt.deliveryAttemptId(), attempt.attemptNo(), attempt.taskExecutionAttemptId(),
                attempt.requestDate(), attempt.status(), attempt.httpStatus(), attempt.resultCode(),
                attempt.startedAt(), attempt.finishedAt());
    }

    private WorkOrderWorkspaceExternalAcknowledgementSummary toAcknowledgementSummary(
            ExternalAcknowledgementView acknowledgement
    ) {
        return new WorkOrderWorkspaceExternalAcknowledgementSummary(
                acknowledgement.acknowledgementId(), acknowledgement.acknowledgementType(),
                acknowledgement.result(), acknowledgement.reasonCode(),
                acknowledgement.mappingVersionId(), acknowledgement.receivedAt());
    }

    private WorkOrderWorkspaceDeliveryReplaySummary toDeliveryReplaySummary(
            DeliveryReplayRequestView replay
    ) {
        // reason / approvalRef / requestedBy 属于高风险人工操作明细，不扩散到工作区。
        return new WorkOrderWorkspaceDeliveryReplaySummary(
                replay.replayRequestId(), replay.executionTaskId(), replay.status(),
                replay.resultCode(), replay.requestedAt(), replay.startedAt(), replay.finishedAt());
    }

    private WorkOrderWorkspaceMeta meta(String freshnessStatus) {
        int generation = projectionRuntime.requireState().activeGeneration();
        String checkpoint = WorkOrderTimelineProjectionRuntime.PROJECTION_CODE + ":gen:" + generation;
        return new WorkOrderWorkspaceMeta(
                clock.instant(), checkpoint, freshnessStatus, UUID.randomUUID().toString());
    }

    private WorkOrderWorkspaceTaskSummary toTaskSummary(WorkOrderTaskSummary task) {
        return new WorkOrderWorkspaceTaskSummary(
                task.id(), task.taskType(), task.taskKind(), task.status(),
                task.stageCode(), task.claimedBy(), task.version());
    }

    private static String normalizeSection(String section) {
        if (section == null || section.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "section is required");
        }
        String normalized = section.trim().toUpperCase(Locale.ROOT);
        if (!ACCEPTED_SECTIONS.contains(normalized)) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED, "workspace section is not accepted: " + normalized);
        }
        return normalized;
    }

    private WorkOrderWorkspaceSlaSummary loadSlaSummary(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            Map<String, String> availability
    ) {
        try {
            List<SlaInstanceItem> items = slaQueries.listForWorkOrder(
                    principal, correlationId, workOrderId, null, 100).items();
            int open = 0;
            int breached = 0;
            for (SlaInstanceItem item : items) {
                if (OPEN_SLA_STATUSES.contains(item.status())) {
                    open++;
                }
                if ("BREACHED".equals(item.status())) {
                    breached++;
                }
            }
            availability.put("SLA", items.isEmpty() ? "EMPTY" : "AVAILABLE");
            return new WorkOrderWorkspaceSlaSummary(open, breached);
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                availability.put("SLA", "UNAVAILABLE");
                return null;
            }
            throw problem;
        }
    }

    private WorkOrderWorkspaceServiceAssignmentSummary loadServiceAssignmentSummary(
            CurrentPrincipal principal,
            String correlationId,
            WorkOrderTaskSummary current,
            Map<String, String> availability
    ) {
        if (current == null) {
            availability.put("SERVICE_ASSIGNMENT", "EMPTY");
            return null;
        }
        try {
            ServiceAssignmentSummary summary = serviceAssignments.findActiveForTask(
                            principal, correlationId, current.id())
                    .orElse(null);
            availability.put("SERVICE_ASSIGNMENT", summary == null ? "EMPTY" : "AVAILABLE");
            if (summary == null) {
                return null;
            }
            return new WorkOrderWorkspaceServiceAssignmentSummary(
                    summary.taskId(),
                    summary.networkId(),
                    summary.networkEffectiveFrom(),
                    summary.networkReassignmentReasonCode(),
                    summary.technicianId(),
                    summary.technicianEffectiveFrom(),
                    summary.technicianReassignmentReasonCode());
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                availability.put("SERVICE_ASSIGNMENT", "UNAVAILABLE");
                return null;
            }
            throw problem;
        }
    }

    private WorkOrderWorkspaceExceptionSummary loadExceptionSummary(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId,
            Map<String, String> availability
    ) {
        try {
            List<OperationalExceptionItem> items = exceptions.list(
                    principal,
                    correlationId,
                    new OperationalExceptionQuery(
                            null, "OPEN", null, null, workOrderId, null, null, 100)
            ).items();
            availability.put("EXCEPTIONS", items.isEmpty() ? "EMPTY" : "AVAILABLE");
            return new WorkOrderWorkspaceExceptionSummary(items.size());
        } catch (BusinessProblem problem) {
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                availability.put("EXCEPTIONS", "UNAVAILABLE");
                return null;
            }
            throw problem;
        }
    }
}
