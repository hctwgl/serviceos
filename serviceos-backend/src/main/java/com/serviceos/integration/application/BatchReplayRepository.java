package com.serviceos.integration.application;

import com.serviceos.integration.api.BatchReplayRequestView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchReplayRepository {
    void insert(NewBatch batch, List<NewItem> items);

    Optional<BatchReplayRequestView> find(String tenantId, UUID batchId);

    void markDecision(
            String tenantId,
            UUID batchId,
            String status,
            String decision,
            String decidedBy,
            String decisionNote,
            Instant decidedAt);

    void markItemScheduled(
            String tenantId,
            UUID batchId,
            UUID deliveryId,
            UUID singleReplayRequestId);

    void markItemFailed(
            String tenantId,
            UUID batchId,
            UUID deliveryId,
            String errorCode);

    void markCompleted(String tenantId, UUID batchId);

    record NewBatch(
            UUID batchId,
            String tenantId,
            String mode,
            String status,
            String reason,
            String approvalRef,
            String requestedBy,
            int maxItems,
            Instant createdAt
    ) {
    }

    record NewItem(
            UUID batchId,
            UUID deliveryId,
            String tenantId,
            UUID projectId,
            String eligibility,
            String ineligibilityCode,
            Long expectedDeliveryVersion,
            String itemStatus
    ) {
    }
}
