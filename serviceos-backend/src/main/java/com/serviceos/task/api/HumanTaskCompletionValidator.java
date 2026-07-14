package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;

/**
 * 人工任务完成前的可插拔领域结果校验器。
 *
 * <p>接口定义在 task API，由结果所属模块实现，避免 task 反向依赖表单、资料或审核模块。</p>
 */
public interface HumanTaskCompletionValidator {
    void validate(CurrentPrincipal principal, CompleteHumanTaskCommand command);
}
