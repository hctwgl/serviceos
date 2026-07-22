package com.serviceos.readmodel.api;

import com.serviceos.task.api.TaskAllowedAction;

import java.time.Instant;
import java.util.List;

/** Admin 工单工作区首屏一次装载所需的页面读模型。 */
public record AdminWorkOrderWorkspaceView(
        WorkOrderWorkspace workspace,
        String projectName,
        String clientName,
        String serviceName,
        String stageName,
        String taskName,
        String statusName,
        List<TaskAllowedAction> allowedActions,
        List<BlockedAction> blockedActions,
        boolean dataComplete,
        String dataProblem,
        Instant generatedAt
) {
    public AdminWorkOrderWorkspaceView {
        allowedActions = List.copyOf(allowedActions);
        blockedActions = List.copyOf(blockedActions);
    }

    public record BlockedAction(String code, String label, String reason) {
    }
}
