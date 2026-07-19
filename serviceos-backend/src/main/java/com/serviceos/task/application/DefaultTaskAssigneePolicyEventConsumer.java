package com.serviceos.task.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.RolePrincipalDirectoryQuery;
import com.serviceos.configuration.api.AssigneePolicyResolution;
import com.serviceos.configuration.api.AssigneePolicyResolveCommand;
import com.serviceos.configuration.api.AssigneePolicyRuntime;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.AssignTaskCandidatesCommand;
import com.serviceos.task.api.AssignmentSourceType;
import com.serviceos.task.api.TaskAssignmentService;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * M323：task.created → 冻结 ASSIGNEE_POLICY → TaskAssignment 候选快照。
 *
 * <p>无 assigneePolicyRef：N/A 完成 Inbox。有策略但候选为空或 requiresManualIntervention：
 * 失败关闭不写候选，审计 MANUAL，Task 保持 READY 供人工分配。非空候选则
 * {@link TaskAssignmentService#assignCandidatesFromFrozenPolicy}。</p>
 */
@Service
final class DefaultTaskAssigneePolicyEventConsumer implements TaskAssigneePolicyEventConsumer {
    private static final String CONSUMER = "task.assignee-policy.created.v1";
    private static final String SYSTEM_ACTOR = "system:assignee-policy";

    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final RolePrincipalDirectoryQuery rolePrincipals;
    private final AssigneePolicyRuntime assigneePolicies;
    private final TaskAssignmentService assignments;
    private final InboxService inbox;
    private final AuditAppender audit;
    private final Clock clock;

    DefaultTaskAssigneePolicyEventConsumer(
            TaskFulfillmentContextService tasks,
            WorkOrderExpressionContextQuery workOrderContexts,
            RolePrincipalDirectoryQuery rolePrincipals,
            AssigneePolicyRuntime assigneePolicies,
            TaskAssignmentService assignments,
            InboxService inbox,
            AuditAppender audit,
            Clock clock
    ) {
        this.tasks = tasks;
        this.workOrderContexts = workOrderContexts;
        this.rolePrincipals = rolePrincipals;
        this.assigneePolicies = assigneePolicies;
        this.assignments = assignments;
        this.inbox = inbox;
        this.audit = audit;
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
                .orElseThrow(() -> new IllegalStateException("Task does not exist for assignee policy"));
        if (!"HUMAN".equals(task.taskKind())) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NOT_HUMAN"));
            return;
        }
        if (task.assigneePolicyRef() == null || task.assigneePolicyRef().isBlank()) {
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|POLICY_NOT_CONFIGURED"));
            return;
        }
        if (task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            throw new IllegalStateException("HUMAN task with assigneePolicyRef missing frozen Bundle");
        }
        if (!"READY".equals(task.status())) {
            // 幂等：若已有候选写入导致版本前进，仍完成 Inbox。
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|NOT_READY|" + task.status()));
            return;
        }

        WorkOrderExpressionContext wo = workOrderContexts.find(message.tenantId(), task.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder expression context missing for assignee policy"));
        ExpressionContext expressionContext = new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        wo.clientCode(), wo.brandCode(), wo.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        wo.provinceCode(), wo.cityCode(), wo.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()));

        Instant asOf = clock.instant();
        Map<String, List<String>> principalsByRole = rolePrincipals.listActivePrincipalsGroupedByRoleCode(
                message.tenantId(), task.projectId(), asOf);
        AssigneePolicyResolution resolution = assigneePolicies.resolve(new AssigneePolicyResolveCommand(
                message.tenantId(),
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.assigneePolicyRef(),
                expressionContext,
                principalsByRole));

        String explanation = String.join("; ", resolution.explanations());
        if (explanation.length() > 1800) {
            explanation = explanation.substring(0, 1800);
        }
        String policyEvidence = resolution.assetVersionId() + "|" + resolution.contentDigest();

        if (resolution.requiresManualIntervention()
                || resolution.resolvedUserPrincipalIds().isEmpty()) {
            audit.append(new AuditEntry(
                    UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                    "TASK_ASSIGNEE_POLICY_MANUAL", "task.assign", "Task", taskId.toString(),
                    "ALLOW", List.of(), "assignee-policy-runtime-v1", "MANUAL", null,
                    Sha256.digest(policyEvidence + "|" + explanation),
                    message.correlationId(), asOf));
            inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                    Sha256.digest(taskId + "|MANUAL|" + policyEvidence));
            return;
        }

        assignments.assignCandidatesFromFrozenPolicy(
                message.tenantId(),
                message.correlationId(),
                new AssignTaskCandidatesCommand(
                        taskId,
                        task.version(),
                        resolution.resolvedUserPrincipalIds(),
                        AssignmentSourceType.ASSIGNEE_POLICY,
                        resolution.assetVersionId().toString()));
        audit.append(new AuditEntry(
                UUID.randomUUID(), message.tenantId(), SYSTEM_ACTOR,
                "TASK_ASSIGNEE_POLICY_APPLIED", "task.assign", "Task", taskId.toString(),
                "ALLOW", List.of(), "assignee-policy-runtime-v1", "APPLIED", null,
                Sha256.digest(policyEvidence + "|" + explanation),
                message.correlationId(), asOf));
        inbox.complete(message.tenantId(), CONSUMER, message.eventId(),
                Sha256.digest(taskId + "|APPLIED|" + policyEvidence
                        + "|" + resolution.resolvedUserPrincipalIds().size()));
    }
}
