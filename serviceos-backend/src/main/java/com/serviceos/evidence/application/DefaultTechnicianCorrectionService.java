package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Technician 整改适配层。源 Task 的网络归属只用于收窄 Portal 上下文；实际写权限始终来自
 * 独立 correction Task 的 CANDIDATE/RESPONSIBLE 快照，避免把已完成源 Task 重新置为 RUNNING。
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
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TechnicianCorrectionView> list(
            CurrentPrincipal principal, String correlationId, String context
    ) {
        PortalActor actor = requirePortalActor(principal, correlationId, context);
        return handlingTasks.listForActor(
                        principal.tenantId(), principal.principalId(), CorrectionTaskAccessValidator.TASK_TYPE)
                .stream()
                .filter(task -> List.of("READY", "CLAIMED", "RUNNING").contains(task.status()))
                .map(task -> correctionRepository.findByCorrectionTaskId(principal.tenantId(), task.taskId())
                        .map(correction -> visibleInNetwork(principal, actor.networkId(), correction)
                                ? view(correction, task) : null)
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public TechnicianCorrectionView claim(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, long expectedVersion
    ) {
        Access access = requireCase(principal, metadata.correlationId(), context, correctionCaseId);
        if (!access.task().actorCandidate()) {
            throw new BusinessProblem(ProblemCode.TASK_ASSIGNMENT_CONFLICT, "当前主体不是整改任务候选人");
        }
        humanTasks.claim(principal, metadata,
                new ClaimHumanTaskCommand(access.task().taskId(), expectedVersion));
        return currentView(principal, correctionCaseId, access.task().taskId());
    }

    @Override
    public TechnicianCorrectionView start(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, long expectedVersion
    ) {
        Access access = requireCase(principal, metadata.correlationId(), context, correctionCaseId);
        if (!access.task().actorResponsible()) {
            throw new BusinessProblem(ProblemCode.TASK_ASSIGNMENT_CONFLICT, "当前主体不负责整改任务");
        }
        humanTasks.start(principal, metadata,
                new StartHumanTaskCommand(access.task().taskId(), expectedVersion));
        return currentView(principal, correctionCaseId, access.task().taskId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceSlotView> listSlots(
            CurrentPrincipal principal, String correlationId, String context, UUID correctionCaseId
    ) {
        Access access = requireCase(principal, correlationId, context, correctionCaseId);
        return slots.listForTask(principal, correlationId, access.correction().taskId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenceItemView> listItems(
            CurrentPrincipal principal, String correlationId, String context, UUID correctionCaseId
    ) {
        Access access = requireCase(principal, correlationId, context, correctionCaseId);
        return evidence.listForTask(principal, correlationId, access.correction().taskId());
    }

    @Override
    public EvidenceUploadSessionView beginUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, UUID slotId,
            TechnicianBeginCorrectionEvidenceUploadCommand command
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        return evidence.beginCorrectionUpload(principal, metadata, new BeginCorrectionEvidenceUploadCommand(
                correctionCaseId, access.task().taskId(), access.correction().taskId(), slotId,
                command.evidenceItemId(), command.originalFileName(), command.declaredMimeType(),
                command.expectedSize(), command.expectedSha256(), command.captureSource(), command.capturedAt()));
    }

    @Override
    public EvidenceItemView finalizeUpload(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, UUID slotId, UUID uploadSessionId,
            String actualSha256, String finalizeCommandId
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        return evidence.finalizeCorrectionUpload(principal, metadata, new FinalizeCorrectionEvidenceUploadCommand(
                correctionCaseId, access.task().taskId(), access.correction().taskId(), slotId,
                uploadSessionId, actualSha256, finalizeCommandId));
    }

    @Override
    public EvidenceSetSnapshotView createSnapshot(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, List<UUID> memberRevisionIds
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        return snapshots.createForCorrection(principal, metadata, correctionCaseId,
                access.task().taskId(), access.correction().taskId(), memberRevisionIds);
    }

    @Override
    @Transactional
    public TechnicianCorrectionView resubmit(
            CurrentPrincipal principal, CommandMetadata metadata, String context,
            UUID correctionCaseId, UUID evidenceSetSnapshotId
    ) {
        Access access = requireWritableCase(principal, metadata.correlationId(), context, correctionCaseId);
        CorrectionCaseView updated = corrections.resubmit(principal, metadata,
                new ResubmitCorrectionCaseCommand(correctionCaseId, evidenceSetSnapshotId));
        // 重提可能发生多轮，不能在此完成 handling Task；审核 CLOSED 才是其权威终态。
        return view(updated, access.task());
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
            CurrentPrincipal principal, UUID correctionCaseId, UUID correctionTaskId
    ) {
        CorrectionCaseView correction = correctionRepository.find(principal.tenantId(), correctionCaseId)
                .orElseThrow(DefaultTechnicianCorrectionService::notFound);
        HandlingTaskContextView task = handlingTasks.findForActor(
                        principal.tenantId(), correctionTaskId, principal.principalId(),
                        CorrectionTaskAccessValidator.TASK_TYPE)
                .orElseThrow(DefaultTechnicianCorrectionService::notFound);
        return view(correction, task);
    }

    private static TechnicianCorrectionView view(
            CorrectionCaseView correction, HandlingTaskContextView task
    ) {
        return new TechnicianCorrectionView(
                correction.correctionCaseId(), correction.taskId(), correction.correctionTaskId(),
                correction.status(), correction.reasonCodes(), task.status(), task.version(),
                correction.latestResubmissionSnapshotId(), correction.resubmissions().size());
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
