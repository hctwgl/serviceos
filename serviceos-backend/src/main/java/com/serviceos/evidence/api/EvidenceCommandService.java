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

    /**
     * M201：受控代补 Begin。绕过「主体 = Task.responsiblePrincipalId」，
     * 要求 {@code evidence.submitOnBehalf}；CaptureMetadata 由服务端写入 onBehalf 字段。
     */
    EvidenceUploadSessionView beginUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            BeginEvidenceUploadOnBehalfCommand command
    );

    EvidenceItemView finalizeUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            FinalizeEvidenceUploadCommand command
    );

    /**
     * M201：受控代补 Finalize。任务校验与鉴权同 beginUploadOnBehalf；
     * 仍要求上传会话 {@code createdBy} 等于当前主体。
     *
     * @param networkId 非空时按 NETWORK scope 校验 {@code evidence.submitOnBehalf}
     */
    EvidenceItemView finalizeUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            FinalizeEvidenceUploadCommand command,
            java.util.UUID networkId
    );

    EvidenceRevisionView invalidate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            InvalidateEvidenceRevisionCommand command
    );

    List<EvidenceItemView> listForTask(CurrentPrincipal principal, String correlationId, UUID taskId);

    EvidenceItemView get(CurrentPrincipal principal, String correlationId, UUID evidenceItemId);
}
