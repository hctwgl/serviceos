package com.serviceos.readmodel.application;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.readmodel.api.AdminWorkOrderDirectoryView;
import com.serviceos.readmodel.api.AdminWorkOrderQueryService;
import com.serviceos.readmodel.api.AdminWorkOrderWorkspaceView;
import com.serviceos.readmodel.api.AdminWorkbenchQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.task.api.TaskAllowedActionQueryService;
import com.serviceos.task.api.TaskAllowedAction;
import com.serviceos.task.api.TaskAllowedActions;
import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskSummary;
import com.serviceos.workorder.api.WorkOrderPage;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import com.serviceos.workorder.api.WorkOrderView;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 为 Admin 工单中心与工单工作区组合页面级读模型。
 *
 * <p>组合只调用各模块公开查询端口，不访问其他模块仓储或数据表。任一授权或依赖错误都会向上传播；
 * 缺少必须展示的中文名称时在项目内明确返回数据不完整状态，不使用 UUID 或英文枚举兜底。</p>
 */
@Service
final class DefaultAdminWorkOrderQueryService implements AdminWorkOrderQueryService {
    private final WorkOrderQueryService workOrders;
    private final WorkOrderWorkspaceQueryService workspaces;
    private final ProjectQueryService projects;
    private final TaskAllowedActionQueryService taskAllowedActions;
    private final AuthorizationService authorization;
    private final AdminWorkbenchQueryService workbench;
    private final Clock clock;

    DefaultAdminWorkOrderQueryService(
            WorkOrderQueryService workOrders,
            WorkOrderWorkspaceQueryService workspaces,
            ProjectQueryService projects,
            TaskAllowedActionQueryService taskAllowedActions,
            AuthorizationService authorization,
            AdminWorkbenchQueryService workbench,
            Clock clock
    ) {
        this.workOrders = workOrders;
        this.workspaces = workspaces;
        this.projects = projects;
        this.taskAllowedActions = taskAllowedActions;
        this.authorization = authorization;
        this.workbench = workbench;
        this.clock = clock;
    }

    @Override
    public AdminWorkOrderDirectoryView list(
            CurrentPrincipal principal,
            String correlationId,
            WorkOrderQuery query
    ) {
        WorkOrderPage page = workOrders.list(principal, correlationId, query);
        Map<UUID, ProjectView> projectById = new LinkedHashMap<>();
        for (WorkOrderView item : page.items()) {
            projectById.computeIfAbsent(item.projectId(), projectId ->
                    projects.get(principal, correlationId, projectId).project());
        }
        Map<UUID, WorkOrderDirectorySlaRiskSummary> slaByWorkOrder = new LinkedHashMap<>();
        if (page.slaRiskSummaries() != null) {
            page.slaRiskSummaries().forEach(summary -> slaByWorkOrder.put(summary.workOrderId(), summary));
        }

        List<AdminWorkOrderDirectoryView.Item> items = page.items().stream()
                .map(item -> toDirectoryItem(item, projectById.get(item.projectId()), slaByWorkOrder.get(item.id())))
                .toList();
        List<AdminWorkOrderDirectoryView.ProjectOption> options = projectById.values().stream()
                .map(project -> new AdminWorkOrderDirectoryView.ProjectOption(project.id(), project.name()))
                .toList();
        return new AdminWorkOrderDirectoryView(
                items, options, workbench.get(principal, correlationId),
                page.nextCursor(), page.totalCount(), clock.instant());
    }

    @Override
    public AdminWorkOrderWorkspaceView getWorkspace(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId
    ) {
        WorkOrderWorkspace workspace = workspaces.get(principal, correlationId, workOrderId);
        ProjectView project = projects.get(
                principal, correlationId, workspace.header().projectId()).project();
        TaskAllowedActions actions = workspace.currentTaskSummary() == null
                ? null
                : taskAllowedActions.get(
                        principal, correlationId, workspace.currentTaskSummary().taskId());

        String clientName = AdminProductLabels.client(workspace.header().clientCode());
        String serviceName = AdminProductLabels.service(workspace.header().serviceProductCode());
        String statusName = AdminProductLabels.status(workspace.header().status());
        String stageName = AdminProductLabels.stage(
                workspace.currentTaskSummary() == null ? null : workspace.currentTaskSummary().stageCode());
        String taskName = AdminProductLabels.task(
                workspace.currentTaskSummary() == null ? null : workspace.currentTaskSummary().taskType());
        List<String> missing = missingLabels(
                project.name(), clientName, serviceName, statusName, stageName, taskName);

        List<TaskAllowedAction> pageActions = new java.util.ArrayList<>(
                actions == null ? List.of() : actions.actions());
        if (workspace.currentTaskSummary() != null
                && "ASSIGN_COORDINATORS".equals(workspace.currentTaskSummary().taskType())
                && workspace.header().currentNetworkId() == null
                && canManageAssignment(principal, correlationId, workspace)) {
            pageActions.addFirst(new TaskAllowedAction(
                    "dispatch.assignment.manage", "分配网点", null, List.of()));
        }

        return new AdminWorkOrderWorkspaceView(
                workspace,
                project.name(),
                clientName,
                serviceName,
                stageName,
                taskName,
                statusName,
                List.copyOf(pageActions),
                actions == null ? List.of() : actions.blockedActions().stream()
                        .map(item -> new AdminWorkOrderWorkspaceView.BlockedAction(
                                item.code(), item.label(), String.join("；", item.blockingReasons())))
                        .toList(),
                missing.isEmpty(),
                missing.isEmpty() ? null : "缺少页面展示字段：" + String.join("、", missing),
                clock.instant());
    }

    private boolean canManageAssignment(
            CurrentPrincipal principal,
            String correlationId,
            WorkOrderWorkspace workspace
    ) {
        AuthorizationDecision decision = authorization.authorize(
                principal,
                AuthorizationRequest.projectCapability(
                        "dispatch.assignment.manage",
                        principal.tenantId(),
                        "Task",
                        workspace.currentTaskSummary().taskId().toString(),
                        workspace.header().projectId().toString()),
                correlationId);
        return decision.effect() == AuthorizationDecision.Effect.ALLOW;
    }

    private AdminWorkOrderDirectoryView.Item toDirectoryItem(
            WorkOrderView item,
            ProjectView project,
            WorkOrderDirectorySlaRiskSummary sla
    ) {
        String clientName = AdminProductLabels.client(item.clientCode());
        String serviceName = AdminProductLabels.service(item.serviceProductCode());
        String statusName = AdminProductLabels.status(item.status());
        String stageName = AdminProductLabels.stage(item.currentStageCode());
        List<String> missing = missingLabels(project == null ? null : project.name(), clientName,
                serviceName, statusName, stageName);
        String slaLevel = sla == null ? "NORMAL" : sla.breachedCount() > 0 ? "BREACHED" : "RISK";
        String slaLabel = switch (slaLevel) {
            case "BREACHED" -> "已超时";
            case "RISK" -> "存在风险";
            default -> "正常";
        };
        return new AdminWorkOrderDirectoryView.Item(
                item.id(), item.externalOrderCode(), item.maskedCustomerName(), item.maskedCustomerPhone(),
                item.projectId(), project == null ? null : project.name(), clientName, serviceName,
                stageName, item.currentNetworkDisplayName(), item.currentTechnicianDisplayName(),
                slaLevel, slaLabel, statusName, item.updatedAt(), missing.isEmpty(),
                missing.isEmpty() ? null : "缺少页面展示字段：" + String.join("、", missing));
    }

    private static List<String> missingLabels(String... values) {
        String[] names = {"项目名称", "客户名称", "服务名称", "工单状态", "当前阶段", "当前任务"};
        java.util.ArrayList<String> missing = new java.util.ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            if (values[index] == null || values[index].isBlank()) {
                missing.add(names[index]);
            }
        }
        return List.copyOf(missing);
    }
}
