package com.serviceos.evidence.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * M325/M329：ReviewCase APPROVED 前的冻结 RULE 门禁。
 *
 * <p>INTERNAL {@code decide} 与 CLIENT 外部回执共用：解析仍使用资产 stage={@code INTERNAL}
 * （与 Task 冻结 {@code ruleRef} 同一资产；Case origin 只体现在审计调用方）。
 * 无 ruleRef：放行。forceApprove / REJECTED 不进入阻断。
 * BLOCK / REQUIRE_APPROVAL 仅拦截 APPROVED。</p>
 */
@Component
final class ReviewRuleGate {
    private final FrozenTaskRuleEvaluator rules;

    ReviewRuleGate(FrozenTaskRuleEvaluator rules) {
        this.rules = rules;
    }

    void assertDecideAllowed(
            String tenantId,
            String actorId,
            String correlationId,
            UUID reviewCaseId,
            UUID taskId,
            String proposedDecision
    ) {
        if ("REJECTED".equals(proposedDecision)) {
            rules.auditRejectAllowed(
                    tenantId, actorId, correlationId, taskId,
                    "evidence.review", "ReviewCase", reviewCaseId.toString());
            return;
        }
        rules.assertProceedAllowed(
                tenantId, actorId, correlationId, taskId,
                "evidence.review", "ReviewCase", reviewCaseId.toString());
    }
}
