package com.serviceos.task.api;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

/** 为预约、到场等下游履约能力提供 Task 责任与业务归属的只读公开边界。 */
public interface TaskFulfillmentContextService {
    Optional<TaskFulfillmentContext> find(String tenantId, UUID taskId);

    /**
     * 按工单读取全部 Task 履约上下文，供整单责任投影到后续流程阶段。
     *
     * <p>调用方只能据此组合只读责任视图，不能绕过 Task 命令修改状态或责任。</p>
     */
    List<TaskFulfillmentContext> listForWorkOrder(String tenantId, UUID workOrderId);
}
