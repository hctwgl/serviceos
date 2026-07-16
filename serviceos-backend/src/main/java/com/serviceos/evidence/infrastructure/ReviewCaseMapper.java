package com.serviceos.evidence.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
interface ReviewCaseMapper {
    void insertCase(Map<String, Object> values);

    int markDecided(
            @Param("tenantId") String tenantId,
            @Param("reviewCaseId") String reviewCaseId,
            @Param("expectedStatus") String expectedStatus,
            @Param("status") String status,
            @Param("decidedAt") Object decidedAt);

    int markReopened(
            @Param("tenantId") String tenantId,
            @Param("reviewCaseId") String reviewCaseId,
            @Param("expectedStatus") String expectedStatus);

    void insertDecision(Map<String, Object> values);

    Map<String, Object> findCase(
            @Param("tenantId") String tenantId, @Param("reviewCaseId") String reviewCaseId);

    List<Map<String, Object>> listCasesByTask(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    Map<String, Object> findTimelineIdentity(
            @Param("tenantId") String tenantId, @Param("reviewCaseId") String reviewCaseId);

    String findActiveCaseIdBySnapshot(
            @Param("tenantId") String tenantId,
            @Param("snapshotId") String snapshotId,
            @Param("origin") String origin);

    String findClientCaseIdByExternalSubmissionRef(
            @Param("tenantId") String tenantId,
            @Param("externalSubmissionRef") String externalSubmissionRef);

    List<Map<String, Object>> listDecisions(
            @Param("tenantId") String tenantId, @Param("reviewCaseId") String reviewCaseId);

    Integer maxDecisionOrdinal(
            @Param("tenantId") String tenantId, @Param("reviewCaseId") String reviewCaseId);

    String findCommandResult(
            @Param("tenantId") String tenantId,
            @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);

    void saveCommandResult(Map<String, Object> values);
}
