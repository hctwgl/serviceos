package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工单目录当前阶段旁载端口。
 *
 * <p>由 task 模块实现。调用方（工单授权目录）已完成 {@code workOrder.read} 项目范围授权，
 * 本端口不再二次鉴权，也不穿越租户。</p>
 *
 * <p>口径与工作区 {@code currentTaskSummary.stageCode} 一致：每个工单取创建时间最早的
 * ACTIVE 任务（READY/PENDING/CLAIMED/RUNNING/RETRY_WAIT/MANUAL_INTERVENTION）的
 * {@code stageCode}；无 ACTIVE 任务时不出现在结果中。</p>
 */
public interface WorkOrderDirectoryStageQuery {

    /**
     * @param tenantId 当前租户
     * @param workOrderIds 已授权工单 ID；空集合返回空 Map
     * @return workOrderId → stageCode；缺任务的工单不出现
     */
    Map<UUID, String> findCurrentStageCodes(String tenantId, Collection<UUID> workOrderIds);

    /**
     * M438：返回当前阶段码等于给定值的工单 ID（同 {@link #findCurrentStageCodes} 口径）。
     *
     * <p>调用方负责将结果以 {@code id IN (...)} 收敛到已授权工单 SQL；本方法可按项目范围
     * 预裁剪，避免无授权项目的任务泄漏到候选集。</p>
     *
     * @param tenantId 当前租户
     * @param stageCode 已规范化的阶段码
     * @param tenantWide 是否租户级授权
     * @param projectIds 非租户级时的授权项目；空且非 tenantWide 时返回空列表
     */
    List<UUID> findWorkOrderIdsByCurrentStageCode(
            String tenantId,
            String stageCode,
            boolean tenantWide,
            Collection<UUID> projectIds
    );
}
