package com.serviceos.task.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 为拥有独立人工接管 Task 的业务模块提供候选发现和实时责任校验。 */
public interface HandlingTaskContextQuery {
    List<HandlingTaskContextView> listForActor(String tenantId, String actorId, String taskType);

    Optional<HandlingTaskContextView> findForActor(
            String tenantId, UUID taskId, String actorId, String taskType);
}
