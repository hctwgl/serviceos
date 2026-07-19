package com.serviceos.workorder.api;

import java.util.Optional;
import java.util.UUID;

/** 读取工单冻结 Bundle 绑定。 */
public interface WorkOrderConfigurationBindingQuery {
    Optional<WorkOrderConfigurationBinding> find(String tenantId, UUID workOrderId);
}
