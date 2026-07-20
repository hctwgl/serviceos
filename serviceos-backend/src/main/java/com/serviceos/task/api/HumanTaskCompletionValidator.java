package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

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

    /**
     * 解释当前为何尚不能完成任务（用于 allowed-actions 阻塞原因投影）。
     * 不得依赖客户端提交的 resultRef；只读取服务端已有事实。
     */
    default List<String> explainBlockingReasons(String tenantId, UUID taskId) {
        return List.of();
    }
}
