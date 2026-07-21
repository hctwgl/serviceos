package com.serviceos.evidence.application;

import com.serviceos.evidence.api.CorrectionCaseQueueItem;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.CorrectionResubmissionView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** CorrectionCase / 补传轮次持久化端口。 */
public interface CorrectionCaseRepository {
    void insertCase(String tenantId, CorrectionCaseView correctionCase);

    int linkCorrectionTask(String tenantId, UUID correctionCaseId, UUID correctionTaskId);

    int markInProgress(String tenantId, UUID correctionCaseId, String expectedStatus);

    void insertResubmission(String tenantId, UUID projectId, CorrectionResubmissionView resubmission);

    int markResubmitted(
            String tenantId,
            UUID correctionCaseId,
            String expectedStatus,
            UUID latestSnapshotId,
            Instant updatedAt
    );

    int markClosed(
            String tenantId,
            UUID correctionCaseId,
            String expectedStatus,
            String closedBy,
            Instant closedAt
    );

    int markWaived(
            String tenantId,
            UUID correctionCaseId,
            String expectedStatus,
            String waivedBy,
            Instant waivedAt,
            String approvalRef,
            String note
    );

    Optional<CorrectionCaseView> find(String tenantId, UUID correctionCaseId);

    Optional<CorrectionCaseView> findByCorrectionTaskId(String tenantId, UUID correctionTaskId);

    List<CorrectionCaseView> listByTask(String tenantId, UUID taskId);

    List<CorrectionCaseQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            UUID taskId,
            UUID sourceReviewCaseId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize);

    /** M452：与 findQueuePage 同筛选口径的精确 COUNT（无 cursor）。 */
    int countQueue(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            UUID taskId,
            UUID sourceReviewCaseId);

    Optional<UUID> findBySourceDecision(String tenantId, UUID reviewDecisionId);

    Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey);

    void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId);

    int nextResubmissionOrdinal(String tenantId, UUID correctionCaseId);
}
