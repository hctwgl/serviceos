package com.serviceos.evidence.application;

import com.serviceos.evidence.api.ReviewCaseView;
import com.serviceos.evidence.api.ReviewCaseQueueItem;
import com.serviceos.evidence.api.ReviewDecisionView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
// List already imported for queue/target decisions

/** ReviewCase / ReviewDecision 持久化端口。 */
public interface ReviewCaseRepository {
    void insertCase(String tenantId, ReviewCaseView reviewCase);

    int markDecided(
            String tenantId,
            UUID reviewCaseId,
            String expectedStatus,
            long expectedAggregateVersion,
            String status,
            Instant decidedAt
    );

    int markReopened(String tenantId, UUID reviewCaseId, String expectedStatus);

    void insertDecision(String tenantId, UUID projectId, ReviewDecisionView decision);

    void insertTargetDecision(
            String tenantId,
            UUID projectId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            String targetType,
            UUID targetId,
            int targetVersion,
            String decision,
            List<String> reasonCodes,
            String note,
            Instant createdAt
    );

    Optional<ReviewCaseView> find(String tenantId, UUID reviewCaseId);

    /** 按 Task 列出案例；调用方负责加载决定链。 */
    List<ReviewCaseView> listByTask(String tenantId, UUID taskId);

    List<ReviewCaseQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            String origin,
            UUID taskId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize);

    /** M452：与 findQueuePage 同筛选口径的精确 COUNT（无 cursor）。 */
    int countQueue(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            String origin,
            UUID taskId);

    /** 时间线专用：只读 reviewCaseId/projectId/taskId，不加载决定。 */
    Optional<ReviewCaseTimelineIdentity> findTimelineIdentity(String tenantId, UUID reviewCaseId);

    Optional<UUID> findActiveBySnapshot(String tenantId, UUID snapshotId, String origin);

    Optional<UUID> findClientByExternalSubmissionRef(String tenantId, String externalSubmissionRef);

    Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey);

    void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId);

    int nextDecisionOrdinal(String tenantId, UUID reviewCaseId);
}
