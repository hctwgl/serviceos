package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * 人工任务完成前的可插拔领域结果校验器。
 *
 * <p>接口定义在 task API，由结果所属模块实现，避免 task 反向依赖表单、资料或审核模块。</p>
 */
public interface HumanTaskCompletionValidator {
    void validate(CurrentPrincipal principal, CompleteHumanTaskCommand command);

    /**
     * 带关联 ID 的完成校验；默认回退到无关联版本，便于既有校验器逐步迁移。
     */
    default void validate(
            CurrentPrincipal principal, String correlationId, CompleteHumanTaskCommand command
    ) {
        validate(principal, command);
    }
}
