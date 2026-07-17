package com.serviceos.appointment.application;

import com.serviceos.appointment.api.AppointmentCommandReceipt;
import com.serviceos.appointment.api.AppointmentService;
import com.serviceos.appointment.api.AppointmentView;
import com.serviceos.appointment.api.AppointmentWindow;
import com.serviceos.appointment.api.CancelAppointmentCommand;
import com.serviceos.appointment.api.ConfirmAppointmentCommand;
import com.serviceos.appointment.api.ContactAttemptView;
import com.serviceos.appointment.api.MarkAppointmentNoShowCommand;
import com.serviceos.appointment.api.NetworkPortalAppointmentService;
import com.serviceos.appointment.api.ProposeAppointmentCommand;
import com.serviceos.appointment.api.RecordContactAttemptCommand;
import com.serviceos.appointment.api.RescheduleAppointmentCommand;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ActiveServiceResponsibility;
import com.serviceos.dispatch.api.ActiveServiceResponsibilityService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Network Portal 预约协作适配器
 * （M197 propose/confirm + M198 reschedule/cancel + M199 mark-no-show/contact）。
 * <p>
 * 事务边界：门禁预检与 AppointmentService 命令同事务；聚合修订、幂等、审计与 Outbox
 * 由 AppointmentService 保证。
 * 幂等键：HTTP Idempotency-Key 原样下传。
 * 失败关闭：伪造上下文、非成员、任务/预约非本网点 ACTIVE NETWORK、伪装 TECHNICIAN 确认方、
 * 乐观锁版本冲突、未结束窗口爽约。
 */
@Service
final class DefaultNetworkPortalAppointmentService implements NetworkPortalAppointmentService {
    private static final String CAPABILITY = "networkPortal.manageAppointment";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";
    private static final Set<String> ALLOWED_CONFIRM_PARTY_TYPES = Set.of("NETWORK_MEMBER", "NETWORK");

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final ActiveServiceResponsibilityService responsibilities;
    private final AppointmentService appointments;
    private final Clock clock;

    DefaultNetworkPortalAppointmentService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            ActiveServiceResponsibilityService responsibilities,
            AppointmentService appointments,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.responsibilities = responsibilities;
        this.appointments = appointments;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentView> listByTask(
            CurrentPrincipal principal,
            String correlationId,
            String networkContextHeader,
            UUID taskId
    ) {
        Objects.requireNonNull(taskId, "taskId");
        UUID networkId = requireAuthorizedNetwork(principal, correlationId, networkContextHeader);
        requireNetworkOwnedTask(principal.tenantId(), taskId, networkId);
        return appointments.listByTask(principal, correlationId, taskId);
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt propose(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            ProposeAppointmentCommand command
    ) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(command, "command");
        if (!taskId.equals(command.taskId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "path taskId 与请求体 taskId 不一致");
        }
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        requireNetworkOwnedTask(principal.tenantId(), taskId, networkId);
        return appointments.propose(principal, metadata, command);
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt confirm(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID appointmentId,
            long expectedVersion,
            String confirmedPartyType,
            String confirmedPartyRef,
            String confirmationChannel
    ) {
        Objects.requireNonNull(appointmentId, "appointmentId");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        AppointmentView current = appointments.get(principal, metadata.correlationId(), appointmentId);
        requireNetworkOwnedTask(principal.tenantId(), current.taskId(), networkId);
        if (current.assignedNetworkId() == null
                || !current.assignedNetworkId().equals(networkId.toString())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "预约不属于当前 Network Portal 上下文网点");
        }

        String partyType = normalizeConfirmPartyType(confirmedPartyType);
        String partyRef = requireConfirmPartyRef(principal, confirmedPartyRef);
        return appointments.confirm(principal, metadata, new ConfirmAppointmentCommand(
                appointmentId, expectedVersion, partyType, partyRef, confirmationChannel));
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt reschedule(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID appointmentId,
            long expectedVersion,
            AppointmentWindow newWindow,
            String reasonCode,
            String note
    ) {
        Objects.requireNonNull(appointmentId, "appointmentId");
        Objects.requireNonNull(newWindow, "newWindow");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        requireNetworkOwnedAppointment(principal, metadata.correlationId(), appointmentId, networkId);
        return appointments.reschedule(principal, metadata, new RescheduleAppointmentCommand(
                appointmentId, expectedVersion, newWindow, reasonCode, note));
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt cancel(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID appointmentId,
            long expectedVersion,
            String reasonCode,
            String note
    ) {
        Objects.requireNonNull(appointmentId, "appointmentId");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        requireNetworkOwnedAppointment(principal, metadata.correlationId(), appointmentId, networkId);
        return appointments.cancel(principal, metadata, new CancelAppointmentCommand(
                appointmentId, expectedVersion, reasonCode, note));
    }

    @Override
    @Transactional
    public AppointmentCommandReceipt markNoShow(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID appointmentId,
            long expectedVersion,
            String noShowPartyType,
            String noShowPartyRef,
            String reasonCode,
            List<String> evidenceRefs
    ) {
        Objects.requireNonNull(appointmentId, "appointmentId");
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        requireNetworkOwnedAppointment(principal, metadata.correlationId(), appointmentId, networkId);
        return appointments.markNoShow(principal, metadata, new MarkAppointmentNoShowCommand(
                appointmentId, expectedVersion, noShowPartyType, noShowPartyRef, reasonCode, evidenceRefs));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactAttemptView> listContactAttempts(
            CurrentPrincipal principal,
            String correlationId,
            String networkContextHeader,
            UUID taskId
    ) {
        Objects.requireNonNull(taskId, "taskId");
        UUID networkId = requireAuthorizedNetwork(principal, correlationId, networkContextHeader);
        requireNetworkOwnedTask(principal.tenantId(), taskId, networkId);
        return appointments.listContactAttempts(principal, correlationId, taskId);
    }

    @Override
    @Transactional
    public ContactAttemptView recordContactAttempt(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String networkContextHeader,
            UUID taskId,
            RecordContactAttemptCommand command
    ) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(command, "command");
        if (!taskId.equals(command.taskId())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "path taskId 与请求体 taskId 不一致");
        }
        UUID networkId = requireAuthorizedNetwork(principal, metadata.correlationId(), networkContextHeader);
        requireNetworkOwnedTask(principal.tenantId(), taskId, networkId);
        return appointments.recordContactAttempt(principal, metadata, command);
    }

    /**
     * 改约/取消/爽约前校验预约任务对本上下文网点持有 ACTIVE NETWORK 责任，并核对 snapshot 网点。
     */
    private void requireNetworkOwnedAppointment(
            CurrentPrincipal principal, String correlationId, UUID appointmentId, UUID networkId
    ) {
        AppointmentView current = appointments.get(principal, correlationId, appointmentId);
        requireNetworkOwnedTask(principal.tenantId(), current.taskId(), networkId);
        if (current.assignedNetworkId() == null
                || !current.assignedNetworkId().equals(networkId.toString())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "预约不属于当前 Network Portal 上下文网点");
        }
    }

    /**
     * Network Portal 确认方只能是网点成员视角，禁止伪装 TECHNICIAN/CUSTOMER 等其它当事方。
     */
    private static String normalizeConfirmPartyType(String confirmedPartyType) {
        if (confirmedPartyType == null || confirmedPartyType.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "confirmedPartyType 无效");
        }
        String normalized = confirmedPartyType.trim().toUpperCase(Locale.ROOT);
        if ("TECHNICIAN".equals(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Network Portal 确认不得使用 TECHNICIAN confirmedPartyType");
        }
        if (!ALLOWED_CONFIRM_PARTY_TYPES.contains(normalized)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Network Portal 确认仅允许 NETWORK_MEMBER 或 NETWORK confirmedPartyType");
        }
        return normalized;
    }

    private static String requireConfirmPartyRef(CurrentPrincipal principal, String confirmedPartyRef) {
        String principalId = principal.principalId();
        if (confirmedPartyRef == null || confirmedPartyRef.isBlank()) {
            return principalId;
        }
        String normalized = confirmedPartyRef.trim();
        if (!normalized.equals(principalId)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Network Portal 确认 confirmedPartyRef 必须等于当前主体 principalId");
        }
        return principalId;
    }

    private void requireNetworkOwnedTask(String tenantId, UUID taskId, UUID networkId) {
        ActiveServiceResponsibility responsibility = responsibilities.find(tenantId, taskId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.ACCESS_DENIED,
                        "任务对本网点没有 ACTIVE NETWORK 责任"));
        if (responsibility.networkId() == null
                || !responsibility.networkId().equals(networkId.toString())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED,
                    "任务对本网点没有 ACTIVE NETWORK 责任");
        }
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
}
