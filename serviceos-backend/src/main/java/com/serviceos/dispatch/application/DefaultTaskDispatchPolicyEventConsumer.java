package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.DispatchCandidate;
import com.serviceos.configuration.api.DispatchResolution;
import com.serviceos.configuration.api.DispatchResolveCommand;
import com.serviceos.configuration.api.DispatchRuntime;
import com.serviceos.configuration.api.EffectiveDispatchClientKinds;
import com.serviceos.configuration.api.FrozenBundleClientCapabilityProbe;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.dispatch.api.ActivateNetworkFromFrozenDispatchCommand;
import com.serviceos.dispatch.api.ActivateTechnicianFromFrozenDispatchCommand;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.network.api.NetworkPortalTechnicianQuery;
import com.serviceos.network.api.NetworkPortalTechnicianView;
import com.serviceos.network.api.TechnicianPrincipalQuery;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.TaskAssignmentService;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * M324/M332/M337/M338：task.created → 冻结 DISPATCH → ACTIVE NETWORK，再 → ACTIVE TECHNICIAN。
 *
 * <p>无 dispatchPolicyRef：N/A 完成 Inbox。NETWORK 候选来自项目网点 ∩ ACTIVE 网点 ∩
 * ACTIVE ServiceCoverage（品牌/业务/省市区精确匹配）∩ 容量；无覆盖失败关闭 MANUAL。
 * M338：候选附带月度签约比例缺口（committedShare − actualShare，ORDER_COUNT）。
 * NETWORK 成功后解析网点内师傅；空池/无容量审计 TECHNICIAN MANUAL，保留 NETWORK。
 * TECHNICIAN 阶段仍用 {@code *} 通配候选（师傅覆盖未建模）。
 * M366/ADR-088：自动 TECHNICIAN 池按冻结 Bundle FORM∩EVIDENCE 定向目标硬过滤师傅声明。</p>
 */
@Service
final class DefaultTaskDispatchPolicyEventConsumer implements TaskDispatchPolicyEventConsumer {
    private static final String CONSUMER = "task.dispatch-policy.created.v1";
    private static final String SYSTEM_ACTOR = "system:dispatch-policy";
    private static final String CLIENT_KIND_TARGET_EMPTY = "CLIENT_KIND_TARGET_EMPTY";

    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final NetworkDispatchCandidateEvaluator networkCandidates;
    private final NetworkPortalTechnicianQuery technicians;
    private final TechnicianPrincipalQuery technicianPrincipals;
    private final FrozenBundleClientCapabilityProbe clientCapabilityProbe;
    private final DispatchRuntime dispatchRuntime;
    private final ServiceAssignmentService assignments;
    private final TaskAssignmentService taskAssignments;
    private final InboxService inbox;
    private final AuditAppender audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    DefaultTaskDispatchPolicyEventConsumer(
            TaskFulfillmentContextService tasks,
            WorkOrderExpressionContextQuery workOrderContexts,
            NetworkDispatchCandidateEvaluator networkCandidates,
            NetworkPortalTechnicianQuery technicians,
            TechnicianPrincipalQuery technicianPrincipals,
            FrozenBundleClientCapabilityProbe clientCapabilityProbe,
            DispatchRuntime dispatchRuntime,
            ServiceAssignmentService assignments,
            TaskAssignmentService taskAssignments,
            InboxService inbox,
            AuditAppender audit,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.tasks = tasks;
        this.workOrderContexts = workOrderContexts;
        this.networkCandidates = networkCandidates;
        this.technicians = technicians;
        this.technicianPrincipals = technicianPrincipals;
        this.clientCapabilityProbe = clientCapabilityProbe;
        this.dispatchRuntime = dispatchRuntime;
        this.assignments = assignments;
        this.taskAssignments = taskAssignments;
        this.inbox = inbox;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void applyFrozenPolicy(OutboxMessage message, UUID taskId, Instant createdAt) {
        InboxDecision decision = inbox.begin(
                message.tenantId(), CONSUMER, message.eventId(),
                message.schemaVersion(), message.payloadDigest());
        if (decision.kind() == InboxDecision.Kind.REPLAY) {
            return;
        }

        TaskFulfillmentContext task = tasks.find(message.tenantId(), taskId)
                .orElseThrow(() -> new IllegalStateException("Task does not exist for dispatch policy"));
        if (!"HUMAN".equals(task.taskKind())) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NOT_HUMAN"));
            return;
        }
        if (task.dispatchPolicyRef() == null || task.dispatchPolicyRef().isBlank()) {
            if (hasActiveAssignment(message.tenantId(), taskId, "TECHNICIAN")) {
                inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                        Sha256.digest(taskId + "|ALREADY_TECHNICIAN_ASSIGNED"));
                return;
            }
            if (inheritWorkOrderTechnician(message, task)) {
                return;
            }
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|POLICY_NOT_CONFIGURED"));
            return;
        }
        if (task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            throw new IllegalStateException("HUMAN task with dispatchPolicyRef missing frozen Bundle");
        }

        boolean networkActive = hasActiveAssignment(
                message.tenantId(), taskId, "NETWORK");
        boolean technicianActive = hasActiveAssignment(
                message.tenantId(), taskId, "TECHNICIAN");
        if (networkActive && technicianActive) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|ALREADY_ASSIGNED"));
            return;
        }

        WorkOrderExpressionContext wo = workOrderContexts.find(message.tenantId(), task.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder expression context missing for dispatch policy"));
        ExpressionContext expressionContext = new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        wo.clientCode(), wo.brandCode(), wo.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        wo.provinceCode(), wo.cityCode(), wo.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()));
        Instant asOf = clock.instant();

        String networkAssigneeId;
        String networkPolicyEvidence;
        if (!networkActive) {
            NetworkActivation network = activateNetwork(message, task, wo, asOf);
            if (network == null) {
                return;
            }
            networkAssigneeId = network.networkAssigneeId();
            networkPolicyEvidence = network.policyEvidence();
        } else {
            networkAssigneeId = activeAssigneeId(message.tenantId(), taskId, "NETWORK");
            networkPolicyEvidence = "EXISTING_NETWORK|" + networkAssigneeId;
        }

        if (technicianActive) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|ALREADY_TECH|" + networkPolicyEvidence));
            return;
        }

        activateTechnician(
                message, task, wo, expressionContext, asOf,
                networkAssigneeId, networkPolicyEvidence);
    }

    private boolean inheritWorkOrderTechnician(
            OutboxMessage message,
            TaskFulfillmentContext task
    ) {
        WorkOrderTechnicianAssignment previous = jdbc.sql("""
                        SELECT service_assignment_id AS "serviceAssignmentId",
                               assignee_id AS "technicianProfileId"
                          FROM dsp_service_assignment
                         WHERE tenant_id = :tenantId
                           AND work_order_id = :workOrderId
                           AND responsibility_level = 'TECHNICIAN'
                           AND status = 'ACTIVE'
                         ORDER BY (task_id = :taskId) DESC,
                                  effective_from DESC,
                                  created_at DESC
                         LIMIT 1
                        """)
                .param("tenantId", message.tenantId())
                .param("workOrderId", task.workOrderId())
                .param("taskId", task.taskId())
                .query(WorkOrderTechnicianAssignment.class)
                .optional()
                .orElse(null);
        if (previous == null) {
            return false;
        }
        String principalId = technicianPrincipals
                .findActivePrincipalId(message.tenantId(), previous.technicianProfileId())
                .orElseThrow(() -> new IllegalStateException(
                        "ACTIVE work-order technician has no ACTIVE login principal"));

        /*
         * 网点和师傅责任属于整张工单，容量在首个现场任务指派时已预占；后续人工阶段只冻结
         * 新 Task 的候选执行权，不重复创建 ServiceAssignment 或占用容量。Task 版本与
         * ServiceAssignment 来源共同幂等，失败会让 Inbox 重试而不是把任务暴露为无人可接。
         */
        taskAssignments.assignCandidateFromServiceAssignment(
                message.tenantId(),
                message.correlationId(),
                new AssignTaskCandidatesCommand(
                        task.taskId(),
                        task.version(),
                        List.of(principalId),
                        AssignmentSourceType.SYSTEM,
                        previous.serviceAssignmentId().toString()));
        Instant now = clock.instant();
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "SERVICE_ASSIGNMENT_RESPONSIBILITY_INHERITED", "dispatch.assignment.manage",
                "Task", task.taskId().toString(),
                "ALLOW", List.of(), "service-assignment-runtime-v1", "INHERITED", null,
                Sha256.digest(previous.serviceAssignmentId() + "|" + task.taskId()),
                message.correlationId(), now));
        inbox.complete(
                message.tenantId(),
                CONSUMER,
                message.eventId(),
                Sha256.digest(task.taskId() + "|INHERITED|"
                        + previous.serviceAssignmentId()));
        return true;
    }

    private NetworkActivation activateNetwork(
            OutboxMessage message,
            TaskFulfillmentContext task,
            WorkOrderExpressionContext wo,
            Instant asOf
    ) {
        UUID taskId = task.taskId();
        NetworkDispatchCandidateEvaluator.Evaluation evaluation =
                networkCandidates.evaluate(message.tenantId(), task);
        DispatchResolution resolution = evaluation.resolution();
        String explanation = clipped(String.join("; ", resolution.explanations()));
        String policyEvidence = resolution.assetVersionId() + "|" + resolution.contentDigest();

        if (resolution.requiresManualIntervention() || resolution.rankedCandidates().isEmpty()) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_POLICY_MANUAL",
                    policyEvidence, explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|MANUAL|" + policyEvidence));
            return null;
        }

        DispatchResolution.RankedCandidate top = resolution.rankedCandidates().getFirst();
        NetworkDispatchCandidateEvaluator.CandidateFacts facts =
                evaluation.candidateFacts().get(top.candidateId());
        if (facts == null || facts.capacity().remaining() <= 0) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_POLICY_MANUAL",
                    policyEvidence + "|CAPACITY_RACE", explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|MANUAL|CAPACITY|" + policyEvidence));
            return null;
        }

        String sourceDecisionId = "decision://dp/" + resolution.assetVersionId()
                + "/" + top.candidateId();
        assignments.activateNetworkFromFrozenDispatchPolicy(
                message.tenantId(),
                message.correlationId(),
                new ActivateNetworkFromFrozenDispatchCommand(
                        task.workOrderId(),
                        taskId,
                        top.candidateId(),
                        wo.serviceProductCode(),
                        sourceDecisionId,
                        facts.capacity().version()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "SERVICE_DISPATCH_POLICY_APPLIED", "dispatch.assignment.manage",
                "Task", taskId.toString(),
                "ALLOW", List.of(), "dispatch-policy-runtime-v1", "APPLIED", null,
                Sha256.digest(policyEvidence + "|" + top.candidateId() + "|" + explanation),
                message.correlationId(), asOf));
        return new NetworkActivation(top.candidateId(), policyEvidence);
    }

    private void activateTechnician(
            OutboxMessage message,
            TaskFulfillmentContext task,
            WorkOrderExpressionContext wo,
            ExpressionContext expressionContext,
            Instant asOf,
            String networkAssigneeId,
            String networkPolicyEvidence
    ) {
        UUID taskId = task.taskId();
        UUID networkId = UUID.fromString(networkAssigneeId);
        // ADR-088 A1-R/A4-R：仅自动 TECHNICIAN 池硬过滤；Manual/Network assign 不走此路径。
        EffectiveDispatchClientKinds kindTarget = clientCapabilityProbe.resolveDispatchTargetClientKinds(
                message.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.formRef());
        List<NetworkPortalTechnicianView> techViews = technicians.listActiveTechnicians(
                message.tenantId(), networkId);
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (NetworkPortalTechnicianView tech : techViews) {
            if (kindTarget.applyFilter()
                    && !DispatchClientKindCompatibility.matchesDeclaredClientKinds(
                            tech.supportedClientKinds(), kindTarget.targetKinds())) {
                continue;
            }
            String assigneeId = tech.technicianProfileId().toString();
            CapacitySnapshot capacity = capacitySnapshot(
                    message.tenantId(), "TECHNICIAN", assigneeId, wo.serviceProductCode());
            if (capacity == null) {
                continue;
            }
            candidates.add(wildcardCandidate(assigneeId, capacity.remaining()));
        }

        if (kindTarget.applyFilter() && candidates.isEmpty()) {
            // A3-R：可解释原因写入 error_code，便于运营与 IT 断言（不仅哈希进 request_digest）。
            String policyEvidence = "client-kind|" + String.join(",", kindTarget.targetKinds());
            audit.append(new AuditEntry(
                    UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                    "SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL", "dispatch.assignment.manage",
                    "Task", taskId.toString(),
                    "ALLOW", List.of(), "dispatch-policy-runtime-v1", "MANUAL",
                    CLIENT_KIND_TARGET_EMPTY,
                    Sha256.digest(policyEvidence + "|" + CLIENT_KIND_TARGET_EMPTY),
                    message.correlationId(), asOf));
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NET_APPLIED|TECH_MANUAL|" + networkPolicyEvidence
                            + "|" + policyEvidence + "|" + CLIENT_KIND_TARGET_EMPTY));
            return;
        }

        DispatchResolution resolution = dispatchRuntime.resolve(new DispatchResolveCommand(
                message.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.dispatchPolicyRef(),
                expressionContext,
                candidates));
        String explanation = clipped(String.join("; ", resolution.explanations()));
        String policyEvidence = resolution.assetVersionId() + "|" + resolution.contentDigest();

        if (resolution.requiresManualIntervention() || resolution.rankedCandidates().isEmpty()) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL",
                    policyEvidence, explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NET_APPLIED|TECH_MANUAL|" + networkPolicyEvidence
                            + "|" + policyEvidence));
            return;
        }

        DispatchResolution.RankedCandidate top = resolution.rankedCandidates().getFirst();
        CapacitySnapshot capacity = capacitySnapshot(
                message.tenantId(), "TECHNICIAN", top.candidateId(), wo.serviceProductCode());
        if (capacity == null || capacity.remaining() <= 0) {
            auditManual(message, taskId, asOf, "SERVICE_DISPATCH_TECHNICIAN_POLICY_MANUAL",
                    policyEvidence + "|CAPACITY_RACE", explanation);
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NET_APPLIED|TECH_MANUAL|CAPACITY|"
                            + networkPolicyEvidence + "|" + policyEvidence));
            return;
        }

        String sourceDecisionId = "decision://dpt/" + resolution.assetVersionId()
                + "/" + top.candidateId();
        assignments.activateTechnicianFromFrozenDispatchPolicy(
                message.tenantId(),
                message.correlationId(),
                new ActivateTechnicianFromFrozenDispatchCommand(
                        task.workOrderId(),
                        taskId,
                        top.candidateId(),
                        wo.serviceProductCode(),
                        sourceDecisionId,
                        capacity.version()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "SERVICE_DISPATCH_TECHNICIAN_POLICY_APPLIED", "dispatch.assignment.manage",
                "Task", taskId.toString(),
                "ALLOW", List.of(), "dispatch-policy-runtime-v1", "TECH_APPLIED", null,
                Sha256.digest(policyEvidence + "|" + top.candidateId() + "|" + explanation),
                message.correlationId(), asOf));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(taskId + "|APPLIED|" + networkPolicyEvidence + "|"
                        + policyEvidence + "|" + top.candidateId()));
    }

    private void auditManual(
            OutboxMessage message,
            UUID taskId,
            Instant asOf,
            String action,
            String policyEvidence,
            String explanation
    ) {
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                action, "dispatch.assignment.manage",
                "Task", taskId.toString(),
                "ALLOW", List.of(), "dispatch-policy-runtime-v1", "MANUAL", null,
                Sha256.digest(policyEvidence + "|" + explanation),
                message.correlationId(), asOf));
    }

    private boolean hasActiveAssignment(String tenantId, UUID taskId, String level) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM dsp_service_assignment
                     WHERE tenant_id = :tenantId AND task_id = :taskId
                       AND responsibility_level = :level AND status = 'ACTIVE'
                )
                """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .param("level", level)
                .query(Boolean.class)
                .single();
    }

    private String activeAssigneeId(String tenantId, UUID taskId, String level) {
        return jdbc.sql("""
                SELECT assignee_id FROM dsp_service_assignment
                 WHERE tenant_id = :tenantId AND task_id = :taskId
                   AND responsibility_level = :level AND status = 'ACTIVE'
                 ORDER BY created_at
                 LIMIT 1
                """)
                .param("tenantId", tenantId)
                .param("taskId", taskId)
                .param("level", level)
                .query(String.class)
                .single();
    }

    private CapacitySnapshot capacitySnapshot(
            String tenantId, String level, String assigneeId, String businessType
    ) {
        return jdbc.sql("""
                SELECT version, max_units AS "maxUnits", occupied_units AS "occupiedUnits"
                  FROM dsp_capacity_counter
                 WHERE tenant_id = :tenantId
                   AND responsibility_level = :level
                   AND assignee_id = :assigneeId
                   AND business_type = :businessType
                """)
                .param("tenantId", tenantId)
                .param("level", level)
                .param("assigneeId", assigneeId)
                .param("businessType", businessType)
                .query((rs, rowNum) -> new CapacitySnapshot(
                        rs.getLong("version"),
                        rs.getInt("maxUnits") - rs.getInt("occupiedUnits")))
                .optional()
                .orElse(null);
    }

    private static DispatchCandidate wildcardCandidate(String candidateId, int remaining) {
        return new DispatchCandidate(
                candidateId,
                true,
                false,
                true,
                Set.of("*"),
                Set.of("*"),
                Set.of("*"),
                remaining,
                0.0,
                0.0,
                0.0,
                0.0);
    }

    private static String clipped(String explanation) {
        return explanation.length() > 1800 ? explanation.substring(0, 1800) : explanation;
    }

    private record CapacitySnapshot(long version, int remaining) {
    }

    private record NetworkActivation(String networkAssigneeId, String policyEvidence) {
    }

    private record WorkOrderTechnicianAssignment(
            UUID serviceAssignmentId,
            String technicianProfileId
    ) {
    }
}
