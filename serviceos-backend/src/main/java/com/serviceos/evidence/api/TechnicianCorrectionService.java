package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** Technician Portal 整改闭环；所有写入均同时校验 Portal 网点上下文和 correction Task 实时责任。 */
public interface TechnicianCorrectionService {
    List<TechnicianCorrectionView> list(
            CurrentPrincipal principal, String correlationId, String context);

    TechnicianCorrectionView claim(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, long expectedVersion);

    TechnicianCorrectionView start(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, long expectedVersion);

    List<EvidenceSlotView> listSlots(
            CurrentPrincipal principal, String correlationId, String context, UUID correctionCaseId);

    List<EvidenceItemView> listItems(
            CurrentPrincipal principal, String correlationId, String context, UUID correctionCaseId);

    EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, UUID slotId,
            TechnicianBeginCorrectionEvidenceUploadCommand command);

    EvidenceItemView finalizeUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, UUID slotId, UUID uploadSessionId,
            String actualSha256, String finalizeCommandId);

    EvidenceSetSnapshotView createSnapshot(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, List<UUID> memberRevisionIds);

    TechnicianCorrectionView resubmit(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, UUID evidenceSetSnapshotId);
}
