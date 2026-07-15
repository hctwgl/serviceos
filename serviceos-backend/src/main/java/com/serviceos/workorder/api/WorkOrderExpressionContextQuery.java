package com.serviceos.workorder.api;

import java.util.Optional;
import java.util.UUID;

/** WorkOrder 表达式上下文只读查询；供 Evidence 条件槽位解析使用。 */
public interface WorkOrderExpressionContextQuery {
    Optional<WorkOrderExpressionContext> find(String tenantId, UUID workOrderId);
}
