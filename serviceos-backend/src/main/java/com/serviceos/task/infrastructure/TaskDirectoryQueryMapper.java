package com.serviceos.task.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
interface TaskDirectoryQueryMapper {
    List<Map<String, Object>> findPage(
            @Param("tenantId") String tenantId,
            @Param("tenantWide") boolean tenantWide,
            @Param("projectIds") List<String> projectIds,
            @Param("projectId") UUID projectId,
            @Param("taskKind") String taskKind,
            @Param("status") String status,
            @Param("assigneeId") String assigneeId,
            @Param("cursorPriority") Integer cursorPriority,
            @Param("cursorNextRunAt") Instant cursorNextRunAt,
            @Param("cursorCreatedAt") Instant cursorCreatedAt,
            @Param("cursorId") UUID cursorId,
            @Param("fetchSize") int fetchSize);

    Map<String, Object> findDetail(
            @Param("tenantId") String tenantId,
            @Param("taskId") UUID taskId);

    Map<String, Object> findAllowedActionState(
            @Param("tenantId") String tenantId,
            @Param("taskId") UUID taskId,
            @Param("principalId") String principalId);

    List<Map<String, Object>> findExecutionAttempts(
            @Param("tenantId") String tenantId,
            @Param("taskId") UUID taskId,
            @Param("beforeAttemptNo") Integer beforeAttemptNo,
            @Param("fetchSize") int fetchSize);

    Map<String, Object> findTimelineContext(
            @Param("tenantId") String tenantId,
            @Param("taskId") UUID taskId);
}
