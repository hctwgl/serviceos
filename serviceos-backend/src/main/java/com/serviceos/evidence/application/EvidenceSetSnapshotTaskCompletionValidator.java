package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCompletionValidator;
import com.serviceos.task.api.InputVersionRef;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * EvidenceSlot 非空时的完成门禁：
 * <ul>
 *   <li>无 formRef：resultRef 必须是精确 TASK_SUBMISSION Snapshot（M41）；</li>
 *   <li>有 formRef：inputVersionRefs 必须同时冻结 FormSubmission 与 Snapshot（M43）。</li>
 * </ul>
 */
@Component
final class EvidenceSetSnapshotTaskCompletionValidator implements HumanTaskCompletionValidator {
    private static final String REFERENCE_PREFIX = "evidence-set-snapshot://";
    private static final String FORM_PREFIX = "form-submission://";

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
        if (!slots.resolutionExists(principal.tenantId(), command.taskId())) {
            return;
        }
        // 即便最新活动集合为空，尚未处置的历史资料也必须阻断完成，避免把条件变化解释成自动删除。
        if (slots.hasPendingDisposition(principal.tenantId(), command.taskId())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Task 存在未处置的资料条件变化，不能完成任务");
        }
        List<EvidenceSlotView> taskSlots = slots.listSlots(principal.tenantId(), command.taskId());
        if (taskSlots.isEmpty()) {
            return;
        }

        if (task.formRef() == null) {
            validateEvidenceOnly(principal, task, command);
            return;
        }
        validateDualInput(principal, task, command);
    }

    private void validateEvidenceOnly(
            CurrentPrincipal principal, TaskFulfillmentContext task, CompleteHumanTaskCommand command
    ) {
        if (!command.inputVersionRefs().isEmpty()) {
            throw refsInvalid("Evidence-only task must not provide inputVersionRefs");
        }
        UUID snapshotId = snapshotId(command.resultRef());
        requireMatchingSnapshot(principal, task, command.taskId(), snapshotId, command.resultDigest());
    }

    private void validateDualInput(
            CurrentPrincipal principal, TaskFulfillmentContext task, CompleteHumanTaskCommand command
    ) {
        List<InputVersionRef> refs = command.inputVersionRefs();
        if (refs.size() != 2) {
            throw refsInvalid("Dual form+evidence completion requires exactly two inputVersionRefs");
        }
        InputVersionRef formRef = uniqueKind(refs, InputVersionRef.FORM_SUBMISSION);
        InputVersionRef evidenceRef = uniqueKind(refs, InputVersionRef.EVIDENCE_SET_SNAPSHOT);
        if (!command.resultRef().equals(formRef.ref())
                || !command.resultDigest().equals(formRef.digest())
                || !formRef.ref().startsWith(FORM_PREFIX)) {
            throw refsInvalid("FORM_SUBMISSION inputVersionRef must match resultRef and resultDigest");
        }
        UUID snapshotId = snapshotId(evidenceRef.ref());
        requireMatchingSnapshot(principal, task, command.taskId(), snapshotId, evidenceRef.digest());
    }

    private void requireMatchingSnapshot(
            CurrentPrincipal principal,
            TaskFulfillmentContext task,
            UUID taskId,
            UUID snapshotId,
            String digest
    ) {
        EvidenceSetSnapshotView snapshot = snapshots.find(principal.tenantId(), snapshotId)
                .orElseThrow(EvidenceSetSnapshotTaskCompletionValidator::notValidated);
        if (!"TASK_SUBMISSION".equals(snapshot.purpose())
                || !taskId.equals(snapshot.taskId())
                || !task.projectId().equals(snapshot.projectId())
                || !snapshot.contentDigest().equals(digest)
                // Snapshot 只能冻结当前解析代次；表单再次提交后，旧 Snapshot 即使摘要匹配也必须失效关闭。
                || !slots.latestResolutionId(principal.tenantId(), taskId)
                .filter(snapshot.resolutionId()::equals).isPresent()) {
            throw notValidated();
        }
    }

    private static InputVersionRef uniqueKind(List<InputVersionRef> refs, String kind) {
        List<InputVersionRef> matches = refs.stream().filter(ref -> kind.equals(ref.kind())).toList();
        if (matches.size() != 1) {
            throw refsInvalid("inputVersionRefs must contain exactly one " + kind);
        }
        return matches.getFirst();
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

    private static BusinessProblem refsInvalid(String message) {
        return new BusinessProblem(ProblemCode.TASK_INPUT_REFS_INVALID, message);
    }
}
