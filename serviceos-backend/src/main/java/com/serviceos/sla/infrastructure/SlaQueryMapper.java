package com.serviceos.sla.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SLA MyBatis SQL 边界，只能由同模块 Repository 适配器调用。 */
@Mapper
interface SlaQueryMapper {
    List<Map<String, Object>> findPage(
            @Param("tenantId") String tenantId,
            @Param("tenantWide") boolean tenantWide,
            @Param("projectIds") List<String> projectIds,
            @Param("workOrderId") UUID workOrderId,
            @Param("status") String status,
            @Param("cursorDeadlineAt") OffsetDateTime cursorDeadlineAt,
            @Param("cursorId") UUID cursorId,
            @Param("fetchSize") int fetchSize);

    Map<String, Object> findById(
            @Param("tenantId") String tenantId,
            @Param("slaInstanceId") UUID slaInstanceId);

    List<Map<String, Object>> findSegments(
            @Param("tenantId") String tenantId,
            @Param("slaInstanceId") UUID slaInstanceId);

    List<Map<String, Object>> findMilestones(
            @Param("tenantId") String tenantId,
            @Param("slaInstanceId") UUID slaInstanceId);
}
