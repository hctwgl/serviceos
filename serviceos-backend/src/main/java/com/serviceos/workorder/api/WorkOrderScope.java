package com.serviceos.workorder.api;

import java.util.UUID;

/** 跨模块只读授权所需的最小工单范围，不暴露客户或地址等敏感业务字段。 */
public record WorkOrderScope(UUID workOrderId, UUID projectId) {
    public WorkOrderScope {
        java.util.Objects.requireNonNull(workOrderId, "workOrderId must not be null");
        java.util.Objects.requireNonNull(projectId, "projectId must not be null");
    }
}
