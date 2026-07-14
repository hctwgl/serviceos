package com.serviceos.evidence.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
interface CorrectionCaseMapper {
    void insertCase(Map<String, Object> values);

    int linkCorrectionTask(
            @Param("tenantId") String tenantId,
            @Param("correctionCaseId") String correctionCaseId,
            @Param("correctionTaskId") String correctionTaskId);

    int markInProgress(
            @Param("tenantId") String tenantId,
            @Param("correctionCaseId") String correctionCaseId,
            @Param("expectedStatus") String expectedStatus);

    void insertResubmission(Map<String, Object> values);

    int markResubmitted(
            @Param("tenantId") String tenantId,
            @Param("correctionCaseId") String correctionCaseId,
            @Param("expectedStatus") String expectedStatus,
            @Param("latestSnapshotId") String latestSnapshotId);

    int markClosed(
            @Param("tenantId") String tenantId,
            @Param("correctionCaseId") String correctionCaseId,
            @Param("expectedStatus") String expectedStatus,
            @Param("closedBy") String closedBy,
            @Param("closedAt") Object closedAt);

    int markWaived(
            @Param("tenantId") String tenantId,
            @Param("correctionCaseId") String correctionCaseId,
            @Param("expectedStatus") String expectedStatus,
            @Param("waivedBy") String waivedBy,
            @Param("waivedAt") Object waivedAt,
            @Param("approvalRef") String approvalRef,
            @Param("note") String note);

    Map<String, Object> findCase(
            @Param("tenantId") String tenantId, @Param("correctionCaseId") String correctionCaseId);

    String findCaseIdBySourceDecision(
            @Param("tenantId") String tenantId, @Param("reviewDecisionId") String reviewDecisionId);

    List<Map<String, Object>> listResubmissions(
            @Param("tenantId") String tenantId, @Param("correctionCaseId") String correctionCaseId);

    Integer maxResubmissionOrdinal(
            @Param("tenantId") String tenantId, @Param("correctionCaseId") String correctionCaseId);

    String findCommandResult(
            @Param("tenantId") String tenantId,
            @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);

    void saveCommandResult(Map<String, Object> values);
}
