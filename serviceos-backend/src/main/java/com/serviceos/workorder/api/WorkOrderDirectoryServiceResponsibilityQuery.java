package com.serviceos.workorder.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * 工单目录网点/师傅服务责任旁载端口。
 *
 * <p>由 dispatch 模块实现。调用方已完成 {@code workOrder.read} 项目范围授权，
 * 本端口不再二次鉴权，也不穿越租户。</p>
 *
 * <p>口径：工单下 ACTIVE NETWORK / ACTIVE TECHNICIAN ServiceAssignment；
 * 同级别多条时取 effective_from 最新。无 ACTIVE 责任时不出现在结果中。</p>
 */
public interface WorkOrderDirectoryServiceResponsibilityQuery {

    Map<UUID, WorkOrderDirectoryServiceResponsibility> findActive(
            String tenantId, Collection<UUID> workOrderIds);
}
