package com.serviceos.task.api;

import java.util.UUID;

/** 跨模块只读的人工接管任务上下文；候选/责任均按请求主体实时计算。 */
public record HandlingTaskContextView(
        UUID taskId,
        String taskType,
        String businessKey,
        String status,
        String claimedBy,
        long version,
        boolean actorCandidate,
        boolean actorResponsible
) {
}
