package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/**
 * Technician Portal 整改闭环；所有写入均同时校验 Portal 网点上下文和 correction Task 实时责任。
 *
 * <p>M361：资料读写路径对已知 {@code TECHNICIAN_*} 复检客户端能力（与主 Evidence 路径同门禁）。</p>
 * <p>M362：列表/生命周期投影可带源 Task 冻结 Bundle 能力预检注解（软标注，不整表 422）。</p>
 */
public interface TechnicianCorrectionService {
    List<TechnicianCorrectionView> list(
            CurrentPrincipal principal, String correlationId, String context, String clientKind);

    TechnicianCorrectionView claim(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, long expectedVersion);

    TechnicianCorrectionView start(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, long expectedVersion);

    List<EvidenceSlotView> listSlots(
            CurrentPrincipal principal, String correlationId, String context,
            String clientKind, UUID correctionCaseId);

    List<EvidenceItemView> listItems(
            CurrentPrincipal principal, String correlationId, String context,
            String clientKind, UUID correctionCaseId);

    EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, UUID slotId,
            TechnicianBeginCorrectionEvidenceUploadCommand command);

    EvidenceItemView finalizeUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, UUID slotId, UUID uploadSessionId,
            String actualSha256, String finalizeCommandId);

    EvidenceSetSnapshotView createSnapshot(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, List<UUID> memberRevisionIds);

    TechnicianCorrectionView resubmit(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, UUID evidenceSetSnapshotId);
}
