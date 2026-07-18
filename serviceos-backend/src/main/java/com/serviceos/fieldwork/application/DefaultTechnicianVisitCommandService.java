package com.serviceos.fieldwork.application;

import com.serviceos.appointment.api.AppointmentVisitContext;
import com.serviceos.appointment.api.AppointmentVisitLifecycleService;
import com.serviceos.fieldwork.api.CheckInVisitCommand;
import com.serviceos.fieldwork.api.CheckOutVisitCommand;
import com.serviceos.fieldwork.api.InterruptVisitCommand;
import com.serviceos.fieldwork.api.TechnicianVisitCommandService;
import com.serviceos.fieldwork.api.VisitCommandReceipt;
import com.serviceos.fieldwork.api.VisitService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.network.api.TechnicianProfileView;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Technician Portal 上下文适配器；领域写仍由 {@link VisitService} 维护单事务不变量。 */
@Service
final class DefaultTechnicianVisitCommandService implements TechnicianVisitCommandService {
    private static final String CONTEXT_PREFIX = "TECHNICIAN|NETWORK|";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AppointmentVisitLifecycleService appointments;
    private final VisitRepository repository;
    private final VisitService visits;
    private final Clock clock;

    DefaultTechnicianVisitCommandService(
            PrincipalNetworkAffiliationQuery affiliations,
            AppointmentVisitLifecycleService appointments,
            VisitRepository repository,
            VisitService visits,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.appointments = appointments;
        this.repository = repository;
        this.visits = visits;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VisitCommandReceipt checkIn(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            CheckInVisitCommand command
    ) {
        UUID contextNetworkId = requireAuthorizedContext(principal, technicianContextHeader);
        AppointmentVisitContext appointment = appointments.find(principal.tenantId(), command.appointmentId())
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "预约不存在"));
        requireResourceNetwork(contextNetworkId, appointment.assignedNetworkId());
        return visits.checkIn(principal, metadata, command);
    }

    @Override
    @Transactional
    public VisitCommandReceipt checkOut(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            CheckOutVisitCommand command
    ) {
        requireVisitNetwork(principal, technicianContextHeader, command.visitId());
        return visits.checkOut(principal, metadata, command);
    }

    @Override
    @Transactional
    public VisitCommandReceipt interrupt(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            String technicianContextHeader,
            InterruptVisitCommand command
    ) {
        requireVisitNetwork(principal, technicianContextHeader, command.visitId());
        return visits.interrupt(principal, metadata, command);
    }

    private void requireVisitNetwork(
            CurrentPrincipal principal, String technicianContextHeader, UUID visitId
    ) {
        UUID contextNetworkId = requireAuthorizedContext(principal, technicianContextHeader);
        VisitAggregate visit = repository.findById(principal.tenantId(), visitId)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "上门记录不存在"));
        requireResourceNetwork(contextNetworkId, visit.networkId());
    }

    /**
     * 先证明 JWT 主体与 ACTIVE TechnicianProfile/网点关系一致；随后 VisitService 还会重新证明当前责任。
     * 两层校验故意都保留，避免 Portal context 和领域责任任一变化时继续写入。
     */
    private UUID requireAuthorizedContext(CurrentPrincipal principal, String header) {
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
        return networkId;
    }

    private static void requireResourceNetwork(UUID contextNetworkId, String resourceNetworkId) {
        if (!contextNetworkId.toString().equals(resourceNetworkId)) {
            // 不区分资源属于哪个其他网点，避免资源 ID 探测形成旁路。
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "现场资源不存在");
        }
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
