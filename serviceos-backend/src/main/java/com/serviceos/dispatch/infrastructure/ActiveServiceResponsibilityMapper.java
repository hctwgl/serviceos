package com.serviceos.dispatch.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;
import java.util.UUID;

/** ACTIVE ServiceAssignment 只读投影的 MyBatis SQL 边界。 */
@Mapper
interface ActiveServiceResponsibilityMapper {
    Map<String, Object> find(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId);

    Map<String, Object> findSummary(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId);
}
