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
 */
public interface NetworkPortalEvidenceService {
    EvidenceUploadSessionView beginUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            UUID slotId,
            BeginEvidenceUploadOnBehalfCommand command
    );

    EvidenceItemView finalizeUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            UUID slotId,
            UUID uploadSessionId,
            FinalizeEvidenceUploadCommand command
    );

    CorrectionCaseView resubmit(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID correctionCaseId,
            UUID evidenceSetSnapshotId
    );
}
