package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;

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

    void updateRevisionStatus(String tenantId, UUID revisionId, String status);

    void updateSlotStatus(String tenantId, UUID slotId, String status);

    Optional<EvidenceRevisionView> findRevisionByFileObjectId(String tenantId, UUID fileObjectId);

    Optional<EvidenceRevisionView> findRevisionByUploadSession(String tenantId, UUID uploadSessionId);
}
