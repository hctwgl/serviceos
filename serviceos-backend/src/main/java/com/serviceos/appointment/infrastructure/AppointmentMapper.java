package com.serviceos.appointment.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 预约聚合的 MyBatis SQL 边界，只能由同包 Repository 适配器调用。 */
@Mapper
interface AppointmentMapper {
    Map<String, Object> findById(@Param("tenantId") String tenantId, @Param("appointmentId") UUID appointmentId);

    List<Map<String, Object>> findByTask(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId);

    List<Map<String, Object>> findRevisions(
            @Param("tenantId") String tenantId, @Param("appointmentId") UUID appointmentId);

    List<Map<String, Object>> findContactAttempts(
            @Param("tenantId") String tenantId, @Param("taskId") UUID taskId);

    Map<String, Object> findContactAttemptById(
            @Param("tenantId") String tenantId, @Param("contactAttemptId") UUID contactAttemptId);

    void insertContactAttempt(Map<String, Object> parameters);

    void insertContactResult(Map<String, Object> parameters);

    Map<String, Object> findContactResult(
            @Param("tenantId") String tenantId,
            @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);

    void insertAppointment(Map<String, Object> parameters);

    void insertRevision(Map<String, Object> parameters);

    int advance(Map<String, Object> parameters);

    int advanceStatus(Map<String, Object> parameters);

    void insertHistory(Map<String, Object> parameters);

    void insertResult(Map<String, Object> parameters);

    Map<String, Object> findResult(
            @Param("tenantId") String tenantId,
            @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);
}
