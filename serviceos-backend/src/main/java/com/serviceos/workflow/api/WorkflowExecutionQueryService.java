package com.serviceos.workflow.api;

import com.serviceos.identity.api.CurrentPrincipal;
import java.util.UUID;

public interface WorkflowExecutionQueryService {
    WorkflowExecutionProjection get(CurrentPrincipal principal, String correlationId, UUID workOrderId);
}
