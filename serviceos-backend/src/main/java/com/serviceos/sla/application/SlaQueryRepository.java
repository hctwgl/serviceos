package com.serviceos.sla.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** SLA 查询持久化端口；排序字段是不可变 deadline 与实例 ID，保证翻页稳定。 */
public interface SlaQueryRepository {
    List<SlaStoredInstance> findPage(
            String tenantId, boolean tenantWide, List<UUID> projectIds, UUID workOrderId, String status,
            Instant cursorDeadlineAt, UUID cursorId, int fetchSize);

    Optional<SlaStoredInstance> findById(String tenantId, UUID slaInstanceId);

    List<SlaStoredSegment> findSegments(String tenantId, UUID slaInstanceId);

    List<SlaStoredMilestone> findMilestones(String tenantId, UUID slaInstanceId);
}
