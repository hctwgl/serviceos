package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工单目录当前工作流阶段旁载与筛选端口。
 *
 * <p>由 workflow 模块基于 ACTIVE Stage 实现。该事实覆盖无 Task 的
 * {@code REVIEW_TASK}/{@code WAIT_EVENT} 编排门闸；任务类型与任务状态仍由 task 模块负责。</p>
 */
public interface WorkOrderDirectoryWorkflowStageQuery {

    /**
     * @param tenantId 当前租户
     * @param workOrderIds 已授权工单 ID；空集合返回空 Map
     * @return workOrderId → ACTIVE workflow stageCode；未启动流程的工单不出现
     */
    Map<UUID, String> findCurrentStageCodes(String tenantId, Collection<UUID> workOrderIds);

    /**
     * 返回当前 ACTIVE workflow stage 等于给定值的工单 ID。
     */
    List<UUID> findWorkOrderIdsByCurrentStageCode(
            String tenantId,
            String stageCode,
            boolean tenantWide,
            Collection<UUID> projectIds
    );
}
