package com.serviceos.evidence.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.task.api.CompleteHumanTaskCommand;
import com.serviceos.task.api.HumanTaskCompletionValidator;
import org.springframework.stereotype.Component;

/**
 * M330：HUMAN Task 完成前复用冻结 {@code ruleRef} 失败关闭。
 */
@Component
final class RuleTaskCompletionValidator implements HumanTaskCompletionValidator {
    private final TaskFulfillmentRuleGate ruleGate;

    RuleTaskCompletionValidator(TaskFulfillmentRuleGate ruleGate) {
        this.ruleGate = ruleGate;
    }

    @Override
    public void validate(CurrentPrincipal principal, CompleteHumanTaskCommand command) {
        validate(principal, "task-complete-rule-gate", command);
    }

    @Override
    public void validate(
            CurrentPrincipal principal, String correlationId, CompleteHumanTaskCommand command
    ) {
        ruleGate.assertTaskCompleteAllowed(
                principal.tenantId(),
                principal.principalId(),
                correlationId,
                command.taskId());
    }
}
