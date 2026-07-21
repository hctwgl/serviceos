package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 工单目录当前责任人旁载端口。
 *
 * <p>由 task 模块实现。调用方已完成 {@code workOrder.read} 项目范围授权。
 * 口径与工作区 currentTask 一致：首个 ACTIVE 任务的 {@code claimed_by}；
 * 未认领或无 ACTIVE 任务时不出现在结果中。</p>
 */
public interface WorkOrderDirectoryAssigneeQuery {

    /**
     * @return workOrderId → claimedBy（非空字符串）；缺认领的工单不出现
     */
    Map<UUID, String> findCurrentClaimedBy(String tenantId, Collection<UUID> workOrderIds);
}
