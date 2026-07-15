package com.serviceos.workflow.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.workflow.api.WorkflowExecutionProjection;
import com.serviceos.workflow.api.WorkflowExecutionQueryService;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;

/** 先复用 WorkOrder 公开查询完成隔离与鉴权，再读取本模块执行事实。 */
@Service
final class DefaultWorkflowExecutionQueryService implements WorkflowExecutionQueryService {
    private final WorkOrderQueryService workOrders; private final WorkflowExecutionQueryRepository queries;
    private final Clock clock;
    DefaultWorkflowExecutionQueryService(WorkOrderQueryService workOrders,
            WorkflowExecutionQueryRepository queries, Clock clock) {
        this.workOrders=workOrders; this.queries=queries; this.clock=clock;
    }
    @Override @Transactional(readOnly=true)
    public WorkflowExecutionProjection get(CurrentPrincipal principal,String correlationId,UUID workOrderId) {
        workOrders.get(principal,correlationId,workOrderId);
        return new WorkflowExecutionProjection(queries.findWorkflow(principal.tenantId(),workOrderId).orElse(null),
                queries.findStages(principal.tenantId(),workOrderId),clock.instant());
    }
}
