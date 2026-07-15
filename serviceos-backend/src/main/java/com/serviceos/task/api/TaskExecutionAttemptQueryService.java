package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** Task 执行 Attempt 历史查询；实现必须先完成 Task 资源的实时授权。 */
public interface TaskExecutionAttemptQueryService {
    TaskExecutionAttemptPage list(
            CurrentPrincipal principal,
            String correlationId,
            UUID taskId,
            String cursor,
            int limit);
}
