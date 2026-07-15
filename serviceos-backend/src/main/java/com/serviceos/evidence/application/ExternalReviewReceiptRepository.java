package com.serviceos.evidence.application;

import com.serviceos.evidence.api.ExternalReviewReceiptView;

import java.util.Optional;
import java.util.UUID;

/** ExternalReviewReceipt 持久化端口。 */
public interface ExternalReviewReceiptRepository {
    void insert(String tenantId, ExternalReviewReceiptView receipt);

    Optional<ExternalReviewReceiptView> find(String tenantId, UUID receiptId);

    Optional<UUID> findByCanonicalMessage(String tenantId, String canonicalMessageId);

    Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey);

    void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId);
}
