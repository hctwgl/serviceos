package com.serviceos.dispatch.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfigureCapacityCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.ManualAssignServiceAssignmentCommand;
import com.serviceos.dispatch.api.ManualReassignTechnicianCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentReceipt;
import com.serviceos.dispatch.api.PrepareServiceAssignmentCommand;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * M144/M200/M367：将已 Implemented 的容量与激活 saga SPI 编排为 Admin/Portal 人工初派与改派用例。
 * <p>
 * 事务边界：双责任激活/改派同事务提交，避免 NETWORK 已 ACTIVE 而 TECHNICIAN 失败的半成品。
 * 幂等：子步骤键由 HTTP Idempotency-Key 派生，重复请求走 SPI 冻结回放。
 * M367/ADR-088 A1-B：TECHNICIAN 激活前按冻结 Bundle 定向目标硬校验师傅声明；不兼容 422。
 * M453：人工派网点提交前按冻结策略重新计算硬过滤候选，禁止自动创建/扩容 NETWORK 容量。
 * 明确不做：跨网点改派和普通人工覆盖不可覆盖硬规则。
 */
@Service
final class DefaultManualServiceAssignmentService implements ManualServiceAssignmentService {
    private static final int CAPACITY_HEADROOM = 50;
    private static final String ASSIGNMENT_CAPABILITY = "dispatch.assignment.manage";

    private final TaskFulfillmentContextService tasks;
    private final AuthorizationService authorization;
    private final CapacityAuthorityService capacities;
    private final ServiceAssignmentService assignments;
    private final ManualTechnicianClientKindGate clientKindGate;
    private final NetworkDispatchCandidateEvaluator networkCandidates;
    private final JdbcClient jdbc;
    private final Clock clock;

    DefaultManualServiceAssignmentService(
            TaskFulfillmentContextService tasks,
            AuthorizationService authorization,
            CapacityAuthorityService capacities,
            ServiceAssignmentService assignments,
            ManualTechnicianClientKindGate clientKindGate,
            NetworkDispatchCandidateEvaluator networkCandidates,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.tasks = tasks;
        this.authorization = authorization;
        this.capacities = capacities;
        this.assignments = assignments;
        this.clientKindGate = clientKindGate;
        this.networkCandidates = networkCandidates;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ManualServiceAssignmentReceipt manualAssign(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ManualAssignServiceAssignmentCommand command
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), command.taskId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        if (!"HUMAN".equals(task.taskKind())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Only a HUMAN Task supports manual service assignment");
        }

        // A1-B：在容量/激活前硬拒绝，避免半成品 NETWORK ACTIVE。
        clientKindGate.requireCompatible(
                principal, metadata.correlationId(), task, command.technicianAssigneeId());

        String key = metadata.idempotencyKey();
        ensureCapacity(principal, metadata, ResponsibilityLevel.NETWORK,
                command.networkAssigneeId(), command.businessType(), key + "-cap-network");
        ensureCapacity(principal, metadata, ResponsibilityLevel.TECHNICIAN,
                command.technicianAssigneeId(), command.businessType(), key + "-cap-technician");

        ServiceAssignmentReceipt network = activateLevel(
                principal, metadata, task.workOrderId(), command.taskId(),
                ResponsibilityLevel.NETWORK, command.networkAssigneeId(),
                command.businessType(), key + "-network");
        ServiceAssignmentReceipt technician = activateLevel(
                principal, metadata, task.workOrderId(), command.taskId(),
                ResponsibilityLevel.TECHNICIAN, command.technicianAssigneeId(),
                command.businessType(), key + "-technician");

        return new ManualServiceAssignmentReceipt(
                command.taskId(), task.workOrderId(),
                network.serviceAssignmentId(), technician.serviceAssignmentId(),
                command.networkAssigneeId(), command.technicianAssigneeId(),
                clock.instant());
    }

    @Override
    @Transactional
    public NetworkPortalAcceptAssignmentReceipt manualAssignNetwork(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID taskId,
            String networkAssigneeId,
            String businessType
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), taskId)
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        if (!"HUMAN".equals(task.taskKind())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Only a HUMAN Task supports network acceptance");
        }
        authorizeNetworkAssignment(principal, metadata.correlationId(), taskId);

        var existing = activeAssignee(principal.tenantId(), taskId, ResponsibilityLevel.NETWORK);
        if (existing.isPresent()) {
            if (!existing.get().assigneeId().equals(networkAssigneeId)) {
                throw new BusinessProblem(
                        ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                        "当前任务已经由其他责任网点承接");
            }
            return new NetworkPortalAcceptAssignmentReceipt(
                    taskId,
                    task.workOrderId(),
                    existing.get().serviceAssignmentId(),
                    networkAssigneeId,
                    clock.instant());
        }

        NetworkDispatchCandidateEvaluator.Evaluation evaluation =
                networkCandidates.evaluate(principal.tenantId(), task);
        if (!evaluation.workOrder().serviceProductCode().equals(businessType)) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "请求业务类型与工单权威服务类型不一致，请刷新工单后重试");
        }
        NetworkDispatchCandidateEvaluator.CandidateFacts facts =
                evaluation.requireAssignable(networkAssigneeId);

        String key = metadata.idempotencyKey();
        ServiceAssignmentReceipt network = activateLevelWithCapacityVersion(
                principal, metadata, task.workOrderId(), taskId,
                ResponsibilityLevel.NETWORK, networkAssigneeId,
                businessType, facts.capacity().version(), key + "-network");
        return new NetworkPortalAcceptAssignmentReceipt(
                taskId, task.workOrderId(),
                network.serviceAssignmentId(), networkAssigneeId,
                clock.instant());
    }

    /**
     * 幂等回放可能在进入底层 ServiceAssignment 之前直接返回既有责任，因此必须在读取并返回
     * 责任详情之前完成同等授权。Network Portal 委托沿用 NETWORK Scope，Admin 仍使用 TENANT Scope。
     */
    private void authorizeNetworkAssignment(
            CurrentPrincipal principal, String correlationId, UUID taskId
    ) {
        String networkScope = NetworkScopedDispatchAuthorization.currentNetworkId();
        AuthorizationRequest request = networkScope == null
                ? AuthorizationRequest.tenantCapability(
                ASSIGNMENT_CAPABILITY,
                principal.tenantId(),
                "ServiceAssignment",
                taskId.toString())
                : AuthorizationRequest.networkCapability(
                ASSIGNMENT_CAPABILITY,
                principal.tenantId(),
                "ServiceAssignment",
                taskId.toString(),
                networkScope);
        authorization.require(principal, request, correlationId);
    }

    @Override
    @Transactional
    public ManualServiceAssignmentReceipt reassignTechnician(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ManualReassignTechnicianCommand command
    ) {
        TaskFulfillmentContext task = tasks.find(principal.tenantId(), command.taskId())
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "Task does not exist"));
        if (!"HUMAN".equals(task.taskKind())) {
            throw new BusinessProblem(ProblemCode.TASK_STATE_CONFLICT,
                    "Only a HUMAN Task supports technician reassignment");
        }

        var activeNetwork = activeAssignee(principal.tenantId(), command.taskId(), ResponsibilityLevel.NETWORK)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "Task has no ACTIVE NETWORK responsibility"));
        if (!activeNetwork.assigneeId().equals(command.networkAssigneeId())) {
            throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                    "ACTIVE NETWORK responsibility belongs to another network");
        }

        var activeTech = activeAssignee(principal.tenantId(), command.taskId(), ResponsibilityLevel.TECHNICIAN)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "Task has no ACTIVE TECHNICIAN responsibility; use assign-technician"));
        if (activeTech.assigneeId().equals(command.technicianAssigneeId())) {
            return new ManualServiceAssignmentReceipt(
                    command.taskId(), task.workOrderId(),
                    activeNetwork.serviceAssignmentId(), activeTech.serviceAssignmentId(),
                    command.networkAssigneeId(), command.technicianAssigneeId(),
                    clock.instant());
        }

        // A1-B：改派目标师傅同样硬校验（幂等同师傅早退已跳过）。
        clientKindGate.requireCompatible(
                principal, metadata.correlationId(), task, command.technicianAssigneeId());

        String key = metadata.idempotencyKey();
        ensureCapacity(principal, metadata, ResponsibilityLevel.TECHNICIAN,
                command.technicianAssigneeId(), command.businessType(), key + "-cap-technician");

        ServiceAssignmentReceipt technician = reassignTechnicianLevel(
                principal, metadata, task.workOrderId(), command.taskId(),
                command.technicianAssigneeId(), command.businessType(),
                activeTech.serviceAssignmentId(), command.reasonCode(), key + "-technician");

        return new ManualServiceAssignmentReceipt(
                command.taskId(), task.workOrderId(),
                activeNetwork.serviceAssignmentId(), technician.serviceAssignmentId(),
                command.networkAssigneeId(), command.technicianAssigneeId(),
                clock.instant());
    }

    private ServiceAssignmentReceipt reassignTechnicianLevel(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID workOrderId,
            UUID taskId,
            String technicianAssigneeId,
            String businessType,
            UUID supersedesServiceAssignmentId,
            String reasonCode,
            String key
    ) {
        long capacityVersion = capacityVersion(
                principal.tenantId(), ResponsibilityLevel.TECHNICIAN, technicianAssigneeId, businessType);
        ServiceAssignmentReceipt pending = assignments.prepare(
                principal, child(metadata, key + "-prepare"),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), workOrderId, taskId, ResponsibilityLevel.TECHNICIAN,
                        technicianAssigneeId, businessType,
                        "decision://manual-reassign/" + key,
                        supersedesServiceAssignmentId, reasonCode, capacityVersion));
        UUID preparedId = UUID.randomUUID();
        assignments.confirmTaskPrepared(
                principal, child(metadata, key + "-task-prepared"),
                new ConfirmTaskAssignmentPreparedCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), taskId,
                        UUID.randomUUID(), preparedId, 1));
        ServiceAssignmentReceipt activated = assignments.activate(
                principal, child(metadata, key + "-activate"),
                new ActivateServiceAssignmentCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2,
                        "authority://manual-reassign/" + technicianAssigneeId, 1,
                        "fence://manual-reassign/" + technicianAssigneeId, "manual-reassign-geo-v1"));
        return assignments.complete(
                principal, child(metadata, key + "-complete"),
                new CompleteServiceAssignmentActivationCommand(
                        activated.sagaId(), activated.serviceAssignmentId(), preparedId, 3));
    }

    private ServiceAssignmentReceipt activateLevel(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID workOrderId,
            UUID taskId,
            ResponsibilityLevel level,
            String assigneeId,
            String businessType,
            String key
    ) {
        var existing = activeAssignee(principal.tenantId(), taskId, level);
        if (existing.isPresent()) {
            if (!existing.get().assigneeId().equals(assigneeId)) {
                throw new BusinessProblem(ProblemCode.SERVICE_ASSIGNMENT_CONFLICT,
                        "ACTIVE " + level + " responsibility already belongs to another assignee");
            }
            return new ServiceAssignmentReceipt(
                    existing.get().serviceAssignmentId(), existing.get().sagaId(), taskId,
                    existing.get().reservationId(), "ACTIVE", "COMPLETED",
                    existing.get().sagaVersion(), clock.instant());
        }

        long capacityVersion = capacityVersion(
                principal.tenantId(), level, assigneeId, businessType);
        return activateLevelWithCapacityVersion(
                principal,
                metadata,
                workOrderId,
                taskId,
                level,
                assigneeId,
                businessType,
                capacityVersion,
                key);
    }

    /**
     * 使用候选评估时读取的容量版本进入预占。若查询后容量发生竞争，容量权威会以版本冲突拒绝，
     * 整个外层事务回滚，不会产生无容量的责任关系。
     */
    private ServiceAssignmentReceipt activateLevelWithCapacityVersion(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID workOrderId,
            UUID taskId,
            ResponsibilityLevel level,
            String assigneeId,
            String businessType,
            long capacityVersion,
            String key
    ) {
        ServiceAssignmentReceipt pending = assignments.prepare(
                principal, child(metadata, key + "-prepare"),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), workOrderId, taskId, level, assigneeId, businessType,
                        "decision://admin-manual/" + key, null, null, capacityVersion));
        UUID preparedId = UUID.randomUUID();
        assignments.confirmTaskPrepared(
                principal, child(metadata, key + "-task-prepared"),
                new ConfirmTaskAssignmentPreparedCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), taskId,
                        UUID.randomUUID(), preparedId, 1));
        ServiceAssignmentReceipt activated = assignments.activate(
                principal, child(metadata, key + "-activate"),
                new ActivateServiceAssignmentCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2,
                        "authority://admin-manual/" + assigneeId, 1,
                        "fence://admin-manual/" + assigneeId, "admin-manual-geo-v1"));
        return assignments.complete(
                principal, child(metadata, key + "-complete"),
                new CompleteServiceAssignmentActivationCommand(
                        activated.sagaId(), activated.serviceAssignmentId(), preparedId, 3));
    }

    private void ensureCapacity(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ResponsibilityLevel level,
            String assigneeId,
            String businessType,
            String key
    ) {
        var existing = jdbc.sql("""
                        SELECT max_units AS "maxUnits", occupied_units AS "occupiedUnits",
                               version AS "version"
                          FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND responsibility_level = :level
                           AND assignee_id = :assigneeId AND business_type = :businessType
                        """)
                .param("tenantId", principal.tenantId())
                .param("level", level.name())
                .param("assigneeId", assigneeId)
                .param("businessType", businessType)
                .query(CapacityRow.class).optional();
        if (existing.isEmpty()) {
            capacities.configure(principal, child(metadata, key + "-create"),
                    new ConfigureCapacityCommand(level, assigneeId, businessType, CAPACITY_HEADROOM, 0));
            return;
        }
        CapacityRow row = existing.get();
        if (row.maxUnits() - row.occupiedUnits() >= 1) {
            return;
        }
        int nextMax = Math.max(row.maxUnits() + CAPACITY_HEADROOM, row.occupiedUnits() + CAPACITY_HEADROOM);
        capacities.configure(principal, child(metadata, key + "-expand"),
                new ConfigureCapacityCommand(level, assigneeId, businessType, nextMax, row.version()));
    }

    private long capacityVersion(
            String tenantId, ResponsibilityLevel level, String assigneeId, String businessType
    ) {
        return jdbc.sql("""
                        SELECT version FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND responsibility_level = :level
                           AND assignee_id = :assigneeId AND business_type = :businessType
                        """)
                .param("tenantId", tenantId)
                .param("level", level.name())
                .param("assigneeId", assigneeId)
                .param("businessType", businessType)
                .query(Long.class).single();
    }

    private java.util.Optional<ActiveRow> activeAssignee(
            String tenantId, UUID taskId, ResponsibilityLevel level
    ) {
        return jdbc.sql("""
                        SELECT a.service_assignment_id AS "serviceAssignmentId",
                               a.activation_saga_id AS "sagaId",
                               a.assignee_id AS "assigneeId",
                               r.capacity_reservation_id AS "reservationId",
                               coalesce(s.version, 0) AS "sagaVersion"
                          FROM dsp_service_assignment a
                          JOIN dsp_capacity_reservation r
                            ON r.tenant_id = a.tenant_id
                           AND r.service_assignment_id = a.service_assignment_id
                           AND r.status = 'CONFIRMED'
                          LEFT JOIN dsp_service_assignment_activation_saga s
                            ON s.tenant_id = a.tenant_id
                           AND s.activation_saga_id = a.activation_saga_id
                         WHERE a.tenant_id = :tenantId AND a.task_id = :taskId
                           AND a.responsibility_level = :level AND a.status = 'ACTIVE'
                         ORDER BY a.created_at
                         LIMIT 1
                        """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .param("level", level.name())
                .query(ActiveRow.class).optional();
    }

    private static CommandMetadata child(CommandMetadata metadata, String suffix) {
        return new CommandMetadata(metadata.correlationId(), metadata.idempotencyKey() + ":" + suffix);
    }

    private record CapacityRow(int maxUnits, int occupiedUnits, long version) {
    }

    private record ActiveRow(
            UUID serviceAssignmentId,
            UUID sagaId,
            String assigneeId,
            UUID reservationId,
            long sagaVersion
    ) {
    }
}
