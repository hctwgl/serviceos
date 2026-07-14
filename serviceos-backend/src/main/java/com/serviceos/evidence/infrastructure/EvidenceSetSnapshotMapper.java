package com.serviceos.evidence.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
interface EvidenceSetSnapshotMapper {
    void insertSnapshot(Map<String, Object> values);

    void insertMember(Map<String, Object> values);

    Map<String, Object> findSnapshot(
            @Param("tenantId") String tenantId, @Param("snapshotId") String snapshotId);

    List<Map<String, Object>> listMembers(
            @Param("tenantId") String tenantId, @Param("snapshotId") String snapshotId);

    String findCommandResult(
            @Param("tenantId") String tenantId,
            @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);

    void saveCommandResult(Map<String, Object> values);
}
