package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCompletionValidator;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 无 formRef 且已解析出 EvidenceSlot 的 HUMAN Task，完成时必须引用精确的 TASK_SUBMISSION Snapshot。
 * 表单+资料双引用完成条件留给后续 inputVersionRefs；本校验器在 formRef 非空时直接跳过。
 */
@Component
final class EvidenceSetSnapshotTaskCompletionValidator implements HumanTaskCompletionValidator {
    private static final String REFERENCE_PREFIX = "evidence-set-snapshot://";

    private final EvidenceSetSnapshotRepository snapshots;
    private final EvidenceSlotRepository slots;
    private final TaskFulfillmentContextService tasks;

    EvidenceSetSnapshotTaskCompletionValidator(
            EvidenceSetSnapshotRepository snapshots,
            EvidenceSlotRepository slots,
            TaskFulfillmentContextService tasks
    ) {
        this.snapshots = snapshots;
        this.slots = slots;
        this.tasks = tasks;
    }

    @Override
    public void validate(CurrentPrincipal principal, CompleteHumanTaskCommand command) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), command.taskId())
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        if (task.formRef() != null) {
            return;
        }
        if (!slots.resolutionExists(principal.tenantId(), command.taskId())) {
            return;
        }
        List<EvidenceSlotView> taskSlots = slots.listSlots(principal.tenantId(), command.taskId());
        if (taskSlots.isEmpty()) {
            return;
        }

        UUID snapshotId = snapshotId(command.resultRef());
        EvidenceSetSnapshotView snapshot = snapshots.find(principal.tenantId(), snapshotId)
                .orElseThrow(EvidenceSetSnapshotTaskCompletionValidator::notValidated);
        if (!"TASK_SUBMISSION".equals(snapshot.purpose())
                || !command.taskId().equals(snapshot.taskId())
                || !task.projectId().equals(snapshot.projectId())
                || !snapshot.contentDigest().equals(command.resultDigest())) {
            throw notValidated();
        }
    }

    private static UUID snapshotId(String resultRef) {
        if (!resultRef.startsWith(REFERENCE_PREFIX)) {
            throw notValidated();
        }
        try {
            return UUID.fromString(resultRef.substring(REFERENCE_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw notValidated();
        }
    }

    private static BusinessProblem notValidated() {
        return new BusinessProblem(ProblemCode.EVIDENCE_SET_NOT_VALIDATED,
                "Task completion requires an exact TASK_SUBMISSION EvidenceSetSnapshot reference and content digest");
    }
}
