package com.serviceos.workflow.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
interface WorkflowExecutionQueryMapper {
    Map<String,Object> findWorkflow(@Param("tenantId") String tenantId,@Param("workOrderId") UUID workOrderId);
    List<Map<String,Object>> findStages(@Param("tenantId") String tenantId,@Param("workOrderId") UUID workOrderId);
}
