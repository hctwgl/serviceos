package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;
import java.util.UUID;

public interface TaskDirectoryQueryService {
 TaskDirectoryPage list(CurrentPrincipal principal,String correlationId,TaskDirectoryQuery query);
 TaskDetail get(CurrentPrincipal principal,String correlationId,UUID taskId);
}
