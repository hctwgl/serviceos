package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceSlotView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Evidence 持久化端口；所有查询必须携带 tenant scope。 */
public interface EvidenceSlotRepository {
    void insert(EvidenceTaskResolution resolution);

    /** 单 Task resolution stream 的事务级串行锁，避免并发 submission 产生重复 generation。 */
    void lockResolutionStream(String tenantId, UUID taskId);

    Optional<EvidenceResolutionState> latestResolution(String tenantId, UUID taskId);

    boolean resolutionExists(String tenantId, UUID taskId);

    List<EvidenceSlotView> listSlots(String tenantId, UUID taskId);

    /** Portal 查询包含活动槽位与尚待处置的历史槽位；Snapshot 只能使用 listSlots 活动集合。 */
    List<EvidenceSlotView> listCurrentSlots(String tenantId, UUID taskId);

    Optional<UUID> latestResolutionId(String tenantId, UUID taskId);

    boolean hasPendingDisposition(String tenantId, UUID taskId);

    Optional<PendingEvidenceConditionDisposition> findPendingDisposition(
            String tenantId, UUID resolutionId, UUID slotId);

    void insertDisposition(EvidenceConditionDisposition disposition);

    Optional<EvidenceConditionDisposition> findDisposition(String tenantId, UUID memberId);

    Optional<EvidenceConditionDisposition> findDispositionById(String tenantId, UUID dispositionId);
}
