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
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.sla.api.SlaInstanceItem;
import com.serviceos.sla.api.SlaQueryService;
import com.serviceos.task.api.WorkOrderTaskQueryService;
import com.serviceos.task.api.WorkOrderTaskSummary;
import com.serviceos.workorder.api.WorkOrderDetail;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 实时组合工单工作区顶层快照。
 * <p>不落工作区投影表；SLA/异常缺权时标记 UNAVAILABLE，不把整个工作区打成 403。</p>
 * <p>故意不加外层事务：次级只读端口缺权会抛 {@code BusinessProblem}，若加入同一事务会
 * 把事务标成 rollback-only，即使外层捕获也会在提交时失败。</p>
 */
@Service
final class DefaultWorkOrderWorkspaceQueryService implements WorkOrderWorkspaceQueryService {
    private static final Set<String> ACTIVE_TASK_STATUSES = Set.of(
            "READY", "PENDING", "CLAIMED", "RUNNING", "RETRY_WAIT", "MANUAL_INTERVENTION");
    private static final Set<String> OPEN_SLA_STATUSES = Set.of("RUNNING", "BREACHED");

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

        int generation = projectionRuntime.requireState().activeGeneration();
        String checkpoint = WorkOrderTimelineProjectionRuntime.PROJECTION_CODE + ":gen:" + generation;

        return new WorkOrderWorkspace(
                detail.workOrder(),
                current == null ? null : new WorkOrderWorkspaceTaskSummary(
                        current.id(), current.taskType(), current.taskKind(), current.status(),
                        current.stageCode(), current.claimedBy(), current.version()),
                availability,
                current == null ? null : "/api/v1/tasks/" + current.id() + "/allowed-actions",
                slaSummary,
                exceptionSummary,
                timelineFreshness,
                new WorkOrderWorkspaceSourceVersions(detail.workOrder().version()),
                new WorkOrderWorkspaceMeta(
                        clock.instant(), checkpoint, timelineFreshness, UUID.randomUUID().toString()));
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
