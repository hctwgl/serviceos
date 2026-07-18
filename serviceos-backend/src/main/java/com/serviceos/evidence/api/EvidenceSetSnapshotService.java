package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.UUID;

/** EvidenceSetSnapshot 命令与查询端口。 */
public interface EvidenceSetSnapshotService {
    EvidenceSetSnapshotView create(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateEvidenceSetSnapshotCommand command
    );

    EvidenceSetSnapshotView createForCorrection(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID correctionCaseId,
            UUID correctionTaskId,
            UUID sourceTaskId,
            java.util.List<UUID> memberRevisionIds
    );

    EvidenceSetSnapshotView get(
            CurrentPrincipal principal,
            String correlationId,
            UUID snapshotId
    );
}
