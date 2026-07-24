package com.serviceos.task.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Task 履约上下文的 MyBatis SQL 边界，只供同包公开查询适配器调用。 */
@Mapper
interface TaskFulfillmentContextMapper {
    Map<String, Object> find(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId);

    List<Map<String, Object>> listForWorkOrder(
            @Param("tenantId") String tenantId,
            @Param("workOrderId") UUID workOrderId);
}
