package com.serviceos.workorder.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
interface WorkOrderQueryMapper {
    List<Map<String,Object>> findPage(@Param("tenantId") String tenantId,
            @Param("tenantWide") boolean tenantWide, @Param("projectIds") List<String> projectIds,
            @Param("clientCode") String clientCode, @Param("projectId") UUID projectId,
            @Param("status") String status, @Param("externalOrderCode") String externalOrderCode,
            @Param("provinceCode") String provinceCode, @Param("cityCode") String cityCode,
            @Param("districtCode") String districtCode,
            @Param("applyStageFilter") boolean applyStageFilter,
            @Param("stageWorkOrderIds") List<String> stageWorkOrderIds,
            @Param("applyNetworkFilter") boolean applyNetworkFilter,
            @Param("networkWorkOrderIds") List<String> networkWorkOrderIds,
            @Param("applyTechnicianFilter") boolean applyTechnicianFilter,
            @Param("technicianWorkOrderIds") List<String> technicianWorkOrderIds,
            @Param("applySlaRiskFilter") boolean applySlaRiskFilter,
            @Param("slaRiskWorkOrderIds") List<String> slaRiskWorkOrderIds,
            @Param("cursorReceivedAt") Instant cursorReceivedAt,
            @Param("cursorId") UUID cursorId, @Param("fetchSize") int fetchSize);

    int countMatching(@Param("tenantId") String tenantId,
            @Param("tenantWide") boolean tenantWide, @Param("projectIds") List<String> projectIds,
            @Param("clientCode") String clientCode, @Param("projectId") UUID projectId,
            @Param("status") String status, @Param("externalOrderCode") String externalOrderCode,
            @Param("provinceCode") String provinceCode, @Param("cityCode") String cityCode,
            @Param("districtCode") String districtCode,
            @Param("applyStageFilter") boolean applyStageFilter,
            @Param("stageWorkOrderIds") List<String> stageWorkOrderIds,
            @Param("applyNetworkFilter") boolean applyNetworkFilter,
            @Param("networkWorkOrderIds") List<String> networkWorkOrderIds,
            @Param("applyTechnicianFilter") boolean applyTechnicianFilter,
            @Param("technicianWorkOrderIds") List<String> technicianWorkOrderIds,
            @Param("applySlaRiskFilter") boolean applySlaRiskFilter,
            @Param("slaRiskWorkOrderIds") List<String> slaRiskWorkOrderIds,
            @Param("fetchSize") int fetchSize);

    Map<String,Object> findById(@Param("tenantId") String tenantId, @Param("workOrderId") UUID workOrderId);

    Map<String,Object> findRawCustomerContact(
            @Param("tenantId") String tenantId, @Param("workOrderId") UUID workOrderId);
}
