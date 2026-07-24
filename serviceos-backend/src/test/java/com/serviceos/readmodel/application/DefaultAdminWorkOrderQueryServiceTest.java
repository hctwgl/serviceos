package com.serviceos.readmodel.application;

import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.ProjectDetail;
import com.serviceos.project.api.ProjectQueryService;
import com.serviceos.project.api.ProjectView;
import com.serviceos.readmodel.api.AdminWorkOrderDirectoryView;
import com.serviceos.readmodel.api.AdminWorkOrderWorkspaceView;
import com.serviceos.readmodel.api.AdminWorkbenchQueryService;
import com.serviceos.readmodel.api.AdminWorkbenchView;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.task.api.TaskAllowedAction;
import com.serviceos.task.api.TaskAllowedActionQueryService;
import com.serviceos.task.api.TaskAllowedActions;
import com.serviceos.task.api.TaskBlockedAction;
import com.serviceos.workorder.api.WorkOrderDirectorySlaRiskSummary;
import com.serviceos.workorder.api.WorkOrderPage;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import com.serviceos.workorder.api.WorkOrderView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAdminWorkOrderQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-22T08:00:00Z");

    @Test
    @DisplayName("工单中心应返回项目与业务中文名称且不让前端自行关联目录")
    void shouldBuildDirectoryReadModel() {
        Fixture fixture = fixture();
        WorkOrderQuery query = new WorkOrderQuery(null, null, null, null, 20);
        when(fixture.workOrders.list(fixture.principal, "corr-list", query)).thenReturn(
                new WorkOrderPage(
                        List.of(fixture.workOrder), null, NOW,
                        List.of(new WorkOrderDirectorySlaRiskSummary(fixture.workOrderId, 1, 0)),
                        List.of(), 1, false));
        when(fixture.workbench.get(fixture.principal, "corr-list")).thenReturn(
                new AdminWorkbenchView(23, 5, 3, 4, 2, 2, 1, 4, NOW));

        AdminWorkOrderDirectoryView result = fixture.service.list(
                fixture.principal, "corr-list", query);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.projectName()).isEqualTo("比亚迪山东家充项目");
            assertThat(item.clientName()).isEqualTo("比亚迪");
            assertThat(item.serviceName()).isEqualTo("充电桩安装服务");
            assertThat(item.stageName()).isEqualTo("上门安装");
            assertThat(item.slaLabel()).isEqualTo("存在风险");
            assertThat(item.dataComplete()).isTrue();
        });
    }

    @Test
    @DisplayName("工单工作区应返回服务端允许动作和不可执行原因")
    void shouldBuildWorkspaceReadModel() {
        Fixture fixture = fixture();
        UUID taskId = UUID.randomUUID();
        WorkOrderWorkspace workspace = new WorkOrderWorkspace(
                fixture.workOrder,
                new WorkOrderWorkspace.WorkOrderWorkspaceTaskSummary(
                        taskId, "INSTALLATION", "HUMAN", "READY", "PILOT_INSTALLATION", null, 2),
                List.of(new WorkOrderWorkspace.WorkOrderWorkspaceStageSummary(
                        "PILOT_INSTALLATION", 2, "ACTIVE", NOW.minusSeconds(60), null)),
                Map.of("TASKS", "AVAILABLE"),
                "/api/v1/tasks/" + taskId + "/allowed-actions",
                null, null, null, List.of(), "FRESH",
                new WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions(3),
                new WorkOrderWorkspace.WorkOrderWorkspaceMeta(NOW, "checkpoint", "FRESH", "query"),
                "王*", "*******5678", "山东省济南市历城区***");
        when(fixture.workspaces.get(fixture.principal, "corr-workspace", fixture.workOrderId))
                .thenReturn(workspace);
        when(fixture.taskActions.get(fixture.principal, "corr-workspace", taskId)).thenReturn(
                new TaskAllowedActions(
                        2,
                        List.of(new TaskAllowedAction("SUBMIT_REVIEW", "提交审核", null, List.of())),
                        List.of(new TaskBlockedAction(
                                "COMPLETE", "完成任务", List.of("还有 1 项必填资料未上传"))),
                        NOW));

        AdminWorkOrderWorkspaceView result = fixture.service.getWorkspace(
                fixture.principal, "corr-workspace", fixture.workOrderId);

        assertThat(result.projectName()).isEqualTo("比亚迪山东家充项目");
        assertThat(result.taskName()).isEqualTo("上门安装");
        assertThat(result.allowedActions()).extracting(TaskAllowedAction::label)
                .containsExactly("提交审核");
        assertThat(result.blockedActions()).singleElement()
                .extracting(AdminWorkOrderWorkspaceView.BlockedAction::reason)
                .isEqualTo("还有 1 项必填资料未上传");
        assertThat(result.dataComplete()).isTrue();
    }

    @Test
    @DisplayName("无任务的工作流门闸应使用工单权威阶段且不误报数据不完整")
    void shouldBuildWorkspaceForWorkflowGateWithoutTask() {
        Fixture fixture = fixture();
        WorkOrderWorkspace workspace = new WorkOrderWorkspace(
                fixture.workOrder,
                null,
                List.of(new WorkOrderWorkspace.WorkOrderWorkspaceStageSummary(
                        "PILOT_INSTALLATION", 2, "ACTIVE", NOW.minusSeconds(60), null)),
                Map.of("TASKS", "AVAILABLE"),
                null,
                null, null, null, List.of(), "FRESH",
                new WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions(3),
                new WorkOrderWorkspace.WorkOrderWorkspaceMeta(
                        NOW, "checkpoint", "FRESH", "query"),
                "王*", "*******5678", "山东省济南市历城区***");
        when(fixture.workspaces.get(fixture.principal, "corr-gate", fixture.workOrderId))
                .thenReturn(workspace);

        AdminWorkOrderWorkspaceView result = fixture.service.getWorkspace(
                fixture.principal, "corr-gate", fixture.workOrderId);

        assertThat(result.stageName()).isEqualTo("上门安装");
        assertThat(result.taskName()).isEqualTo("上门安装");
        assertThat(result.allowedActions()).isEmpty();
        assertThat(result.dataComplete()).isTrue();
    }

    @Test
    @DisplayName("已完成工单无活动节点时应展示明确终态而不是尚未进入流程")
    void shouldBuildCompletedWorkspaceWithoutActiveTask() {
        Fixture fixture = fixture();
        WorkOrderView active = fixture.workOrder;
        WorkOrderView completed = new WorkOrderView(
                active.id(), active.tenantId(), active.projectId(), active.clientCode(),
                active.brandCode(), active.serviceProductCode(), active.externalOrderCode(),
                "FULFILLED", active.configurationBundleId(), active.configurationBundleCode(),
                active.configurationBundleVersion(), active.configurationBundleDigest(),
                active.provinceCode(), active.cityCode(), active.districtCode(),
                active.externalDispatchedAt(), active.receivedAt(), NOW, active.activatedAt(), NOW,
                active.version() + 1, active.maskedCustomerName(), active.maskedCustomerPhone(),
                active.maskedServiceAddress(), null, null, null, null, null,
                null, null, null, null);
        WorkOrderWorkspace workspace = new WorkOrderWorkspace(
                completed,
                null,
                List.of(new WorkOrderWorkspace.WorkOrderWorkspaceStageSummary(
                        "CLIENT_CALLBACK", 4, "COMPLETED", NOW.minusSeconds(60), NOW)),
                Map.of("TASKS", "AVAILABLE"),
                null,
                null, null, null, List.of(), "FRESH",
                new WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions(4),
                new WorkOrderWorkspace.WorkOrderWorkspaceMeta(
                        NOW, "checkpoint", "FRESH", "query"),
                "王*", "*******5678", "山东省济南市历城区***");
        when(fixture.workspaces.get(fixture.principal, "corr-completed", fixture.workOrderId))
                .thenReturn(workspace);

        AdminWorkOrderWorkspaceView result = fixture.service.getWorkspace(
                fixture.principal, "corr-completed", fixture.workOrderId);

        assertThat(result.statusName()).isEqualTo("已完成");
        assertThat(result.stageName()).isEqualTo("履约已完成");
        assertThat(result.taskName()).isEqualTo("全部任务已完成");
        assertThat(result.dataComplete()).isTrue();
    }

    private Fixture fixture() {
        WorkOrderQueryService workOrders = mock(WorkOrderQueryService.class);
        WorkOrderWorkspaceQueryService workspaces = mock(WorkOrderWorkspaceQueryService.class);
        ProjectQueryService projects = mock(ProjectQueryService.class);
        TaskAllowedActionQueryService taskActions = mock(TaskAllowedActionQueryService.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        AdminWorkbenchQueryService workbench = mock(AdminWorkbenchQueryService.class);
        CurrentPrincipal principal = new CurrentPrincipal(
                "admin", "tenant", CurrentPrincipal.PrincipalType.USER, "admin-web", Set.of());
        UUID projectId = UUID.randomUUID();
        UUID workOrderId = UUID.randomUUID();
        ProjectView project = new ProjectView(
                projectId, "tenant", "PRJ-BYD-SD", "BYD", "比亚迪山东家充项目",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                List.of("370000"), List.of(), "ACTIVE", 1, NOW, 1, 0);
        when(projects.get(eq(principal), any(), eq(projectId)))
                .thenReturn(new ProjectDetail(project, NOW));
        WorkOrderView workOrder = new WorkOrderView(
                workOrderId, "tenant", projectId, "BYD", "BYD",
                "HOME_CHARGING_SURVEY_INSTALL", "BYD202607220001", "ACTIVE",
                UUID.randomUUID(), "BYD-SD", "1", "a".repeat(64),
                "370000", "370100", "370112", NOW.minusSeconds(3600), NOW.minusSeconds(3500),
                NOW.minusSeconds(60), NOW.minusSeconds(3400), null, 3,
                "王*", "*******5678", "山东省济南市历城区***",
                "PILOT_INSTALLATION", "INSTALLATION", "READY", null, "张伟",
                UUID.randomUUID().toString(), "济南智联服务中心",
                UUID.randomUUID().toString(), "李师傅");
        return new Fixture(
                workOrders, workspaces, projects, taskActions, principal, projectId, workOrderId,
                workbench, workOrder, new DefaultAdminWorkOrderQueryService(
                        workOrders, workspaces, projects, taskActions, authorization, workbench,
                        Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private record Fixture(
            WorkOrderQueryService workOrders,
            WorkOrderWorkspaceQueryService workspaces,
            ProjectQueryService projects,
            TaskAllowedActionQueryService taskActions,
            CurrentPrincipal principal,
            UUID projectId,
            UUID workOrderId,
            AdminWorkbenchQueryService workbench,
            WorkOrderView workOrder,
            DefaultAdminWorkOrderQueryService service
    ) {
    }
}
