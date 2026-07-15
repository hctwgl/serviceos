package com.serviceos.evidence.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** EvidenceSlot MyBatis SQL 边界，仅供同模块 Repository 适配器使用。 */
@Mapper
interface EvidenceSlotMapper {
    void insertResolution(Map<String, Object> values);

    void insertSlots(@Param("slots") List<Map<String, Object>> slots);

    void insertMembers(@Param("members") List<Map<String, Object>> members);

    int lockResolutionStream(@Param("streamKey") String streamKey);

    int countResolution(@Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listSlots(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listCurrentSlots(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    Map<String, Object> findLatestResolution(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listResolutionMembers(
            @Param("tenantId") String tenantId, @Param("resolutionId") String resolutionId);

    int countPendingDisposition(@Param("tenantId") String tenantId, @Param("taskId") String taskId);

    Map<String, Object> findPendingDisposition(
            @Param("tenantId") String tenantId,
            @Param("resolutionId") String resolutionId,
            @Param("slotId") String slotId);

    void insertDisposition(Map<String, Object> values);

    Map<String, Object> findDisposition(
            @Param("tenantId") String tenantId, @Param("memberId") String memberId);

    Map<String, Object> findDispositionById(
            @Param("tenantId") String tenantId, @Param("dispositionId") String dispositionId);
}
