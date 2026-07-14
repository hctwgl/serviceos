package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceSlotView;

import java.util.List;
import java.util.UUID;

/** Evidence 持久化端口；所有查询必须携带 tenant scope。 */
public interface EvidenceSlotRepository {
    void insert(EvidenceTaskResolution resolution);

    boolean resolutionExists(String tenantId, UUID taskId);

    List<EvidenceSlotView> listSlots(String tenantId, UUID taskId);
}
