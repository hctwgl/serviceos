package com.serviceos.fieldwork.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Visit MyBatis SQL 边界，仅供同包 Repository 适配器使用。 */
@Mapper
interface VisitMapper {
    Map<String, Object> findById(@Param("tenantId") String tenantId, @Param("visitId") UUID visitId);

    List<Map<String, Object>> findByWorkOrder(
            @Param("tenantId") String tenantId, @Param("workOrderId") UUID workOrderId);

    Map<String, Object> findGeofencePolicy(
            @Param("tenantId") String tenantId, @Param("projectId") UUID projectId);

    int nextSequence(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId);

    Long lockTask(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId);

    void insertVisit(Map<String, Object> values);

    int terminate(Map<String, Object> values);

    void insertFact(Map<String, Object> values);

    void insertResult(Map<String, Object> values);

    Map<String, Object> findResult(
            @Param("tenantId") String tenantId, @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);
}
