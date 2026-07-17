package com.serviceos.dispatch.application;

import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfigureCapacityCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.ManualAssignServiceAssignmentCommand;
import com.serviceos.dispatch.api.ManualReassignTechnicianCommand;
import com.serviceos.dispatch.api.ManualServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ManualServiceAssignmentService;
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
 * M144/M200：将已 Implemented 的容量与激活 saga SPI 编排为 Admin/Portal 人工初派与改派用例。
 * <p>
 * 事务边界：双责任激活/改派同事务提交，避免 NETWORK 已 ACTIVE 而 TECHNICIAN 失败的半成品。
 * 幂等：子步骤键由 HTTP Idempotency-Key 派生，重复请求走 SPI 冻结回放。
 * 明确不做：硬过滤重跑、评分、DispatchDecision、ServiceNetwork 生命周期、跨网点改派。
 */
@Service
final class DefaultManualServiceAssignmentService implements ManualServiceAssignmentService {
    private static final int CAPACITY_HEADROOM = 50;

    private final TaskFulfillmentContextService tasks;
    private final CapacityAuthorityService capacities;
    private final ServiceAssignmentService assignments;
    private final JdbcClient jdbc;
    private final Clock clock;

    DefaultManualServiceAssignmentService(
            TaskFulfillmentContextService tasks,
            CapacityAuthorityService capacities,
            ServiceAssignmentService assignments,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.tasks = tasks;
        this.capacities = capacities;
        this.assignments = assignments;
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
