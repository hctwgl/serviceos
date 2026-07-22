package com.serviceos.workorder.application;

import com.serviceos.workorder.api.WorkOrderView;
import com.serviceos.workorder.api.WorkOrderProjectPersonnelView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 授权工单查询端口；列表范围必须在 SQL 中收敛。 */
public interface WorkOrderQueryRepository {
    List<WorkOrderView> findPage(String tenantId, boolean tenantWide, List<UUID> authorizedProjectIds,
            String clientCode, UUID projectId, String status, String externalOrderCode,
            String provinceCode, String cityCode, String districtCode,
            boolean applyStageFilter, List<UUID> stageWorkOrderIds,
            boolean applyTaskStatusFilter, List<UUID> taskStatusWorkOrderIds,
            boolean applyNetworkFilter, List<UUID> networkWorkOrderIds,
            boolean applyTechnicianFilter, List<UUID> technicianWorkOrderIds,
            boolean applySlaRiskFilter, List<UUID> slaRiskWorkOrderIds,
            boolean applyReviewCorrectionFilter, List<UUID> reviewCorrectionWorkOrderIds,
            String keywordPhoneLast4, String keywordLikePattern,
            Instant receivedFromInclusive, Instant receivedToExclusive,
            Instant cursorReceivedAt, UUID cursorId, int fetchSize);

    /**
     * M444：当前筛选（无 cursor）精确匹配行数；与分页 cursor/limit 无关。
     */
    int countMatching(String tenantId, boolean tenantWide, List<UUID> authorizedProjectIds,
            String clientCode, UUID projectId, String status, String externalOrderCode,
            String provinceCode, String cityCode, String districtCode,
            boolean applyStageFilter, List<UUID> stageWorkOrderIds,
            boolean applyTaskStatusFilter, List<UUID> taskStatusWorkOrderIds,
            boolean applyNetworkFilter, List<UUID> networkWorkOrderIds,
            boolean applyTechnicianFilter, List<UUID> technicianWorkOrderIds,
            boolean applySlaRiskFilter, List<UUID> slaRiskWorkOrderIds,
            boolean applyReviewCorrectionFilter, List<UUID> reviewCorrectionWorkOrderIds,
            String keywordPhoneLast4, String keywordLikePattern,
            Instant receivedFromInclusive, Instant receivedToExclusive);

    Optional<WorkOrderView> findById(String tenantId, UUID workOrderId);

    /** 读取客户联系原文供模块内脱敏；不得跨模块直接暴露。 */
    Optional<RawCustomerContact> findRawCustomerContact(String tenantId, UUID workOrderId);

    List<WorkOrderProjectPersonnelView> findProjectPersonnel(String tenantId, UUID workOrderId);

    record RawCustomerContact(
            UUID workOrderId,
            String customerName,
            String customerMobile,
            String serviceAddress
    ) {
    }
}
