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

    /**
     * Network Portal 代补创建资料快照：NETWORK scope {@code evidence.submitOnBehalf}；
     * 冻结整改源 Task 当前可提交集合，不要求师傅 correction Task 认领。
     */
    EvidenceSetSnapshotView createOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID correctionCaseId,
            java.util.List<UUID> memberRevisionIds,
            UUID networkId
    );

    EvidenceSetSnapshotView get(
            CurrentPrincipal principal,
            String correlationId,
            UUID snapshotId
    );
}
