package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;
import java.util.UUID;

public interface WorkOrderTaskQueryService {
    WorkOrderTaskPage list(CurrentPrincipal principal,String correlationId,UUID workOrderId,String cursor,int limit);
}
