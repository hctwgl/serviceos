package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** CorrectionCase 命令与查询端口。 */
public interface CorrectionCaseService {
    CorrectionCaseView get(CurrentPrincipal principal, String correlationId, UUID correctionCaseId);

    CorrectionCaseView resubmit(
            CurrentPrincipal principal, CommandMetadata metadata, ResubmitCorrectionCaseCommand command);

    CorrectionCaseView close(
            CurrentPrincipal principal, CommandMetadata metadata, CloseCorrectionCaseCommand command);

    /** 由 Review REJECTED 同事务调用；不单独对外暴露。 */
    CorrectionCaseView openFromRejectedDecision(
            String tenantId,
            String actorId,
            String correlationId,
            String causationId,
            UUID projectId,
            UUID taskId,
            UUID reviewCaseId,
            UUID reviewDecisionId,
            UUID evidenceSetSnapshotId,
            String snapshotContentDigest,
            List<String> reasonCodes
    );
}
