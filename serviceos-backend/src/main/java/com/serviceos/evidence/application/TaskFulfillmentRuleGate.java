package com.serviceos.evidence.application;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * M330：EvidenceSetSnapshot 创建与 Task complete 的冻结 RULE 门禁。
 *
 * <p>复用 Task {@code ruleRef}；BLOCK / REQUIRE_APPROVAL 失败关闭（此处无 forceApprove 旁路）。</p>
 */
@Component
final class TaskFulfillmentRuleGate {
    private final FrozenTaskRuleEvaluator rules;

    TaskFulfillmentRuleGate(FrozenTaskRuleEvaluator rules) {
        this.rules = rules;
    }

    void assertSnapshotCreateAllowed(
            String tenantId,
            String actorId,
            String correlationId,
            UUID taskId
    ) {
        rules.assertProceedAllowed(
                tenantId, actorId, correlationId, taskId,
                "evidence.submit", "Task", taskId.toString());
    }

    void assertTaskCompleteAllowed(
            String tenantId,
            String actorId,
            String correlationId,
            UUID taskId
    ) {
        rules.assertProceedAllowed(
                tenantId, actorId, correlationId, taskId,
                "task.complete", "Task", taskId.toString());
    }
}
