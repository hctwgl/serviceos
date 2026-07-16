package com.serviceos.readmodel.application;

import com.serviceos.appointment.api.AppointmentRevisionView;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentView;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.fieldwork.api.VisitService;
import com.serviceos.fieldwork.api.VisitView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.operations.api.OperationalExceptionItem;
import com.serviceos.operations.api.OperationalExceptionQuery;
import com.serviceos.operations.api.OperationalExceptionWorkbenchService;
import com.serviceos.readmodel.api.WorkOrderTimelineQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceExceptionSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceMeta;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceSlaSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceTaskSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceAppointmentSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceAppointmentsVisitsSectionData;
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
import com.serviceos.workorder.api.WorkOrderQueryService;
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
 * <p>不落工作区投影表；SLA/异常/预约到访缺权时标记 UNAVAILABLE，不把整个工作区打成 403。</p>
 * <p>故意不加外层事务：次级只读端口缺权会抛 {@code BusinessProblem}，若加入同一事务会
 * 把事务标成 rollback-only，即使外层捕获也会在提交时失败。</p>
 */
@Service
final class DefaultWorkOrderWorkspaceQueryService implements WorkOrderWorkspaceQueryService {
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "READY", "PENDING", "CLAIMED", "RUNNING", "RETRY_WAIT", "MANUAL_INTERVENTION");
    private static final Set<String> OPEN_SLA_STATUSES = Set.of("RUNNING", "BREACHED");
    private static final Set<String> ACCEPTED_SECTIONS = Set.of(
            "TASKS", "TIMELINE_AUDIT", "APPOINTMENTS_VISITS");

    private final WorkOrderQueryService workOrders;
    private final WorkOrderTaskQueryService workOrderTasks;
    private final WorkOrderTimelineQueryService timelines;
    private final WorkOrderTimelineProjectionRuntime projectionRuntime;
    private final SlaQueryService slaQueries;
    private final OperationalExceptionWorkbenchService exceptions;
    private final VisitService visits;
    private final AppointmentService appointments;
    private final Clock clock;

    DefaultWorkOrderWorkspaceQueryService(
            WorkOrderQueryService workOrders,
            WorkOrderTaskQueryService workOrderTasks,
            WorkOrderTimelineQueryService timelines,
            WorkOrderTimelineProjectionRuntime projectionRuntime,
            SlaQueryService slaQueries,
            OperationalExceptionWorkbenchService exceptions,
            VisitService visits,
            AppointmentService appointments,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.workOrderTasks = workOrderTasks;
        this.timelines = timelines;
        this.projectionRuntime = projectionRuntime;
        this.slaQueries = slaQueries;
        this.exceptions = exceptions;
        this.visits = visits;
        this.appointments = appointments;
        this.clock = clock;
    }

    @Override
    public WorkOrderWorkspace get(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId
    ) {
        WorkOrderDetail detail = workOrders.get(principal, correlationId, workOrderId);
        List<WorkOrderTaskSummary> tasks = workOrderTasks.list(
                principal, correlationId, workOrderId, null, 100).items();
        WorkOrderTaskSummary current = tasks.stream()
                .filter(task -> ACTIVE_TASK_STATUSES.contains(task.status()))
                .findFirst()
                .orElse(null);

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
        availability.put("FORMS_EVIDENCE", "UNAVAILABLE");
        availability.put("REVIEWS_CORRECTIONS", "UNAVAILABLE");
        availability.put("INTEGRATION", "UNAVAILABLE");
        availability.put("FACTS_CALCULATIONS", "UNAVAILABLE");
        availability.put("SERVICE_ASSIGNMENT", "UNAVAILABLE");

        WorkOrderWorkspaceSlaSummary slaSummary = loadSlaSummary(
                principal, correlationId, workOrderId, availability);
        WorkOrderWorkspaceExceptionSummary exceptionSummary = loadExceptionSummary(
                principal, correlationId, workOrderId, availability);

        return new WorkOrderWorkspace(
                detail.workOrder(),
                current == null ? null : toTaskSummary(current),
                availability,
                current == null ? null : "/api/v1/tasks/" + current.id() + "/allowed-actions",
                slaSummary,
                exceptionSummary,
                timelineFreshness,
                new WorkOrderWorkspaceSourceVersions(detail.workOrder().version()),
                meta(timelineFreshness));
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
                        normalized, versions, meta("FRESH"), null, null, payload);
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
        boolean visitsEmpty = visitDenied || loaded.visits().isEmpty();
        boolean appointmentsEmpty = appointmentDenied || loaded.appointments().isEmpty();
        if (visitDenied && appointmentDenied) {
            return "UNAVAILABLE";
        }
        if (visitDenied && appointmentsEmpty) {
            return "UNAVAILABLE";
        }
        if (appointmentDenied && visitsEmpty) {
            return "UNAVAILABLE";
        }
        if (visitsEmpty && appointmentsEmpty) {
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

        return new WorkOrderWorkspaceAppointmentsVisitsSectionData(
                visitSummaries, appointmentSummaries, null);
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
                    new OperationalExceptionQuery("OPEN", null, null, workOrderId, null, null, 100)
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
