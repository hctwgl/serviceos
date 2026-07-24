package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工单目录网点/师傅服务责任旁载与筛选端口。
 *
 * <p>由 dispatch 模块实现。调用方已完成 {@code workOrder.read} 项目范围授权，
 * 本端口不再二次鉴权，也不穿越租户。项目范围收敛仍由工单授权 SQL 负责。</p>
 *
 * <p>口径：工单下 ACTIVE NETWORK / ACTIVE TECHNICIAN ServiceAssignment；
 * 同级别多条时取 effective_from 最新。无 ACTIVE 责任时不出现在结果中。</p>
 */
public interface WorkOrderDirectoryServiceResponsibilityQuery {

    Map<UUID, WorkOrderDirectoryServiceResponsibility> findActive(
            String tenantId, Collection<UUID> workOrderIds);

    /**
     * M440：解析 ACTIVE NETWORK assignee 等于给定网点 ID 的工单集合（与目录列同口径）。
     *
     * @return 匹配工单 ID；无匹配时为空列表（调用方应失败关闭为无结果，不得忽略筛选）
     */
    List<UUID> findWorkOrderIdsByActiveNetworkId(String tenantId, UUID networkId);

    /**
     * M441：解析 ACTIVE TECHNICIAN assignee 等于给定师傅档案 ID 的工单集合（与目录列同口径）。
     *
     * @return 匹配工单 ID；无匹配时为空列表（调用方应失败关闭为无结果，不得忽略筛选）
     */
    List<UUID> findWorkOrderIdsByActiveTechnicianId(String tenantId, UUID technicianProfileId);

    /**
     * 返回租户内当前存在 ACTIVE NETWORK 服务责任的工单集合。
     *
     * <p>调用方用该集合做反向筛选得到待派责任网点工单；工单授权和项目范围仍由调用方 SQL 收敛。</p>
     */
    List<UUID> findWorkOrderIdsWithActiveNetwork(String tenantId);
}
