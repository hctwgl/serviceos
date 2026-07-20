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
import com.serviceos.dispatch.api.NetworkPortalAcceptAssignmentReceipt;
import com.serviceos.dispatch.api.PrepareServiceAssignmentCommand;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.DspCapacityCounter;
import com.serviceos.jooq.generated.tables.DspCapacityReservation;
import com.serviceos.jooq.generated.tables.DspServiceAssignment;
import com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.Record5;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.DspCapacityCounter.DSP_CAPACITY_COUNTER;
import static com.serviceos.jooq.generated.tables.DspCapacityReservation.DSP_CAPACITY_RESERVATION;
import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.DspServiceAssignmentActivationSaga.DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA;

/**
 * M144/M200/M367：将已 Implemented 的容量与激活 saga SPI 编排为 Admin/Portal 人工初派与改派用例。
 * <p>
 * 事务边界：双责任激活/改派同事务提交，避免 NETWORK 已 ACTIVE 而 TECHNICIAN 失败的半成品。
 * 幂等：子步骤键由 HTTP Idempotency-Key 派生，重复请求走 SPI 冻结回放。
 * M367/ADR-088 A1-B：TECHNICIAN 激活前按冻结 Bundle 定向目标硬校验师傅声明；不兼容 422。
 * 明确不做：评分、DispatchDecision、ServiceNetwork 生命周期、跨网点改派。
 */
@Service
final class JooqManualServiceAssignmentService implements ManualServiceAssignmentService {
    private static final int CAPACITY_HEADROOM = 50;

    private final TaskFulfillmentContextService tasks;
    private final CapacityAuthorityService capacities;
    private final ServiceAssignmentService assignments;
    private final ManualTechnicianClientKindGate clientKindGate;
    private final DSLContext dsl;
    private final Clock clock;

    JooqManualServiceAssignmentService(
            TaskFulfillmentContextService tasks,
            CapacityAuthorityService capacities,
            ServiceAssignmentService assignments,
            ManualTechnicianClientKindGate clientKindGate,
            DSLContext dsl,
            Clock clock
    ) {
        this.tasks = tasks;
        this.capacities = capacities;
        this.assignments = assignments;
        this.clientKindGate = clientKindGate;
        this.dsl = dsl;
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

        String key = metadata.idempotencyKey();
        ensureCapacity(principal, metadata, ResponsibilityLevel.NETWORK,
                networkAssigneeId, businessType, key + "-cap-network");
        ServiceAssignmentReceipt network = activateLevel(
                principal, metadata, task.workOrderId(), taskId,
                ResponsibilityLevel.NETWORK, networkAssigneeId,
                businessType, key + "-network");
        return new NetworkPortalAcceptAssignmentReceipt(
                taskId, task.workOrderId(),
                network.serviceAssignmentId(), networkAssigneeId,
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
        DspCapacityCounter counter = DSP_CAPACITY_COUNTER;
        Optional<CapacityRow> existing = dsl.select(
                        counter.MAX_UNITS, counter.OCCUPIED_UNITS, counter.VERSION)
                .from(counter)
                .where(counter.TENANT_ID.eq(principal.tenantId()))
                .and(counter.RESPONSIBILITY_LEVEL.eq(level.name()))
                .and(counter.ASSIGNEE_ID.eq(assigneeId))
                .and(counter.BUSINESS_TYPE.eq(businessType))
                .fetchOptional(JooqManualServiceAssignmentService::mapCapacityRow);
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
        DspCapacityCounter counter = DSP_CAPACITY_COUNTER;
        return dsl.select(counter.VERSION)
                .from(counter)
                .where(counter.TENANT_ID.eq(tenantId))
                .and(counter.RESPONSIBILITY_LEVEL.eq(level.name()))
                .and(counter.ASSIGNEE_ID.eq(assigneeId))
                .and(counter.BUSINESS_TYPE.eq(businessType))
                .fetchSingle(counter.VERSION);
    }

    private Optional<ActiveRow> activeAssignee(
            String tenantId, UUID taskId, ResponsibilityLevel level
    ) {
        DspServiceAssignment a = DSP_SERVICE_ASSIGNMENT.as("a");
        DspServiceAssignmentActivationSaga s = DSP_SERVICE_ASSIGNMENT_ACTIVATION_SAGA.as("s");
        DspCapacityReservation r = DSP_CAPACITY_RESERVATION.as("r");
        return dsl.select(
                        a.SERVICE_ASSIGNMENT_ID,
                        a.ACTIVATION_SAGA_ID,
                        a.ASSIGNEE_ID,
                        r.CAPACITY_RESERVATION_ID,
                        DSL.coalesce(s.VERSION, 0L))
                .from(a)
                .join(r)
                .on(r.TENANT_ID.eq(a.TENANT_ID))
                .and(r.SERVICE_ASSIGNMENT_ID.eq(a.SERVICE_ASSIGNMENT_ID))
                .and(r.STATUS.eq("CONFIRMED"))
                .leftJoin(s)
                .on(s.TENANT_ID.eq(a.TENANT_ID))
                .and(s.ACTIVATION_SAGA_ID.eq(a.ACTIVATION_SAGA_ID))
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.TASK_ID.eq(taskId))
                .and(a.RESPONSIBILITY_LEVEL.eq(level.name()))
                .and(a.STATUS.eq("ACTIVE"))
                .orderBy(a.CREATED_AT)
                .limit(1)
                .fetchOptional(JooqManualServiceAssignmentService::mapActiveRow);
    }

    private static CommandMetadata child(CommandMetadata metadata, String suffix) {
        return new CommandMetadata(metadata.correlationId(), metadata.idempotencyKey() + ":" + suffix);
    }

    private static CapacityRow mapCapacityRow(Record3<Integer, Integer, Long> row) {
        return new CapacityRow(row.value1(), row.value2(), row.value3());
    }

    private static ActiveRow mapActiveRow(Record5<UUID, UUID, String, UUID, Long> row) {
        return new ActiveRow(row.value1(), row.value2(), row.value3(), row.value4(), row.value5());
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
