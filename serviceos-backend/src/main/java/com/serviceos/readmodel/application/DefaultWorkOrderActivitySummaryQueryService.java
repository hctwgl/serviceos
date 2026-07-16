package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.WorkOrderActivitySummary;
import com.serviceos.readmodel.api.WorkOrderActivitySummaryQueryService;
import com.serviceos.readmodel.api.WorkOrderTimelinePage;
import com.serviceos.readmodel.api.WorkOrderTimelineQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceMeta;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * 最近活动仅复用权威时间线第一页；不维护第二份投影，也不引入未接受的关键事件分类。
 */
@Service
final class DefaultWorkOrderActivitySummaryQueryService
        implements WorkOrderActivitySummaryQueryService {
    private final WorkOrderTimelineQueryService timelines;
    private final WorkOrderTimelineProjectionRuntime projectionRuntime;
    private final Clock clock;

    DefaultWorkOrderActivitySummaryQueryService(
            WorkOrderTimelineQueryService timelines,
            WorkOrderTimelineProjectionRuntime projectionRuntime,
            Clock clock
    ) {
        this.timelines = timelines;
        this.projectionRuntime = projectionRuntime;
        this.clock = clock;
    }

    @Override
    public WorkOrderActivitySummary get(
            CurrentPrincipal principal, String correlationId, UUID workOrderId, int limit
    ) {
        if (limit < 1 || limit > 20) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED, "limit must be between 1 and 20");
        }
        WorkOrderTimelinePage page = timelines.list(
                principal, correlationId, workOrderId, null, limit);
        int generation = projectionRuntime.requireState().activeGeneration();
        WorkOrderWorkspaceMeta meta = new WorkOrderWorkspaceMeta(
                clock.instant(),
                WorkOrderTimelineProjectionRuntime.PROJECTION_CODE + ":gen:" + generation,
                page.freshnessStatus(),
                UUID.randomUUID().toString());
        return new WorkOrderActivitySummary(
                page.resourceVersion(), page.items(), page.lastProjectedAt(), meta);
    }
}
