package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 工单目录运营异常旁载端口。
 *
 * <p>由 operations 模块实现。调用方已完成 PROJECT {@code operations.exception.read} soft-gate
 * 与项目范围收敛；本端口不再鉴权。</p>
 *
 * <p>旁载仅返回 OPEN 异常 openCount&gt;0 的摘要行（与工作区 exceptionSummary 口径一致）。</p>
 */
public interface WorkOrderDirectoryExceptionQuery {

    List<WorkOrderDirectoryExceptionSummary> findOpenCounts(
            String tenantId, Collection<UUID> workOrderIds);
}
