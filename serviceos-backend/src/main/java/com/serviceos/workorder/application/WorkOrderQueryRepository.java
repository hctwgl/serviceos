package com.serviceos.workorder.application;

import com.serviceos.workorder.api.WorkOrderView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 授权工单查询端口；列表范围必须在 SQL 中收敛。 */
public interface WorkOrderQueryRepository {
    List<WorkOrderView> findPage(String tenantId, boolean tenantWide, List<UUID> authorizedProjectIds,
            String clientCode, UUID projectId, String status, String externalOrderCode,
            Instant cursorReceivedAt, UUID cursorId, int fetchSize);
    Optional<WorkOrderView> findById(String tenantId, UUID workOrderId);
}
