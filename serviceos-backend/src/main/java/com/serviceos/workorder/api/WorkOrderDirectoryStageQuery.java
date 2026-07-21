package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工单目录当前阶段/任务状态旁载与筛选端口。
 *
 * <p>由 task 模块实现。调用方（工单授权目录）已完成 {@code workOrder.read} 项目范围授权，
 * 本端口不再二次鉴权，也不穿越租户。</p>
 *
 * <p>口径与工作区 {@code currentTaskSummary} 一致：每个工单取创建时间最早的
 * ACTIVE 任务（READY/PENDING/CLAIMED/RUNNING/RETRY_WAIT/MANUAL_INTERVENTION）；
 * 无 ACTIVE 任务时不出现在结果中。</p>
 */
public interface WorkOrderDirectoryStageQuery {

    /**
     * @param tenantId 当前租户
     * @param workOrderIds 已授权工单 ID；空集合返回空 Map
     * @return workOrderId → stageCode；缺任务的工单不出现
     */
    Map<UUID, String> findCurrentStageCodes(String tenantId, Collection<UUID> workOrderIds);

    /**
     * M446：当前 ACTIVE 任务 status；缺任务的工单不出现。
     */
    Map<UUID, String> findCurrentTaskStatuses(String tenantId, Collection<UUID> workOrderIds);

    /**
     * M438：返回当前阶段码等于给定值的工单 ID（同 {@link #findCurrentStageCodes} 口径）。
     */
    List<UUID> findWorkOrderIdsByCurrentStageCode(
            String tenantId,
            String stageCode,
            boolean tenantWide,
            Collection<UUID> projectIds
    );

    /**
     * M446：返回当前任务状态等于给定值的工单 ID（同 {@link #findCurrentTaskStatuses} 口径）。
     */
    List<UUID> findWorkOrderIdsByCurrentTaskStatus(
            String tenantId,
            String taskStatus,
            boolean tenantWide,
            Collection<UUID> projectIds
    );
}
