package com.serviceos.evidence.application;

import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewDecisionView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** ReviewCase / ReviewDecision 持久化端口。 */
public interface ReviewCaseRepository {
    void insertCase(String tenantId, ReviewCaseView reviewCase);

    int markDecided(
            String tenantId,
            UUID reviewCaseId,
            String expectedStatus,
            String status,
            Instant decidedAt
    );

    void insertDecision(String tenantId, UUID projectId, ReviewDecisionView decision);

    Optional<ReviewCaseView> find(String tenantId, UUID reviewCaseId);

    Optional<UUID> findBySnapshot(String tenantId, UUID snapshotId);

    Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey);

    void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId);

    int nextDecisionOrdinal(String tenantId, UUID reviewCaseId);
}
