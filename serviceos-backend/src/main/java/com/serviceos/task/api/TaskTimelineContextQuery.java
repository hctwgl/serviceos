package com.serviceos.task.api;

import java.util.Optional;
import java.util.UUID;

/** 仅供其他模块按 tenant + taskId 解析稳定资源关系，不提供用户授权。 */
public interface TaskTimelineContextQuery {
    Optional<TaskTimelineContext> find(String tenantId, UUID taskId);
}
