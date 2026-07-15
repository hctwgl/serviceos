package com.serviceos.workorder.api;

import java.util.Optional;
import java.util.UUID;

/** 供其他模块在不访问工单内部表的前提下核验工单 Project Scope。 */
public interface WorkOrderScopeQuery {
    Optional<WorkOrderScope> find(String tenantId, UUID workOrderId);
}
