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
            String provinceCode, String cityCode, String districtCode,
            Instant cursorReceivedAt, UUID cursorId, int fetchSize);

    /**
     * M436：当前筛选（无 cursor）匹配行数，最多扫描 {@code fetchSize} 行。
     * 调用方用 fetchSize=limit+1 判断 truncated，避免精确全量 COUNT(*)。
     */
    int countMatching(String tenantId, boolean tenantWide, List<UUID> authorizedProjectIds,
            String clientCode, UUID projectId, String status, String externalOrderCode,
            String provinceCode, String cityCode, String districtCode,
            int fetchSize);

    Optional<WorkOrderView> findById(String tenantId, UUID workOrderId);

    /** 读取客户联系原文供模块内脱敏；不得跨模块直接暴露。 */
    Optional<RawCustomerContact> findRawCustomerContact(String tenantId, UUID workOrderId);

    record RawCustomerContact(
            UUID workOrderId,
            String customerName,
            String customerMobile,
            String serviceAddress
    ) {
    }
}
