package com.serviceos.task.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 以实时授权、责任、状态和 guard 事实投影当前主体的 Task 动作。 */
public interface TaskAllowedActionQueryService {
    TaskAllowedActions get(CurrentPrincipal principal, String correlationId, UUID taskId);
}
