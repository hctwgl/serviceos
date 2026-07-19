package com.serviceos.dispatch.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.DispatchCandidate;
import com.serviceos.configuration.api.DispatchResolution;
import com.serviceos.configuration.api.DispatchResolveCommand;
import com.serviceos.configuration.api.DispatchRuntime;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.dispatch.api.ActivateNetworkFromFrozenDispatchCommand;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.network.api.ServiceNetworkDirectoryQuery;
import com.serviceos.project.api.ProjectNetworkDirectoryQuery;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
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
 * M324：task.created → 冻结 DISPATCH → ACTIVE NETWORK ServiceAssignment。
 *
 * <p>无 dispatchPolicyRef：N/A 完成 Inbox。有策略但无存活候选或 requiresManualIntervention：
 * 失败关闭不写派单，审计 MANUAL。非空候选取 rank=1 激活 NETWORK（protocol v1）。
 * PARTIAL：候选 brand/region/business 使用 {@code *} 通配，直至 ServiceCoverage 落地。</p>
 */
@Service
final class DefaultTaskDispatchPolicyEventConsumer implements TaskDispatchPolicyEventConsumer {
    private static final String CONSUMER = "task.dispatch-policy.created.v1";
    private static final String SYSTEM_ACTOR = "system:dispatch-policy";

    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final ProjectNetworkDirectoryQuery projectNetworks;
    private final ServiceNetworkDirectoryQuery serviceNetworks;
    private final DispatchRuntime dispatchRuntime;
    private final ServiceAssignmentService assignments;
    private final InboxService inbox;
    private final AuditAppender audit;
    private final JdbcClient jdbc;
    private final Clock clock;

    DefaultTaskDispatchPolicyEventConsumer(
            TaskFulfillmentContextService tasks,
            WorkOrderExpressionContextQuery workOrderContexts,
            ProjectNetworkDirectoryQuery projectNetworks,
            ServiceNetworkDirectoryQuery serviceNetworks,
            DispatchRuntime dispatchRuntime,
            ServiceAssignmentService assignments,
            InboxService inbox,
            AuditAppender audit,
            JdbcClient jdbc,
            Clock clock
    ) {
        this.tasks = tasks;
        this.workOrderContexts = workOrderContexts;
        this.projectNetworks = projectNetworks;
        this.serviceNetworks = serviceNetworks;
        this.dispatchRuntime = dispatchRuntime;
        this.assignments = assignments;
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
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|POLICY_NOT_CONFIGURED"));
            return;
        }
        if (task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            throw new IllegalStateException("HUMAN task with dispatchPolicyRef missing frozen Bundle");
        }

        boolean alreadyAssigned = jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM dsp_service_assignment
                     WHERE tenant_id = :tenantId AND task_id = :taskId
                       AND responsibility_level = 'NETWORK' AND status = 'ACTIVE'
                )
                """)
                .param("tenantId", message.tenantId())
                .param("taskId", taskId)
                .query(Boolean.class)
                .single();
        if (alreadyAssigned) {
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
        List<String> projectNetworkIds = projectNetworks.listActiveNetworkIds(
                message.tenantId(), task.projectId(), asOf);
        List<String> activeNetworkIds = serviceNetworks.listActiveNetworkIds(
                message.tenantId(), projectNetworkIds);
        List<DispatchCandidate> candidates = new ArrayList<>();
        for (String networkId : activeNetworkIds) {
            CapacitySnapshot capacity = capacitySnapshot(
                    message.tenantId(), networkId, wo.serviceProductCode());
            if (capacity == null) {
                // 无容量计数：失败关闭排除，不自动 configure 扩容。
                continue;
            }
            candidates.add(new DispatchCandidate(
                    networkId,
                    true,
                    false,
                    true,
                    Set.of("*"),
                    Set.of("*"),
                    Set.of("*"),
                    capacity.remaining(),
                    0.0,
                    0.0,
                    0.0,
                    0.0));
        }

        DispatchResolution resolution = dispatchRuntime.resolve(new DispatchResolveCommand(
                message.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.dispatchPolicyRef(),
                expressionContext,
                candidates));

        String explanation = String.join("; ", resolution.explanations());
        if (explanation.length() > 1800) {
            explanation = explanation.substring(0, 1800);
        }
        String policyEvidence = resolution.assetVersionId() + "|" + resolution.contentDigest();

        if (resolution.requiresManualIntervention() || resolution.rankedCandidates().isEmpty()) {
            audit.append(new AuditEntry(
                    UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                    "SERVICE_DISPATCH_POLICY_MANUAL", "dispatch.assignment.manage",
                    "Task", taskId.toString(),
                    "ALLOW", List.of(), "dispatch-policy-runtime-v1", "MANUAL", null,
                    Sha256.digest(policyEvidence + "|" + explanation),
                    message.correlationId(), asOf));
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|MANUAL|" + policyEvidence));
            return;
        }

        DispatchResolution.RankedCandidate top = resolution.rankedCandidates().getFirst();
        CapacitySnapshot capacity = capacitySnapshot(
                message.tenantId(), top.candidateId(), wo.serviceProductCode());
        if (capacity == null || capacity.remaining() <= 0) {
            audit.append(new AuditEntry(
                    UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                    "SERVICE_DISPATCH_POLICY_MANUAL", "dispatch.assignment.manage",
                    "Task", taskId.toString(),
                    "ALLOW", List.of(), "dispatch-policy-runtime-v1", "MANUAL", null,
                    Sha256.digest(policyEvidence + "|CAPACITY_RACE|" + explanation),
                    message.correlationId(), asOf));
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|MANUAL|CAPACITY|" + policyEvidence));
            return;
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
                        capacity.version()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "SERVICE_DISPATCH_POLICY_APPLIED", "dispatch.assignment.manage",
                "Task", taskId.toString(),
                "ALLOW", List.of(), "dispatch-policy-runtime-v1", "APPLIED", null,
                Sha256.digest(policyEvidence + "|" + top.candidateId() + "|" + explanation),
                message.correlationId(), asOf));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(taskId + "|APPLIED|" + policyEvidence + "|" + top.candidateId()));
    }

    private CapacitySnapshot capacitySnapshot(String tenantId, String assigneeId, String businessType) {
        return jdbc.sql("""
                SELECT version, max_units AS "maxUnits", occupied_units AS "occupiedUnits"
                  FROM dsp_capacity_counter
                 WHERE tenant_id = :tenantId
                   AND responsibility_level = 'NETWORK'
                   AND assignee_id = :assigneeId
                   AND business_type = :businessType
                """)
                .param("tenantId", tenantId)
                .param("assigneeId", assigneeId)
                .param("businessType", businessType)
                .query((rs, rowNum) -> new CapacitySnapshot(
                        rs.getLong("version"),
                        rs.getInt("maxUnits") - rs.getInt("occupiedUnits")))
                .optional()
                .orElse(null);
    }

    private record CapacitySnapshot(long version, int remaining) {
    }
}
