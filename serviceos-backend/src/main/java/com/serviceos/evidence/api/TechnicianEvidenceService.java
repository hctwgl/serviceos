package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.task.api.HumanTaskCommandReceipt;

import java.util.List;
import java.util.UUID;

/** Technician Portal 当前责任任务的在线资料适配端口。 */
public interface TechnicianEvidenceService {
    List<EvidenceSlotView> listSlots(
            CurrentPrincipal principal,
            String correlationId,
            String technicianContextHeader,
            String clientKind,
            UUID taskId);

    List<EvidenceItemView> listItems(
            CurrentPrincipal principal,
            String correlationId,
            String technicianContextHeader,
            String clientKind,
            UUID taskId);

    EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            String clientKind,
            TechnicianBeginEvidenceUploadCommand command);

    EvidenceItemView finalizeUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            String clientKind,
            FinalizeEvidenceUploadCommand command);

    EvidenceSetSnapshotView createTaskSubmissionSnapshot(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            String clientKind,
            UUID taskId,
            List<UUID> memberRevisionIds);

    HumanTaskCommandReceipt completeTask(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            String clientKind,
            TechnicianCompleteTaskCommand command);
}
