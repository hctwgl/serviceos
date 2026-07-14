package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/** EvidenceItem / Revision 命令与查询端口。 */
public interface EvidenceCommandService {
    EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            BeginEvidenceUploadCommand command
    );

    EvidenceItemView finalizeUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            FinalizeEvidenceUploadCommand command
    );

    List<EvidenceItemView> listForTask(CurrentPrincipal principal, String correlationId, UUID taskId);

    EvidenceItemView get(CurrentPrincipal principal, String correlationId, UUID evidenceItemId);
}
