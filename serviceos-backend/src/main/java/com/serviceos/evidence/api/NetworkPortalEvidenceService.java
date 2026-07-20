package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/**
 * Network Portal 资料代补编排边界。
 * <p>
 * 能力：NETWORK scope {@code evidence.submitOnBehalf}；委托
 * {@link EvidenceCommandService#beginUploadOnBehalf}/{@link EvidenceCommandService#finalizeUploadOnBehalf}
 * 与 {@link CorrectionCaseService#resubmit}。
 * <p>
 * M368 / ADR-089：写路径要求 {@code X-ServiceOS-Client-Kind=NETWORK_WEB}，并按网点端能力目录
 * 校验源 Task EVIDENCE 槽位；不兼容返回 {@code CLIENT_CAPABILITY_UNSUPPORTED}。
 */
public interface NetworkPortalEvidenceService {
    EvidenceUploadSessionView beginUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID taskId,
            UUID slotId,
            BeginEvidenceUploadOnBehalfCommand command
    );

    EvidenceItemView finalizeUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID taskId,
            UUID slotId,
            UUID uploadSessionId,
            FinalizeEvidenceUploadCommand command
    );

    EvidenceSetSnapshotView createSnapshotOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID correctionCaseId,
            java.util.List<UUID> memberRevisionIds
    );

    CorrectionCaseView resubmit(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID correctionCaseId,
            UUID evidenceSetSnapshotId
    );
}
