package com.serviceos.dispatch.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.Optional;
import java.util.UUID;

/** ServiceAssignment 授权只读边界。 */
public interface ServiceAssignmentQueryService {

    /**
     * 查询 Task 当前 ACTIVE 网点/师傅责任；要求 dispatch.read 与实时 Project Scope。
     */
    Optional<ServiceAssignmentSummary> findActiveForTask(
            CurrentPrincipal principal, String correlationId, UUID taskId);
}
