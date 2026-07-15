package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** EvidenceItem / Revision / 上传绑定持久化端口。 */
public interface EvidenceItemRepository {
    Optional<EvidenceSlotView> findSlot(String tenantId, UUID taskId, UUID slotId);

    EvidenceSlotView lockSlot(String tenantId, UUID slotId);

    void insertUploadBinding(EvidenceUploadBinding binding);

    Optional<EvidenceUploadBinding> findUploadBinding(String tenantId, UUID uploadSessionId);

    Optional<EvidenceUploadBinding> findUploadBindingByFileId(String tenantId, UUID fileId);

    void markUploadFinalized(String tenantId, UUID uploadSessionId);

    Optional<EvidenceItemView> findItem(String tenantId, UUID evidenceItemId);

    List<EvidenceItemView> listItems(String tenantId, UUID taskId);

    Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey);

    void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId);

    int nextItemOrdinal(String tenantId, UUID slotId);

    int countItems(String tenantId, UUID slotId);

    int countCountingItems(String tenantId, UUID slotId);

    void insertItem(String tenantId, EvidenceItemView item);

    int nextRevisionNumber(String tenantId, UUID evidenceItemId);

    void insertRevision(String tenantId, EvidenceRevisionView revision);

    int updateRevisionStatus(String tenantId, UUID revisionId, String expectedStatus, String status);

    int invalidateRevision(
            String tenantId,
            UUID revisionId,
            String reasonCode,
            String approvalRef,
            String invalidatedBy,
            Instant invalidatedAt
    );

    void updateSlotStatus(String tenantId, UUID slotId, String status);

    Optional<EvidenceRevisionView> findRevision(String tenantId, UUID revisionId);

    Optional<EvidenceRevisionView> findRevisionByFileObjectId(String tenantId, UUID fileObjectId);

    Optional<EvidenceRevisionView> findRevisionByUploadSession(String tenantId, UUID uploadSessionId);

    List<EvidenceRevisionView> findRevisionsByIds(String tenantId, UUID taskId, List<UUID> revisionIds);

    /** 人工 INVALIDATE 处置检查并处理该历史槽位仍在计数的精确资料版本。 */
    List<EvidenceRevisionView> listCountingRevisionsForSlot(String tenantId, UUID slotId);

    boolean existsOtherCountingDigest(
            String tenantId, UUID projectId, String contentDigest, UUID excludeRevisionId);

    List<EvidenceValidationView> listValidations(String tenantId, UUID revisionId);

    void insertValidation(String tenantId, UUID projectId, UUID taskId, UUID slotId,
                          UUID evidenceItemId, EvidenceValidationView validation);
}
