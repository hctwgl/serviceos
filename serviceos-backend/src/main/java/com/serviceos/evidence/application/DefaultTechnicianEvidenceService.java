package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.evidence.api.BeginEvidenceUploadCommand;
import com.serviceos.evidence.api.CreateEvidenceSetSnapshotCommand;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.TechnicianBeginEvidenceUploadCommand;
import com.serviceos.evidence.api.TechnicianCompleteTaskCommand;
import com.serviceos.evidence.api.TechnicianEvidenceService;
import com.serviceos.forms.api.FormSubmissionService;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCommandReceipt;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.InputVersionRef;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Technician Portal 资料适配层；安全文件与不可变 Revision 仍由既有 Evidence/Files 服务维护。 */
@Service
final class DefaultTechnicianEvidenceService implements TechnicianEvidenceService {
    private static final String CONTEXT_PREFIX = "TECHNICIAN|NETWORK|";
    private static final String TASK_READ_ASSIGNED = "task.readAssigned";
    private static final Set<String> ONLINE_SOURCES = Set.of("CAMERA", "GALLERY", "FILE");

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final TechnicianActiveAssignmentQuery assignments;
    private final TaskFulfillmentContextService tasks;
    private final EvidenceSlotQueryService slots;
    private final EvidenceCommandService evidence;
    private final EvidenceSetSnapshotService snapshots;
    private final FormSubmissionService formSubmissions;
    private final HumanTaskCommandService humanTasks;
    private final AuthorizationService authorization;
    private final ClientCapabilityRuntimeGate clientCapabilityRuntimeGate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultTechnicianEvidenceService(
            PrincipalNetworkAffiliationQuery affiliations,
            TechnicianActiveAssignmentQuery assignments,
            TaskFulfillmentContextService tasks,
            EvidenceSlotQueryService slots,
            EvidenceCommandService evidence,
            EvidenceSetSnapshotService snapshots,
            FormSubmissionService formSubmissions,
            HumanTaskCommandService humanTasks,
            AuthorizationService authorization,
            ClientCapabilityRuntimeGate clientCapabilityRuntimeGate,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.assignments = assignments;
        this.tasks = tasks;
        this.slots = slots;
        this.evidence = evidence;
        this.snapshots = snapshots;
        this.formSubmissions = formSubmissions;
        this.humanTasks = humanTasks;
        this.authorization = authorization;
        this.clientCapabilityRuntimeGate = clientCapabilityRuntimeGate;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceSlotView> listSlots(
            CurrentPrincipal principal,
            String correlationId,
            String context,
            String clientKind,
            UUID taskId
    ) {
        requireCurrentTask(principal, correlationId, context, taskId);
        List<EvidenceSlotView> resolved = slots.listForTask(principal, correlationId, taskId);
        requireClientCompatible(clientKind, resolved);
        return resolved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItemView> listItems(
            CurrentPrincipal principal,
            String correlationId,
            String context,
            String clientKind,
            UUID taskId
    ) {
        requireCurrentTask(principal, correlationId, context, taskId);
        requireClientCompatible(clientKind, slots.listForTask(principal, correlationId, taskId));
        return evidence.listForTask(principal, correlationId, taskId);
    }

    @Override
    public EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String context,
            String clientKind,
            TechnicianBeginEvidenceUploadCommand command
    ) {
        requireCurrentTask(principal, metadata.correlationId(), context, command.taskId());
        requireClientCompatible(
                clientKind,
                slots.listForTask(principal, metadata.correlationId(), command.taskId()));
        return evidence.beginUpload(principal, metadata, new BeginEvidenceUploadCommand(
                command.taskId(), command.slotId(), command.evidenceItemId(),
                command.originalFileName(), command.declaredMimeType(), command.expectedSize(),
                command.expectedSha256(), captureMetadata(command)));
    }

    @Override
    public EvidenceItemView finalizeUpload(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String context,
            String clientKind,
            FinalizeEvidenceUploadCommand command
    ) {
        requireCurrentTask(principal, metadata.correlationId(), context, command.taskId());
        requireClientCompatible(
                clientKind,
                slots.listForTask(principal, metadata.correlationId(), command.taskId()));
        return evidence.finalizeUpload(principal, metadata, command);
    }

    @Override
    @Transactional
    public EvidenceSetSnapshotView createTaskSubmissionSnapshot(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String context,
            String clientKind,
            UUID taskId,
            List<UUID> memberRevisionIds
    ) {
        requireCurrentTask(principal, metadata.correlationId(), context, taskId);
        requireClientCompatible(
                clientKind, slots.listForTask(principal, metadata.correlationId(), taskId));
        return snapshots.create(principal, metadata,
                new CreateEvidenceSetSnapshotCommand(taskId, "TASK_SUBMISSION", memberRevisionIds));
    }

    @Override
    @Transactional
    public HumanTaskCommandReceipt completeTask(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String context,
            String clientKind,
            TechnicianCompleteTaskCommand command
    ) {
        TaskFulfillmentContext task = requireCurrentTask(
                principal, metadata.correlationId(), context, command.taskId());
        requireClientCompatible(
                clientKind,
                slots.listForTask(principal, metadata.correlationId(), command.taskId()));
        EvidenceSetSnapshotView snapshot = snapshots.get(
                principal, metadata.correlationId(), command.evidenceSetSnapshotId());
        if (!task.taskId().equals(snapshot.taskId())) {
            throw new BusinessProblem(ProblemCode.EVIDENCE_SET_NOT_VALIDATED,
                    "资料快照不属于当前任务");
        }

        String snapshotRef = "evidence-set-snapshot://" + snapshot.evidenceSetSnapshotId();
        CompleteHumanTaskCommand completion;
        if (task.formRef() == null) {
            if (command.formSubmissionId() != null) {
                throw new BusinessProblem(ProblemCode.TASK_INPUT_REFS_INVALID,
                        "无表单任务不能提交 FormSubmission 引用");
            }
            completion = new CompleteHumanTaskCommand(
                    task.taskId(), command.expectedTaskVersion(), snapshotRef, snapshot.contentDigest());
        } else {
            if (command.formSubmissionId() == null) {
                throw new BusinessProblem(ProblemCode.TASK_INPUT_REFS_INVALID,
                        "表单与资料双输入任务缺少 FormSubmission");
            }
            FormSubmissionView submission = formSubmissions.get(
                    principal, metadata.correlationId(), command.formSubmissionId());
            if (!task.taskId().equals(submission.taskId())) {
                throw new BusinessProblem(ProblemCode.FORM_SUBMISSION_NOT_VALIDATED,
                        "表单提交不属于当前任务");
            }
            String formRef = "form-submission://" + submission.submissionId();
            completion = new CompleteHumanTaskCommand(
                    task.taskId(), command.expectedTaskVersion(), formRef, submission.contentDigest(), List.of(
                    new InputVersionRef(InputVersionRef.FORM_SUBMISSION, formRef, submission.contentDigest()),
                    new InputVersionRef(InputVersionRef.EVIDENCE_SET_SNAPSHOT,
                            snapshotRef, snapshot.contentDigest())));
        }
        // Task 内核仍在同一事务内复核状态、责任、版本、表单冻结版本和 Snapshot 最新解析代次。
        return humanTasks.complete(principal, metadata, completion);
    }

    private void requireClientCompatible(String clientKind, List<EvidenceSlotView> resolvedSlots) {
        clientCapabilityRuntimeGate.requireCompatibleEvidenceSlots(
                clientKind,
                resolvedSlots.stream().map(EvidenceSlotView::mediaType).toList(),
                resolvedSlots.stream().map(EvidenceSlotView::requirementDefinitionJson).toList());
    }

    /**
     * Portal 上下文和当前责任先于领域写命令重验；领域服务随后还会重验 RUNNING/guard、
     * evidence/file capability、上传会话归属、checksum 与幂等，避免适配层成为授权旁路。
     */
    private TaskFulfillmentContext requireCurrentTask(
            CurrentPrincipal principal, String correlationId, String header, UUID taskId
    ) {
        UUID networkId = parseContext(header);
        UUID principalId = principalUuid(principal);
        TechnicianProfileView profile = affiliations.findActiveTechnicianProfile(
                        principal.tenantId(), principalId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                        "当前主体没有有效的 TechnicianProfile"));
        boolean activeMember = affiliations.listActiveTechnicianMemberships(
                        principal.tenantId(), profile.id(), clock.instant()).stream()
                .map(NetworkTechnicianMembershipView::serviceNetworkId)
                .anyMatch(networkId::equals);
        if (!activeMember) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体不能使用请求的 Technician Portal 上下文");
        }
        authorization.require(principal, AuthorizationRequest.networkCapability(
                        TASK_READ_ASSIGNED, principal.tenantId(), "ServiceNetwork",
                        networkId.toString(), networkId.toString()), correlationId);

        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(DefaultTechnicianEvidenceService::taskNotFound);
        List<String> assigneeIds = List.of(principalId.toString(), profile.id().toString());
        boolean currentResponsible = assigneeIds.contains(task.responsiblePrincipalId());
        boolean sameNetwork = assignments.filterTaskIdsForNetwork(
                principal.tenantId(), networkId.toString(), List.of(taskId)).contains(taskId);
        if (!currentResponsible || !sameNetwork) {
            throw taskNotFound();
        }
        return task;
    }

    /** 在线端不接受 offline/locationVerified/uploader 等事实，只组装允许的最小声明。 */
    private String captureMetadata(TechnicianBeginEvidenceUploadCommand command) {
        String source = command.captureSource() == null
                ? "" : command.captureSource().trim().toUpperCase(Locale.ROOT);
        if (!ONLINE_SOURCES.contains(source)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "captureSource 必须是 CAMERA、GALLERY 或 FILE");
        }
        if (command.capturedAt() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "capturedAt 不能为空");
        }
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("captureSource", source);
        metadata.put("capturedAt", command.capturedAt().toString());
        metadata.put("offlineFlag", false);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Technician CaptureMetadata serialization failed", exception);
        }
    }

    private static BusinessProblem taskNotFound() {
        return new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "任务不存在");
    }

    private static UUID parseContext(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "缺少 X-Technician-Context");
        }
        String raw = header.trim();
        String uuid = raw.startsWith(CONTEXT_PREFIX) ? raw.substring(CONTEXT_PREFIX.length()) : raw;
        if (!raw.startsWith(CONTEXT_PREFIX) && raw.contains("|")) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Technician Portal 上下文形态无效");
        }
        try {
            return UUID.fromString(uuid);
        } catch (IllegalArgumentException exception) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Technician Portal 上下文形态无效");
        }
    }

    private static UUID principalUuid(CurrentPrincipal principal) {
        try {
            return UUID.fromString(principal.principalId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体无法形成 Technician Portal 上下文");
        }
    }
}
