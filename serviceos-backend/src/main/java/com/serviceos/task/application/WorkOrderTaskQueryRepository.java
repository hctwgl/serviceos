package com.serviceos.task.application;

import com.serviceos.task.api.WorkOrderTaskSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface WorkOrderTaskQueryRepository {
    List<WorkOrderTaskSummary> findPage(String tenantId,UUID workOrderId,Instant cursorCreatedAt,UUID cursorId,int fetchSize);
}
