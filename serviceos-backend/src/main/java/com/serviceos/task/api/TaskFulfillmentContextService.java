package com.serviceos.task.api;

import java.util.Optional;
import java.util.UUID;

/** 为预约、到场等下游履约能力提供 Task 责任与业务归属的只读公开边界。 */
public interface TaskFulfillmentContextService {
    Optional<TaskFulfillmentContext> find(String tenantId, UUID taskId);
}
