package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 工单目录 SLA 风险旁载端口。
 *
 * <p>由 sla 模块实现。调用方已完成 PROJECT {@code sla.read} soft-gate 与项目范围收敛；
 * 本端口不再鉴权。仅返回 openCount&gt;0 的摘要行。</p>
 */
public interface WorkOrderDirectorySlaRiskQuery {

    List<WorkOrderDirectorySlaRiskSummary> findOpenRisks(
            String tenantId, Collection<UUID> workOrderIds);
}
