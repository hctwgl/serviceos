package com.serviceos.workflow.api;

import java.time.Instant;
import java.util.List;

/** 工单工作区中的 Workflow/Stage 当前权威投影；未初始化时 workflow 为 null。 */
public record WorkflowExecutionProjection(
        WorkflowInstanceView workflow, List<StageInstanceView> stages, Instant asOf) {
    public WorkflowExecutionProjection { stages = List.copyOf(stages); }
}
