package com.serviceos.operations.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** MyBatis SQL 边界；只能由同包 Repository 适配器调用。 */
@Mapper
interface OperationalExceptionWorkbenchMapper {
    List<Map<String, Object>> findPage(
            @Param("tenantId") String tenantId,
            @Param("tenantWide") boolean tenantWide,
            @Param("projectIds") List<String> projectIds,
            @Param("projectId") String projectId,
            @Param("status") String status,
            @Param("category") String category,
            @Param("severity") String severity,
            @Param("workOrderId") UUID workOrderId,
            @Param("taskId") UUID taskId,
            @Param("cursorOpenedAt") OffsetDateTime cursorOpenedAt,
            @Param("cursorId") UUID cursorId,
            @Param("fetchSize") int fetchSize);

    Map<String, Object> findById(@Param("tenantId") String tenantId, @Param("exceptionId") UUID exceptionId);

    List<Map<String, Object>> listByTask(@Param("tenantId") String tenantId, @Param("taskId") String taskId);

    int acknowledge(
            @Param("tenantId") String tenantId, @Param("exceptionId") UUID exceptionId,
            @Param("expectedVersion") long expectedVersion, @Param("actorId") String actorId,
            @Param("note") String note, @Param("acknowledgedAt") OffsetDateTime acknowledgedAt);

    void insertAcknowledgement(
            @Param("tenantId") String tenantId, @Param("idempotencyKey") String idempotencyKey,
            @Param("exceptionId") UUID exceptionId, @Param("aggregateVersion") long aggregateVersion,
            @Param("acknowledgedAt") OffsetDateTime acknowledgedAt, @Param("acknowledgedBy") String acknowledgedBy);

    Map<String, Object> findAcknowledgement(
            @Param("tenantId") String tenantId, @Param("idempotencyKey") String idempotencyKey);
}
