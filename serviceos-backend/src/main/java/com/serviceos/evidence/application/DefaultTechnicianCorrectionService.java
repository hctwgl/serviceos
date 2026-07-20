package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.dispatch.api.TechnicianActiveAssignmentQuery;
import com.serviceos.evidence.api.BeginCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.ResubmitCorrectionCaseCommand;
import com.serviceos.evidence.api.TechnicianBeginCorrectionEvidenceUploadCommand;
import com.serviceos.evidence.api.TechnicianCorrectionService;
import com.serviceos.evidence.api.TechnicianCorrectionView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.ClaimHumanTaskCommand;
import com.serviceos.task.api.HandlingTaskContextQuery;
import com.serviceos.task.api.HandlingTaskContextView;
import com.serviceos.task.api.HumanTaskCommandService;
import com.serviceos.task.api.StartHumanTaskCommand;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Technician 整改适配层。源 Task 的网络归属只用于收窄 Portal 上下文；实际写权限始终来自
 * 独立 correction Task 的 CANDIDATE/RESPONSIBLE 快照，避免把已完成源 Task 重新置为 RUNNING。
 *
 * <p>M361：整改资料路径复用主 Evidence 的客户端能力门禁与定向目标，禁止以整改旁路绕过
 * {@code CLIENT_CAPABILITY_UNSUPPORTED}。</p>
 *
 * <p>M362：列表投影对源 Task 冻结 Bundle 做能力预检软注解，不整表 422、不隐藏整改责任。</p>
 *
 * <p>M363：claim/start 在变更状态前对源 Task 冻结 Bundle 硬拒单（与 M359 任务详情同构），
 * 对齐 Product/08「不允许执行到现场中途才发现必需能力缺失」；UNKNOWN 仍由 Probe 短路。</p>
 */
@Service
final class DefaultTechnicianCorrectionService implements TechnicianCorrectionService {
    private static final String CONTEXT_PREFIX = "TECHNICIAN|NETWORK|";
    private static final String TASK_READ_ASSIGNED = "task.readAssigned";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final TechnicianActiveAssignmentQuery assignments;
    private final HandlingTaskContextQuery handlingTasks;
    private final HumanTaskCommandService humanTasks;
    private final CorrectionCaseRepository correctionRepository;
    private final CorrectionCaseService corrections;
    private final EvidenceSlotQueryService slots;
    private final EvidenceCommandService evidence;
    private final EvidenceSetSnapshotService snapshots;
    private final AuthorizationService authorization;
    private final ClientCapabilityRuntimeGate clientCapabilityRuntimeGate;
    private final FrozenBundleClientCapabilityProbe clientCapabilityProbe;
    private final ConfigurationService configurations;
    private final TaskFulfillmentContextService sourceTasks;
    private final Clock clock;

    DefaultTechnicianCorrectionService(
            PrincipalNetworkAffiliationQuery affiliations,
            TechnicianActiveAssignmentQuery assignments,
            HandlingTaskContextQuery handlingTasks,
            HumanTaskCommandService humanTasks,
            CorrectionCaseRepository correctionRepository,
            CorrectionCaseService corrections,
            EvidenceSlotQueryService slots,
            EvidenceCommandService evidence,
            EvidenceSetSnapshotService snapshots,
            AuthorizationService authorization,
            ClientCapabilityRuntimeGate clientCapabilityRuntimeGate,
            FrozenBundleClientCapabilityProbe clientCapabilityProbe,
            ConfigurationService configurations,
            TaskFulfillmentContextService sourceTasks,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.assignments = assignments;
        this.handlingTasks = handlingTasks;
        this.humanTasks = humanTasks;
        this.correctionRepository = correctionRepository;
        this.corrections = corrections;
        this.slots = slots;
        this.evidence = evidence;
        this.snapshots = snapshots;
        this.authorization = authorization;
        this.clientCapabilityRuntimeGate = clientCapabilityRuntimeGate;
        this.clientCapabilityProbe = clientCapabilityProbe;
        this.configurations = configurations;
        this.sourceTasks = sourceTasks;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianCorrectionView> list(
            CurrentPrincipal principal, String correlationId, String context, String clientKind
    ) {
        PortalActor actor = requirePortalActor(principal, correlationId, context);
        return handlingTasks.listForActor(
                        principal.tenantId(), principal.principalId(), CorrectionTaskAccessValidator.TASK_TYPE)
                .stream()
                .filter(task -> List.of("READY", "CLAIMED", "RUNNING").contains(task.status()))
                .map(task -> correctionRepository.findByCorrectionTaskId(principal.tenantId(), task.taskId())
                        .map(correction -> visibleInNetwork(principal, actor.networkId(), correction)
                                ? view(principal.tenantId(), correction, task, clientKind) : null)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public TechnicianCorrectionView claim(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, long expectedVersion
    ) {
        Access access = requireCase(principal, metadata.correlationId(), context, correctionCaseId);
        if (!access.task().actorCandidate()) {
            throw new BusinessProblem(ProblemCode.TASK_ASSIGNMENT_CONFLICT, "当前主体不是整改任务候选人");
        }
        // 先硬拒再 claim，避免不兼容端把任务推进到 CLAIMED 后再失败。
        requireSourceTaskClientCompatible(principal.tenantId(), clientKind, access.correction().taskId());
        humanTasks.claim(principal, metadata,
                new ClaimHumanTaskCommand(access.task().taskId(), expectedVersion));
        return currentView(principal, clientKind, correctionCaseId, access.task().taskId());
    }

    @Override
    public TechnicianCorrectionView start(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, long expectedVersion
    ) {
        Access access = requireCase(principal, metadata.correlationId(), context, correctionCaseId);
        if (!access.task().actorResponsible()) {
            throw new BusinessProblem(ProblemCode.TASK_ASSIGNMENT_CONFLICT, "当前主体不负责整改任务");
        }
        // 已 CLAIMED 的不兼容端在 start 前仍失败关闭，禁止进入 RUNNING 补传。
        requireSourceTaskClientCompatible(principal.tenantId(), clientKind, access.correction().taskId());
        humanTasks.start(principal, metadata,
                new StartHumanTaskCommand(access.task().taskId(), expectedVersion));
        return currentView(principal, clientKind, correctionCaseId, access.task().taskId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceSlotView> listSlots(
            CurrentPrincipal principal, String correlationId, String context,
            String clientKind, UUID correctionCaseId
    ) {
        Access access = requireCase(principal, correlationId, context, correctionCaseId);
        List<EvidenceSlotView> resolved = slots.listForTask(
                principal, correlationId, access.correction().taskId());
        requireClientCompatible(principal.tenantId(), access.correction().taskId(), clientKind, resolved);
        return resolved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItemView> listItems(
            CurrentPrincipal principal, String correlationId, String context,
            String clientKind, UUID correctionCaseId
    ) {
        Access access = requireCase(principal, correlationId, context, correctionCaseId);
        requireClientCompatible(
                principal.tenantId(),
                access.correction().taskId(),
                clientKind,
                slots.listForTask(principal, correlationId, access.correction().taskId()));
        return evidence.listForTask(principal, correlationId, access.correction().taskId());
    }

    @Override
    public EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, UUID slotId,
            TechnicianBeginCorrectionEvidenceUploadCommand command
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        requireClientCompatible(
                principal.tenantId(),
                access.correction().taskId(),
                clientKind,
                slots.listForTask(principal, metadata.correlationId(), access.correction().taskId()));
        return evidence.beginCorrectionUpload(principal, metadata, new BeginCorrectionEvidenceUploadCommand(
                correctionCaseId, access.task().taskId(), access.correction().taskId(), slotId,
                command.evidenceItemId(), command.originalFileName(), command.declaredMimeType(),
                command.expectedSize(), command.expectedSha256(), command.captureSource(), command.capturedAt()));
    }

    @Override
    public EvidenceItemView finalizeUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, UUID slotId, UUID uploadSessionId,
            String actualSha256, String finalizeCommandId
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        requireClientCompatible(
                principal.tenantId(),
                access.correction().taskId(),
                clientKind,
                slots.listForTask(principal, metadata.correlationId(), access.correction().taskId()));
        return evidence.finalizeCorrectionUpload(principal, metadata, new FinalizeCorrectionEvidenceUploadCommand(
                correctionCaseId, access.task().taskId(), access.correction().taskId(), slotId,
                uploadSessionId, actualSha256, finalizeCommandId));
    }

    @Override
    public EvidenceSetSnapshotView createSnapshot(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, List<UUID> memberRevisionIds
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        requireClientCompatible(
                principal.tenantId(),
                access.correction().taskId(),
                clientKind,
                slots.listForTask(principal, metadata.correlationId(), access.correction().taskId()));
        return snapshots.createForCorrection(principal, metadata, correctionCaseId,
                access.task().taskId(), access.correction().taskId(), memberRevisionIds);
    }

    @Override
    @Transactional
    public TechnicianCorrectionView resubmit(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            String clientKind, UUID correctionCaseId, UUID evidenceSetSnapshotId
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        // 重提前复检：禁止客户端在上传时兼容、重提时换成不兼容端绕过。
        requireClientCompatible(
                principal.tenantId(),
                access.correction().taskId(),
                clientKind,
                slots.listForTask(principal, metadata.correlationId(), access.correction().taskId()));
        CorrectionCaseView updated = corrections.resubmit(principal, metadata,
                new ResubmitCorrectionCaseCommand(correctionCaseId, evidenceSetSnapshotId));
        // 重提可能发生多轮，不能在此完成 handling Task；审核 CLOSED 才是其权威终态。
        return view(principal.tenantId(), updated, access.task(), clientKind);
    }

    /**
     * 对整改源 Task 冻结 Bundle 的 EVIDENCE 槽位做能力门禁；源 Task 缺失 Bundle 时仍按槽位 mediaType/条件校验。
     */
    private void requireClientCompatible(
            String tenantId,
            UUID sourceTaskId,
            String clientKind,
            List<EvidenceSlotView> resolvedSlots
    ) {
        TaskFulfillmentContext sourceTask = sourceTasks.find(tenantId, sourceTaskId).orElse(null);
        clientCapabilityRuntimeGate.requireCompatibleEvidenceSlots(
                clientKind,
                resolvedSlots.stream().map(EvidenceSlotView::mediaType).toList(),
                resolvedSlots.stream().map(EvidenceSlotView::requirementDefinitionJson).toList(),
                resolveEvidenceSupportedClientKinds(tenantId, sourceTask));
    }

    private List<String> resolveEvidenceSupportedClientKinds(
            String tenantId, TaskFulfillmentContext task
    ) {
        if (task == null
                || task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            return List.of();
        }
        return configurations.listBundleAssets(
                        tenantId, task.configurationBundleId(), task.configurationBundleDigest(),
                        ConfigurationAssetType.EVIDENCE)
                .stream()
                .map(ConfigurationAssetDefinition::supportedClientKinds)
                .filter(kinds -> kinds != null && !kinds.isEmpty())
                .findFirst()
                .orElse(List.of());
    }

    private Access requireWritableCase(
            CurrentPrincipal principal, String correlationId, String context, UUID correctionCaseId
    ) {
        Access access = requireCase(principal, correlationId, context, correctionCaseId);
        if (!"RUNNING".equals(access.task().status())
                || !access.task().actorResponsible()
                || !principal.principalId().equals(access.task().claimedBy())) {
            throw new BusinessProblem(ProblemCode.TASK_ASSIGNMENT_CONFLICT,
                    "整改资料写入要求当前主体正在负责 RUNNING correction Task");
        }
        return access;
    }

    private Access requireCase(
            CurrentPrincipal principal, String correlationId, String context, UUID correctionCaseId
    ) {
        PortalActor actor = requirePortalActor(principal, correlationId, context);
        CorrectionCaseView correction = correctionRepository.find(principal.tenantId(), correctionCaseId)
                .orElseThrow(DefaultTechnicianCorrectionService::notFound);
        if (correction.correctionTaskId() == null || !visibleInNetwork(principal, actor.networkId(), correction)) {
            throw notFound();
        }
        HandlingTaskContextView task = handlingTasks.findForActor(
                        principal.tenantId(), correction.correctionTaskId(), principal.principalId(),
                        CorrectionTaskAccessValidator.TASK_TYPE)
                .orElseThrow(DefaultTechnicianCorrectionService::notFound);
        if (!correctionCaseId.toString().equals(task.businessKey())) {
            throw notFound();
        }
        return new Access(correction, task);
    }

    private boolean visibleInNetwork(
            CurrentPrincipal principal, UUID networkId, CorrectionCaseView correction
    ) {
        return assignments.filterTaskIdsForNetwork(
                principal.tenantId(), networkId.toString(), List.of(correction.taskId()))
                .contains(correction.taskId());
    }

    private PortalActor requirePortalActor(
            CurrentPrincipal principal, String correlationId, String context
    ) {
        UUID networkId = parseContext(context);
        UUID principalId = principalUuid(principal);
        TechnicianProfileView profile = affiliations.findActiveTechnicianProfile(
                        principal.tenantId(), principalId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.PORTAL_CONTEXT_INVALID, "当前主体没有有效的 TechnicianProfile"));
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
        return new PortalActor(networkId);
    }

    private TechnicianCorrectionView currentView(
            CurrentPrincipal principal, String clientKind, UUID correctionCaseId, UUID correctionTaskId
    ) {
        CorrectionCaseView correction = correctionRepository.find(principal.tenantId(), correctionCaseId)
                .orElseThrow(DefaultTechnicianCorrectionService::notFound);
        HandlingTaskContextView task = handlingTasks.findForActor(
                        principal.tenantId(), correctionTaskId, principal.principalId(),
                        CorrectionTaskAccessValidator.TASK_TYPE)
                .orElseThrow(DefaultTechnicianCorrectionService::notFound);
        return view(principal.tenantId(), correction, task, clientKind);
    }

    /**
     * 投影最小整改视图，并按源业务 Task 冻结 Bundle 注解能力不兼容说明。
     *
     * <p>失败关闭语义：注解只告知、不删除责任项；UNKNOWN/非师傅端由 Probe 短路跳过（M253）。
     * 资料读写仍由 M361 RuntimeGate 硬拒单，本注解用于领取/开工前预检。</p>
     */
    private TechnicianCorrectionView view(
            String tenantId,
            CorrectionCaseView correction,
            HandlingTaskContextView task,
            String clientKind
    ) {
        return new TechnicianCorrectionView(
                correction.correctionCaseId(), correction.taskId(), correction.correctionTaskId(),
                correction.status(), correction.reasonCodes(), task.status(), task.version(),
                correction.latestResubmissionSnapshotId(), correction.resubmissions().size(),
                capabilityDetail(tenantId, clientKind, correction.taskId()));
    }

    private String capabilityDetail(String tenantId, String clientKind, UUID sourceTaskId) {
        TaskFulfillmentContext sourceTask = sourceTasks.find(tenantId, sourceTaskId).orElse(null);
        if (sourceTask == null) {
            return null;
        }
        return clientCapabilityProbe.findIncompatibilityDetail(
                        tenantId,
                        clientKind,
                        sourceTask.configurationBundleId(),
                        sourceTask.configurationBundleDigest(),
                        sourceTask.formRef())
                .orElse(null);
    }

    /**
     * claim/start 入口硬预检：与列表软注解同源 Probe，但在状态迁移前失败关闭。
     *
     * <p>源 Task 缺失时不在此发明额外拒单（沿用后续资料路径/404 语义）；Probe 对 UNKNOWN 短路。</p>
     */
    private void requireSourceTaskClientCompatible(String tenantId, String clientKind, UUID sourceTaskId) {
        TaskFulfillmentContext sourceTask = sourceTasks.find(tenantId, sourceTaskId).orElse(null);
        if (sourceTask == null) {
            return;
        }
        clientCapabilityProbe.requireCompatible(
                tenantId,
                clientKind,
                sourceTask.configurationBundleId(),
                sourceTask.configurationBundleDigest(),
                sourceTask.formRef());
    }

    private static UUID parseContext(String header) {
        if (header == null || !header.startsWith(CONTEXT_PREFIX)) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "X-Technician-Context 必须是 TECHNICIAN|NETWORK|<networkId>");
        }
        try {
            return UUID.fromString(header.substring(CONTEXT_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "X-Technician-Context 中的 networkId 无效");
        }
    }

    private static UUID principalUuid(CurrentPrincipal principal) {
        try {
            return UUID.fromString(principal.principalId());
        } catch (IllegalArgumentException exception) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体不是可映射的 Technician Principal");
        }
    }

    private static BusinessProblem notFound() {
        return new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "整改任务不存在");
    }

    private record PortalActor(UUID networkId) {
    }

    private record Access(CorrectionCaseView correction, HandlingTaskContextView task) {
    }
}
