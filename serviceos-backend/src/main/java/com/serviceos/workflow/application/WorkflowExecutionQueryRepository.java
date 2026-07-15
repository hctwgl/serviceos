package com.serviceos.workflow.application;

import com.serviceos.workflow.api.StageInstanceView;
import com.serviceos.workflow.api.WorkflowInstanceView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowExecutionQueryRepository {
    Optional<WorkflowInstanceView> findWorkflow(String tenantId, UUID workOrderId);
    List<StageInstanceView> findStages(String tenantId, UUID workOrderId);
}
