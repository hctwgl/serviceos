package com.serviceos.evidence.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.RuleResolution;
import com.serviceos.configuration.api.RuleResolveCommand;
import com.serviceos.configuration.api.RuleRuntime;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.TaskFulfillmentContext;
import com.serviceos.task.api.TaskFulfillmentContextService;
import com.serviceos.workorder.api.WorkOrderExpressionContext;
import com.serviceos.workorder.api.WorkOrderExpressionContextQuery;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M325/M329/M330：基于 Task 冻结 {@code ruleRef} 的 RULE 求值与失败关闭阻断。
 *
 * <p>解析仍固定 {@code subjectType=EVIDENCE_REVIEW}/{@code stage=INTERNAL}，与资产声明及
 * Workflow 冻结引用一致。无 ruleRef 放行；BLOCK / REQUIRE_APPROVAL 拦截“继续前进”路径
 * （APPROVED / Snapshot 创建 / Task complete）。</p>
 */
@Component
final class FrozenTaskRuleEvaluator {
    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final RuleRuntime rules;
    private final AuditAppender audit;
    private final ReviewRuleDenyAuditor denyAuditor;
    private final Clock clock;

    FrozenTaskRuleEvaluator(
            TaskFulfillmentContextService tasks,
            WorkOrderExpressionContextQuery workOrderContexts,
            RuleRuntime rules,
            AuditAppender audit,
            ReviewRuleDenyAuditor denyAuditor,
            Clock clock
    ) {
        this.tasks = tasks;
        this.workOrderContexts = workOrderContexts;
        this.rules = rules;
        this.audit = audit;
        this.denyAuditor = denyAuditor;
        this.clock = clock;
    }

    /**
     * 对“继续履约”路径求值：无 ruleRef 放行；BLOCK / REQUIRE_APPROVAL 拒绝并独立提交审计。
     */
    void assertProceedAllowed(
            String tenantId,
            String actorId,
            String correlationId,
            UUID taskId,
            String capability,
            String aggregateType,
            String aggregateId
    ) {
        Optional<Evaluation> evaluation = resolve(tenantId, taskId);
        if (evaluation.isEmpty()) {
            return;
        }
        RuleResolution resolution = evaluation.get().resolution();
        String evidence = evaluation.get().evidence();
        Instant now = clock.instant();

        switch (resolution.decision()) {
            case "BLOCK" -> {
                denyAuditor.appendDenied(new AuditEntry(
                        UUID.randomUUID(), tenantId, actorId,
                        "REVIEW_RULE_BLOCKED", capability, aggregateType, aggregateId,
                        "DENY", List.of(), "review-rule-gate-v1", "BLOCK", null,
                        Sha256.digest(evidence), correlationId, now));
                throw new BusinessProblem(ProblemCode.REVIEW_RULE_BLOCKED,
                        "冻结 RULE 阻断：" + firstRejectReason(resolution));
            }
            case "REQUIRE_APPROVAL" -> {
                denyAuditor.appendDenied(new AuditEntry(
                        UUID.randomUUID(), tenantId, actorId,
                        "REVIEW_RULE_REQUIRE_APPROVAL", capability, aggregateType, aggregateId,
                        "DENY", List.of(), "review-rule-gate-v1", "REQUIRE_APPROVAL", null,
                        Sha256.digest(evidence), correlationId, now));
                throw new BusinessProblem(ProblemCode.REVIEW_RULE_REQUIRES_APPROVAL,
                        "冻结 RULE 要求 forceApprove：" + firstRejectReason(resolution));
            }
            case "PASS_WITH_WARNINGS" -> audit.append(new AuditEntry(
                    UUID.randomUUID(), tenantId, actorId,
                    "REVIEW_RULE_WARNED", capability, aggregateType, aggregateId,
                    "ALLOW", List.of(), "review-rule-gate-v1", "WARN", null,
                    Sha256.digest(evidence), correlationId, now));
            default -> audit.append(new AuditEntry(
                    UUID.randomUUID(), tenantId, actorId,
                    "REVIEW_RULE_PASSED", capability, aggregateType, aggregateId,
                    "ALLOW", List.of(), "review-rule-gate-v1", resolution.decision(), null,
                    Sha256.digest(evidence), correlationId, now));
        }
    }

    /** REJECTED 等非前进路径：仍求值并记 ALLOW，但不因 BLOCK 失败关闭。 */
    void auditRejectAllowed(
            String tenantId,
            String actorId,
            String correlationId,
            UUID taskId,
            String capability,
            String aggregateType,
            String aggregateId
    ) {
        Optional<Evaluation> evaluation = resolve(tenantId, taskId);
        if (evaluation.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        audit.append(new AuditEntry(
                UUID.randomUUID(), tenantId, actorId,
                "REVIEW_RULE_PASSED", capability, aggregateType, aggregateId,
                "ALLOW", List.of(), "review-rule-gate-v1", "REJECT_ALLOWED", null,
                Sha256.digest(evaluation.get().evidence()), correlationId, now));
    }

    private Optional<Evaluation> resolve(String tenantId, UUID taskId) {
        TaskFulfillmentContext task = tasks.find(tenantId, taskId)
                .orElseThrow(() -> new IllegalStateException("Task missing for fulfillment rule gate"));
        if (task.ruleRef() == null || task.ruleRef().isBlank()) {
            return Optional.empty();
        }
        if (task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            throw new IllegalStateException("Task with ruleRef missing frozen Bundle");
        }

        WorkOrderExpressionContext wo = workOrderContexts.find(tenantId, task.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder expression context missing for fulfillment rule gate"));
        ExpressionContext expressionContext = new ExpressionContext(
                new ExpressionContext.WorkOrderContext(
                        wo.clientCode(), wo.brandCode(), wo.serviceProductCode()),
                new ExpressionContext.RegionContext(
                        wo.provinceCode(), wo.cityCode(), wo.districtCode()),
                new ExpressionContext.TaskContext(task.stageCode(), task.taskType()));

        RuleResolution resolution = rules.resolve(new RuleResolveCommand(
                tenantId,
                task.configurationBundleId(),
                task.configurationBundleDigest(),
                task.ruleRef(),
                "EVIDENCE_REVIEW",
                "INTERNAL",
                expressionContext));

        String hitDigest = resolution.hits().stream()
                .map(hit -> hit.ruleCode() + ":" + hit.severity() + ":" + hit.rejectReasonCode())
                .collect(Collectors.joining("|"));
        String evidence = resolution.assetVersionId() + "|" + resolution.contentDigest()
                + "|" + resolution.decision() + "|" + hitDigest;
        return Optional.of(new Evaluation(resolution, evidence));
    }

    private static String firstRejectReason(RuleResolution resolution) {
        return resolution.hits().stream()
                .map(RuleResolution.RuleHit::rejectReasonCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(resolution.decision());
    }

    private record Evaluation(RuleResolution resolution, String evidence) {
    }
}
