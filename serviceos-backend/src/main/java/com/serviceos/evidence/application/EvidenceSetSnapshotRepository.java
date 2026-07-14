package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceSetSnapshotView;

import java.util.Optional;
import java.util.UUID;

/** EvidenceSetSnapshot 持久化端口。 */
public interface EvidenceSetSnapshotRepository {
    void insert(String tenantId, EvidenceSetSnapshotView snapshot);

    Optional<EvidenceSetSnapshotView> find(String tenantId, UUID snapshotId);

    Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey);

    void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId);
}
