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
            @Param("cursorReceivedAt") Instant cursorReceivedAt,
            @Param("cursorId") UUID cursorId, @Param("fetchSize") int fetchSize);
    Map<String,Object> findById(@Param("tenantId") String tenantId, @Param("workOrderId") UUID workOrderId);
}
