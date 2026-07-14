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

    int countResolution(@Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listSlots(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);
}
