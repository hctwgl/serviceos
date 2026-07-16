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

    int markReopened(String tenantId, UUID reviewCaseId, String expectedStatus);

    void insertDecision(String tenantId, UUID projectId, ReviewDecisionView decision);

    Optional<ReviewCaseView> find(String tenantId, UUID reviewCaseId);

    /** 按 Task 列出案例；调用方负责加载决定链。 */
    List<ReviewCaseView> listByTask(String tenantId, UUID taskId);

    /** 时间线专用：只读 reviewCaseId/projectId/taskId，不加载决定。 */
    Optional<ReviewCaseTimelineIdentity> findTimelineIdentity(String tenantId, UUID reviewCaseId);

    Optional<UUID> findActiveBySnapshot(String tenantId, UUID snapshotId, String origin);

    Optional<UUID> findClientByExternalSubmissionRef(String tenantId, String externalSubmissionRef);

    Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey);

    void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId);

    int nextDecisionOrdinal(String tenantId, UUID reviewCaseId);
}
