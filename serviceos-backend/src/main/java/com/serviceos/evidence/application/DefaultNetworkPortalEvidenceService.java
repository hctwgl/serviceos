package com.serviceos.evidence.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.evidence.api.BeginEvidenceUploadOnBehalfCommand;
import com.serviceos.evidence.api.CorrectionCaseService;
import com.serviceos.evidence.api.CorrectionCaseView;
import com.serviceos.evidence.api.EvidenceCommandService;
import com.serviceos.evidence.api.EvidenceItemView;
import com.serviceos.evidence.api.EvidenceSetSnapshotService;
import com.serviceos.evidence.api.EvidenceSetSnapshotView;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceUploadSessionView;
import com.serviceos.evidence.api.FinalizeEvidenceUploadCommand;
import com.serviceos.evidence.api.NetworkPortalEvidenceService;
import com.serviceos.evidence.api.ResubmitCorrectionCaseCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ClientMetadata;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * M201 Network Portal 资料代补适配器；M368 叠加 NETWORK_WEB 客户端能力门禁。
 * <p>
 * 事务边界：门禁预检与 Evidence/Correction 命令同事务；聚合、幂等、审计与 Outbox 由领域服务保证。
 * 失败关闭：伪造上下文、非成员、无未关闭整改、onBehalfOf 非 ACTIVE TECHNICIAN、跨网点、
 * 非 NETWORK_WEB ClientKind、网点端能力不兼容。
 */
@Service
final class DefaultNetworkPortalEvidenceService implements NetworkPortalEvidenceService {
    private static final String CAPABILITY = "evidence.submitOnBehalf";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";
    private static final String REQUIRED_CLIENT_KIND = "NETWORK_WEB";
    private static final Set<String> OPEN_CORRECTION_STATUSES = Set.of(
            "OPEN", "IN_PROGRESS", "RESUBMITTED");

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final ActiveServiceResponsibilityService responsibilities;
    private final NetworkPortalTechnicianQuery technicians;
    private final EvidenceCommandService evidence;
    private final EvidenceSlotQueryService slots;
    private final EvidenceSetSnapshotService snapshots;
    private final CorrectionCaseService corrections;
    private final ClientCapabilityRuntimeGate clientCapabilityRuntimeGate;
    private final Clock clock;

    DefaultNetworkPortalEvidenceService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            ActiveServiceResponsibilityService responsibilities,
            NetworkPortalTechnicianQuery technicians,
            EvidenceCommandService evidence,
            EvidenceSlotQueryService slots,
            EvidenceSetSnapshotService snapshots,
            CorrectionCaseService corrections,
            ClientCapabilityRuntimeGate clientCapabilityRuntimeGate,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.responsibilities = responsibilities;
        this.technicians = technicians;
        this.evidence = evidence;
        this.slots = slots;
        this.snapshots = snapshots;
        this.corrections = corrections;
        this.clientCapabilityRuntimeGate = clientCapabilityRuntimeGate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EvidenceUploadSessionView beginUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID taskId,
            UUID slotId,
            BeginEvidenceUploadOnBehalfCommand command
    ) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(command, "command");
        if (!taskId.equals(command.taskId()) || !slotId.equals(command.slotId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "path 与请求体 taskId/slotId 不一致");
        }
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        ActiveServiceResponsibility responsibility = requireNetworkOwnedTask(
                principal.tenantId(), taskId, networkId);
        requireOpenCorrection(principal, metadata.correlationId(), taskId);
        String onBehalfOf = requireText(command.onBehalfOf(), "onBehalfOf", 128);
        requireActiveTechnicianAssignee(principal.tenantId(), networkId, responsibility, onBehalfOf);
        requireNetworkWebCapable(principal, metadata.correlationId(), clientKind, taskId, networkId);

        BeginEvidenceUploadOnBehalfCommand scoped = new BeginEvidenceUploadOnBehalfCommand(
                command.taskId(), command.slotId(), command.evidenceItemId(),
                command.originalFileName(), command.declaredMimeType(), command.expectedSize(),
                command.expectedSha256(), command.captureMetadataJson(),
                onBehalfOf, requireText(command.onBehalfReason(), "onBehalfReason", 500),
                networkId);
        return evidence.beginUploadOnBehalf(principal, metadata, scoped);
    }

    @Override
    @Transactional
    public EvidenceItemView finalizeUploadOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID taskId,
            UUID slotId,
            UUID uploadSessionId,
            FinalizeEvidenceUploadCommand command
    ) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(uploadSessionId, "uploadSessionId");
        Objects.requireNonNull(command, "command");
        if (!taskId.equals(command.taskId()) || !slotId.equals(command.slotId())
                || !uploadSessionId.equals(command.uploadSessionId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "path 与请求体 taskId/slotId/uploadSessionId 不一致");
        }
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        requireNetworkOwnedTask(principal.tenantId(), taskId, networkId);
        requireOpenCorrection(principal, metadata.correlationId(), taskId);
        requireNetworkWebCapable(principal, metadata.correlationId(), clientKind, taskId, networkId);
        return evidence.finalizeUploadOnBehalf(principal, metadata, command, networkId);
    }

    @Override
    @Transactional
    public EvidenceSetSnapshotView createSnapshotOnBehalf(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID correctionCaseId,
            java.util.List<UUID> memberRevisionIds
    ) {
        Objects.requireNonNull(correctionCaseId, "correctionCaseId");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        CorrectionCaseView current = corrections.get(principal, metadata.correlationId(), correctionCaseId);
        requireNetworkOwnedTask(principal.tenantId(), current.taskId(), networkId);
        requireOpenCorrection(principal, metadata.correlationId(), current.taskId());
        requireNetworkWebCapable(
                principal, metadata.correlationId(), clientKind, current.taskId(), networkId);
        return snapshots.createOnBehalf(
                principal, metadata, correctionCaseId, memberRevisionIds, networkId);
    }

    @Override
    @Transactional
    public CorrectionCaseView resubmit(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            String clientKind,
            UUID correctionCaseId,
            UUID evidenceSetSnapshotId
    ) {
        Objects.requireNonNull(correctionCaseId, "correctionCaseId");
        Objects.requireNonNull(evidenceSetSnapshotId, "evidenceSetSnapshotId");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        CorrectionCaseView current = corrections.get(principal, metadata.correlationId(), correctionCaseId);
        requireNetworkOwnedTask(principal.tenantId(), current.taskId(), networkId);
        requireNetworkWebCapable(
                principal, metadata.correlationId(), clientKind, current.taskId(), networkId);
        return corrections.resubmit(principal, metadata,
                new ResubmitCorrectionCaseCommand(correctionCaseId, evidenceSetSnapshotId));
    }

    /**
     * ADR-089：代补按网点端 NETWORK_WEB 评估能力；定向 supportedClientKinds 不参与（传空列表）。
     * 槽位读取走 NETWORK scope evidence.read（listForTaskOnNetwork），避免项目 scope 旁路。
     */
    private void requireNetworkWebCapable(
            CurrentPrincipal principal,
            String correlationId,
            String clientKind,
            UUID taskId,
            UUID networkId
    ) {
        String kind = clientKind == null || clientKind.isBlank()
                ? ClientMetadata.UNKNOWN_KIND : clientKind.trim();
        if (!REQUIRED_CLIENT_KIND.equals(kind)) {
            throw new BusinessProblem(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED,
                    "网点资料代补要求 X-ServiceOS-Client-Kind=NETWORK_WEB；当前为 " + kind
                            + "。请使用网点 Web 客户端，或由兼容端处理。");
        }
        List<EvidenceSlotView> resolved = slots.listForTaskOnNetwork(
                principal, correlationId, taskId, networkId);
        clientCapabilityRuntimeGate.requireCompatibleEvidenceSlots(
                kind,
                resolved.stream().map(EvidenceSlotView::mediaType).toList(),
                resolved.stream().map(EvidenceSlotView::requirementDefinitionJson).toList(),
                List.of());
    }

    private void requireOpenCorrection(CurrentPrincipal principal, String correlationId, UUID taskId) {
        boolean open = corrections.listForTask(principal, correlationId, taskId).stream()
                .map(CorrectionCaseView::status)
                .anyMatch(OPEN_CORRECTION_STATUSES::contains);
        if (!open) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "资料代补要求任务存在未关闭整改（OPEN/IN_PROGRESS/RESUBMITTED）");
        }
    }

    private void requireActiveTechnicianAssignee(
            String tenantId,
            UUID networkId,
            ActiveServiceResponsibility responsibility,
            String onBehalfOf
    ) {
        if (responsibility.technicianId() == null || responsibility.technicianId().isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "任务没有 ACTIVE TECHNICIAN，应先指派/改派师傅");
        }
        if (!responsibility.technicianId().equals(onBehalfOf)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "onBehalfOf 必须等于任务 ACTIVE TECHNICIAN 责任人");
        }
        boolean member = technicians.listActiveTechnicians(tenantId, networkId).stream()
                .anyMatch(row -> matchesTechnician(row, onBehalfOf));
        if (!member) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "onBehalfOf 对本网点没有 ACTIVE 师傅服务关系");
        }
    }

    private static boolean matchesTechnician(NetworkPortalTechnicianView row, String onBehalfOf) {
        return row.technicianProfileId().toString().equals(onBehalfOf)
                || row.principalId().toString().equals(onBehalfOf);
    }

    private ActiveServiceResponsibility requireNetworkOwnedTask(
            String tenantId, UUID taskId, UUID networkId
    ) {
        ActiveServiceResponsibility responsibility = responsibilities.find(tenantId, taskId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.ACCESS_DENIED,
                        "任务对本网点没有 ACTIVE NETWORK 责任"));
        if (responsibility.networkId() == null
                || !responsibility.networkId().equals(networkId.toString())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "任务对本网点没有 ACTIVE NETWORK 责任");
        }
        return responsibility;
    }

    private UUID requireAuthorizedNetwork(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = parseNetworkContext(networkContextHeader);
        UUID principalId = requirePrincipalUuid(actor);
        Instant at = clock.instant();
        boolean member = affiliations.listActiveNetworkMemberships(actor.tenantId(), principalId, at).stream()
                .map(NetworkMembershipView::serviceNetworkId)
                .anyMatch(networkId::equals);
        if (!member) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID,
                    "当前主体不能使用请求的 Network Portal 上下文");
        }
        authorization.require(actor, AuthorizationRequest.networkCapability(
                CAPABILITY, actor.tenantId(), "ServiceNetwork", networkId.toString(), networkId.toString()),
                correlationId);
        return networkId;
    }

    private static UUID parseNetworkContext(String header) {
        if (header == null || header.isBlank()) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "缺少 X-Network-Context");
        }
        String raw = header.trim();
        String uuidPart = raw;
        if (raw.startsWith(CONTEXT_PREFIX)) {
            uuidPart = raw.substring(CONTEXT_PREFIX.length());
        } else if (raw.contains("|")) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Network Portal 上下文形态无效");
        }
        try {
            return UUID.fromString(uuidPart);
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "Network Portal 上下文形态无效");
        }
    }

    private static UUID requirePrincipalUuid(CurrentPrincipal actor) {
        try {
            return UUID.fromString(actor.principalId());
        } catch (IllegalArgumentException ex) {
            throw new BusinessProblem(ProblemCode.PORTAL_CONTEXT_INVALID, "当前主体无法形成 Network Portal 上下文");
        }
    }

    private static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, name + " 无效");
        }
        return value.trim();
    }
}
