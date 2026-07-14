package com.serviceos.task.api;

import java.util.Optional;
import java.util.UUID;

/** 查询 Task 当前责任人；供同事务编排使用。 */
public interface TaskResponsibilityQuery {
    Optional<String> findActiveResponsibleUser(String tenantId, UUID taskId);
}
