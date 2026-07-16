package com.serviceos.readmodel.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
interface WorkOrderTimelineMapper {
    int append(Map<String, Object> entry);

    List<Map<String, Object>> findPage(
            @Param("tenantId") String tenantId,
            @Param("workOrderId") UUID workOrderId,
            @Param("rebuildGeneration") int rebuildGeneration,
            @Param("beforeOccurredAt") Instant beforeOccurredAt,
            @Param("beforeEntryId") UUID beforeEntryId,
            @Param("fetchSize") int fetchSize);

    Instant findLastProjectedAt(
            @Param("tenantId") String tenantId,
            @Param("workOrderId") UUID workOrderId,
            @Param("rebuildGeneration") int rebuildGeneration);

    long countGeneration(@Param("rebuildGeneration") int rebuildGeneration);
}
