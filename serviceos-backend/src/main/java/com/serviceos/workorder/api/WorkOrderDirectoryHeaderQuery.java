package com.serviceos.workorder.api;

import java.util.Optional;
import java.util.UUID;

/** M236：按租户 + workOrderId 读取目录用非 PII 工单头。 */
public interface WorkOrderDirectoryHeaderQuery {
    Optional<WorkOrderDirectoryHeader> find(String tenantId, UUID workOrderId);
}
