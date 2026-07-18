package com.serviceos.task.api;

import java.util.Optional;
import java.util.UUID;

/** 查询资料整改应回派的最后责任人；供同事务编排使用。 */
public interface TaskResponsibilityQuery {
    Optional<String> findCorrectionCandidateUser(String tenantId, UUID taskId);
}
