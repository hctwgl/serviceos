package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 工单目录 SLA 风险旁载与筛选端口。
 *
 * <p>由 sla 模块实现。调用方已完成 PROJECT {@code sla.read} soft-gate 与项目范围收敛；
 * 本端口不再鉴权。</p>
 *
 * <p>旁载仅返回 openCount&gt;0 的摘要行。筛选口径：
 * {@code OPEN}=RUNNING∪BREACHED；{@code BREACHED}=仅 BREACHED；
 * {@code NEAR}=RUNNING 且 {@code now &lt; deadline_at ≤ now+30m}（M445 Admin 目录 MVP）。</p>
 */
public interface WorkOrderDirectorySlaRiskQuery {

    List<WorkOrderDirectorySlaRiskSummary> findOpenRisks(
            String tenantId, Collection<UUID> workOrderIds);

    /**
     * 解析具备指定 SLA 风险口径的工单 ID。
     *
     * @param slaRisk {@code OPEN}、{@code BREACHED} 或 {@code NEAR}
     * @param now 用于 NEAR 窗口的当前时刻；OPEN/BREACHED 可忽略
     * @return 匹配工单 ID；无匹配时为空列表（调用方应失败关闭为无结果）
     */
    List<UUID> findWorkOrderIdsBySlaRisk(
            String tenantId,
            String slaRisk,
            boolean tenantWide,
            Collection<UUID> projectIds,
            Instant now
    );
}
