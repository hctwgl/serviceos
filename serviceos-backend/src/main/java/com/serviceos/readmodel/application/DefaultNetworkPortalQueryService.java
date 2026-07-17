package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.NetworkActiveAssignmentQuery;
import com.serviceos.dispatch.api.NetworkActiveAssignmentView;
import com.serviceos.dispatch.api.NetworkCapacityCounterView;
import com.serviceos.dispatch.api.NetworkCapacitySummaryQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkMembershipView;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.PrincipalNetworkAffiliationQuery;
import com.serviceos.readmodel.api.NetworkPortalCapacityItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
import com.serviceos.readmodel.api.NetworkPortalTaskItem;
import com.serviceos.readmodel.api.NetworkPortalTechnicianItem;
import com.serviceos.readmodel.api.NetworkPortalWorkbenchView;
import com.serviceos.readmodel.api.NetworkPortalWorkOrderItem;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Network Portal 只读编排。
 *
 * <p>事务边界：只读；不写 Outbox/领域事实。鉴权顺序：解析可信上下文 → ACTIVE
 * NetworkMembership → NETWORK scope capability → 按 networkId 收敛数据。跨网点失败关闭。</p>
 */
@Service
final class DefaultNetworkPortalQueryService implements NetworkPortalQueryService {
    private static final String NETWORK_TASK_READ = "networkTask.read";
    private static final String TECHNICIAN_READ_OWN = "technician.readOwnNetwork";
    private static final String CONTEXT_PREFIX = "NETWORK|NETWORK|";

    private final PrincipalNetworkAffiliationQuery affiliations;
    private final AuthorizationService authorization;
    private final NetworkActiveAssignmentQuery assignments;
    private final NetworkCapacitySummaryQuery capacity;
    private final NetworkPortalTechnicianQuery technicians;
    private final TaskFulfillmentContextService tasks;
    private final Clock clock;

    DefaultNetworkPortalQueryService(
            PrincipalNetworkAffiliationQuery affiliations,
            AuthorizationService authorization,
            NetworkActiveAssignmentQuery assignments,
            NetworkCapacitySummaryQuery capacity,
            NetworkPortalTechnicianQuery technicians,
            TaskFulfillmentContextService tasks,
            Clock clock
    ) {
        this.affiliations = affiliations;
        this.authorization = authorization;
        this.assignments = assignments;
        this.capacity = capacity;
        this.technicians = technicians;
        this.tasks = tasks;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalWorkOrderItem> listWorkOrders(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        Map<UUID, NetworkPortalWorkOrderItem> byWorkOrder = new LinkedHashMap<>();
        for (NetworkActiveAssignmentView row : active) {
            TaskFulfillmentContext task = tasks.find(actor.tenantId(), row.taskId()).orElse(null);
            UUID projectId = task == null ? null : task.projectId();
            NetworkPortalWorkOrderItem existing = byWorkOrder.get(row.workOrderId());
            if (existing == null) {
                byWorkOrder.put(row.workOrderId(), new NetworkPortalWorkOrderItem(
                        row.workOrderId(),
                        projectId,
                        List.of(row.taskId()),
                        row.businessType(),
                        row.technicianId(),
                        row.effectiveFrom()));
            } else {
                List<UUID> taskIds = new ArrayList<>(existing.taskIds());
                if (!taskIds.contains(row.taskId())) {
                    taskIds.add(row.taskId());
                }
                byWorkOrder.put(row.workOrderId(), new NetworkPortalWorkOrderItem(
                        existing.workOrderId(),
                        existing.projectId() != null ? existing.projectId() : projectId,
                        taskIds,
                        existing.businessType(),
                        existing.technicianId() != null ? existing.technicianId() : row.technicianId(),
                        existing.effectiveFrom() != null ? existing.effectiveFrom() : row.effectiveFrom()));
            }
        }
        return new NetworkPortalPage<>(networkId, List.copyOf(byWorkOrder.values()), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalTaskItem> listTasks(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        List<NetworkPortalTaskItem> items = new ArrayList<>();
        for (NetworkActiveAssignmentView row : active) {
            TaskFulfillmentContext task = tasks.find(actor.tenantId(), row.taskId()).orElse(null);
            items.add(new NetworkPortalTaskItem(
                    row.taskId(),
                    row.workOrderId(),
                    task == null ? null : task.projectId(),
                    task == null ? null : task.taskType(),
                    task == null ? null : task.taskKind(),
                    task == null ? null : task.stageCode(),
                    task == null ? null : task.status(),
                    row.businessType(),
                    row.technicianId(),
                    row.effectiveFrom()));
        }
        return new NetworkPortalPage<>(networkId, List.copyOf(items), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalTechnicianItem> listTechnicians(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, TECHNICIAN_READ_OWN);
        List<NetworkPortalTechnicianView> rows = technicians.listActiveTechnicians(actor.tenantId(), networkId);
        List<NetworkPortalTechnicianItem> items = rows.stream()
                .map(row -> new NetworkPortalTechnicianItem(
                        row.membershipId(),
                        row.technicianProfileId(),
                        row.principalId(),
                        row.displayName(),
                        row.profileStatus(),
                        row.membershipStatus(),
                        row.validFrom(),
                        row.validTo()))
                .toList();
        return new NetworkPortalPage<>(networkId, items, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalPage<NetworkPortalCapacityItem> listCapacity(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        return new NetworkPortalPage<>(networkId, capacityItems(actor.tenantId(), networkId), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public NetworkPortalWorkbenchView workbench(
            CurrentPrincipal actor, String correlationId, String networkContextHeader
    ) {
        UUID networkId = requireAuthorizedNetwork(actor, correlationId, networkContextHeader, NETWORK_TASK_READ);
        List<NetworkActiveAssignmentView> active = assignments.listActiveForNetwork(
                actor.tenantId(), networkId.toString());
        Set<UUID> workOrders = new LinkedHashSet<>();
        for (NetworkActiveAssignmentView row : active) {
            workOrders.add(row.workOrderId());
        }
        int technicianCount = technicians.listActiveTechnicians(actor.tenantId(), networkId).size();
        Instant asOf = clock.instant();
        return new NetworkPortalWorkbenchView(
                networkId,
                workOrders.size(),
                active.size(),
                technicianCount,
                capacityItems(actor.tenantId(), networkId),
                asOf);
    }

    private List<NetworkPortalCapacityItem> capacityItems(String tenantId, UUID networkId) {
        List<NetworkCapacityCounterView> rows = capacity.listForNetwork(tenantId, networkId.toString());
        return rows.stream()
                .map(row -> new NetworkPortalCapacityItem(
                        row.capacityCounterId(),
                        row.businessType(),
                        row.maxUnits(),
                        row.occupiedUnits(),
                        row.availableUnits(),
                        row.version(),
                        row.updatedAt()))
                .toList();
    }

    /**
     * 解析并校验可信网点上下文。membership 无效用 PORTAL_CONTEXT_INVALID；
     * 成员有效但缺能力用 ACCESS_DENIED（authorization.require）。
     */
    private UUID requireAuthorizedNetwork(
            CurrentPrincipal actor,
            String correlationId,
            String networkContextHeader,
            String capability
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
                capability, actor.tenantId(), "ServiceNetwork", networkId.toString(), networkId.toString()),
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
            // 拒绝 TECHNICIAN|NETWORK|... 或其他 Portal 形态伪装
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
