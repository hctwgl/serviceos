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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M325：INTERNAL ReviewCase.decide 前的冻结 RULE 门禁。
 *
 * <p>无 ruleRef：放行。forceApprove 调用方不进入本门禁。
 * BLOCK / REQUIRE_APPROVAL 仅拦截 APPROVED；REJECTED 始终放行。
 * REQUIRE_MANUAL / PASS / PASS_WITH_WARNINGS 放行（人工裁决即 manual）。</p>
 */
@Component
final class ReviewRuleGate {
    private final TaskFulfillmentContextService tasks;
    private final WorkOrderExpressionContextQuery workOrderContexts;
    private final RuleRuntime rules;
    private final AuditAppender audit;
    private final ReviewRuleDenyAuditor denyAuditor;
    private final Clock clock;

    ReviewRuleGate(
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

    void assertDecideAllowed(
            String tenantId,
            String actorId,
            String correlationId,
            UUID reviewCaseId,
            UUID taskId,
            String proposedDecision
    ) {
        TaskFulfillmentContext task = tasks.find(tenantId, taskId)
                .orElseThrow(() -> new IllegalStateException("Task missing for review rule gate"));
        if (task.ruleRef() == null || task.ruleRef().isBlank()) {
            return;
        }
        if (task.configurationBundleId() == null
                || task.configurationBundleDigest() == null
                || task.configurationBundleDigest().isBlank()) {
            throw new IllegalStateException("Task with ruleRef missing frozen Bundle");
        }

        WorkOrderExpressionContext wo = workOrderContexts.find(tenantId, task.workOrderId())
                .orElseThrow(() -> new IllegalStateException(
                        "WorkOrder expression context missing for review rule gate"));
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

        Instant now = clock.instant();
        String hitDigest = resolution.hits().stream()
                .map(hit -> hit.ruleCode() + ":" + hit.severity() + ":" + hit.rejectReasonCode())
                .collect(Collectors.joining("|"));
        String evidence = resolution.assetVersionId() + "|" + resolution.contentDigest()
                + "|" + resolution.decision() + "|" + hitDigest;

        if ("REJECTED".equals(proposedDecision)) {
            audit.append(new AuditEntry(
                    UUID.randomUUID(), tenantId, actorId,
                    "REVIEW_RULE_PASSED", "evidence.review", "ReviewCase", reviewCaseId.toString(),
                    "ALLOW", List.of(), "review-rule-gate-v1", "REJECT_ALLOWED", null,
                    Sha256.digest(evidence), correlationId, now));
            return;
        }

        switch (resolution.decision()) {
            case "BLOCK" -> {
                // 拒绝路径必须独立提交审计，否则 decide 回滚会抹掉阻断证据。
                denyAuditor.appendDenied(new AuditEntry(
                        UUID.randomUUID(), tenantId, actorId,
                        "REVIEW_RULE_BLOCKED", "evidence.review", "ReviewCase",
                        reviewCaseId.toString(),
                        "DENY", List.of(), "review-rule-gate-v1", "BLOCK", null,
                        Sha256.digest(evidence), correlationId, now));
                throw new BusinessProblem(ProblemCode.REVIEW_RULE_BLOCKED,
                        "冻结 RULE 阻断 APPROVED：" + firstRejectReason(resolution));
            }
            case "REQUIRE_APPROVAL" -> {
                denyAuditor.appendDenied(new AuditEntry(
                        UUID.randomUUID(), tenantId, actorId,
                        "REVIEW_RULE_REQUIRE_APPROVAL", "evidence.review", "ReviewCase",
                        reviewCaseId.toString(),
                        "DENY", List.of(), "review-rule-gate-v1", "REQUIRE_APPROVAL", null,
                        Sha256.digest(evidence), correlationId, now));
                throw new BusinessProblem(ProblemCode.REVIEW_RULE_REQUIRES_APPROVAL,
                        "冻结 RULE 要求 forceApprove：" + firstRejectReason(resolution));
            }
            case "PASS_WITH_WARNINGS" -> audit.append(new AuditEntry(
                    UUID.randomUUID(), tenantId, actorId,
                    "REVIEW_RULE_WARNED", "evidence.review", "ReviewCase", reviewCaseId.toString(),
                    "ALLOW", List.of(), "review-rule-gate-v1", "WARN", null,
                    Sha256.digest(evidence), correlationId, now));
            default -> audit.append(new AuditEntry(
                    UUID.randomUUID(), tenantId, actorId,
                    "REVIEW_RULE_PASSED", "evidence.review", "ReviewCase", reviewCaseId.toString(),
                    "ALLOW", List.of(), "review-rule-gate-v1", resolution.decision(), null,
                    Sha256.digest(evidence), correlationId, now));
        }
    }

    private static String firstRejectReason(RuleResolution resolution) {
        return resolution.hits().stream()
                .map(RuleResolution.RuleHit::rejectReasonCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst()
                .orElse(resolution.decision());
    }
}
