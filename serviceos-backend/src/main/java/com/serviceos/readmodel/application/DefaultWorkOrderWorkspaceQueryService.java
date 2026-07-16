package com.serviceos.readmodel.application;

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
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceTasksSectionData;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceTimelineSectionData;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 实时组合工单工作区顶层快照与按需区块。
 * <p>不落工作区投影表；SLA/异常缺权时标记 UNAVAILABLE，不把整个工作区打成 403。</p>
 * <p>故意不加外层事务：次级只读端口缺权会抛 {@code BusinessProblem}，若加入同一事务会
 * 把事务标成 rollback-only，即使外层捕获也会在提交时失败。</p>
 */
@Service
final class DefaultWorkOrderWorkspaceQueryService implements WorkOrderWorkspaceQueryService {
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "READY", "PENDING", "CLAIMED", "RUNNING", "RETRY_WAIT", "MANUAL_INTERVENTION");
    private static final Set<String> OPEN_SLA_STATUSES = Set.of("RUNNING", "BREACHED");
    private static final Set<String> ACCEPTED_SECTIONS = Set.of("TASKS", "TIMELINE_AUDIT");

    private final WorkOrderQueryService workOrders;
    private final WorkOrderTaskQueryService workOrderTasks;
    private final WorkOrderTimelineQueryService timelines;
    private final WorkOrderTimelineProjectionRuntime projectionRuntime;
    private final SlaQueryService slaQueries;
    private final OperationalExceptionWorkbenchService exceptions;
    private final Clock clock;

    DefaultWorkOrderWorkspaceQueryService(
            WorkOrderQueryService workOrders,
            WorkOrderTaskQueryService workOrderTasks,
            WorkOrderTimelineQueryService timelines,
            WorkOrderTimelineProjectionRuntime projectionRuntime,
            SlaQueryService slaQueries,
            OperationalExceptionWorkbenchService exceptions,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.workOrderTasks = workOrderTasks;
        this.timelines = timelines;
        this.projectionRuntime = projectionRuntime;
        this.slaQueries = slaQueries;
        this.exceptions = exceptions;
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
        availability.put("APPOINTMENTS_VISITS", "UNAVAILABLE");
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
                                page.freshnessStatus()));
            }
            default -> throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED, "workspace section is not accepted: " + normalized);
        };
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
