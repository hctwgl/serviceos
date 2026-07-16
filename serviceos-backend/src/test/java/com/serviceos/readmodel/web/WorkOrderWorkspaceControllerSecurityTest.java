package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.WorkOrderWorkspace;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceMeta;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceSourceVersions;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceTaskSummary;
import com.serviceos.readmodel.api.WorkOrderWorkspaceQueryService;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection;
import com.serviceos.readmodel.api.WorkOrderWorkspaceSection.WorkOrderWorkspaceTasksSectionData;
import com.serviceos.workorder.api.WorkOrderView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderWorkspaceController.class)
@Import(SecurityConfiguration.class)
class WorkOrderWorkspaceControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkOrderWorkspaceQueryService workspaces;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationAndSafeWorkspaceContractAreEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        mvc.perform(get("/api/v1/work-orders/{id}/workspace", workOrderId))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m85", Set.of());
        Instant now = Instant.parse("2026-07-16T10:00:00Z");
        UUID taskId = UUID.randomUUID();
        WorkOrderView header = new WorkOrderView(
                workOrderId, "tenant", UUID.randomUUID(), "BYD", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "EXT-1", "ACTIVE",
                UUID.randomUUID(), "BUNDLE", "1.0.0", "a".repeat(64),
                "370000", "370100", "370102", now.minusSeconds(100), now.minusSeconds(90),
                now.minusSeconds(80), null, 3);
        WorkOrderWorkspace workspace = new WorkOrderWorkspace(
                header,
                new WorkOrderWorkspaceTaskSummary(
                        taskId, "SITE_SURVEY", "HUMAN", "READY", "SURVEY", null, 1),
                Map.of("TASKS", "AVAILABLE", "SLA", "UNAVAILABLE", "EXCEPTIONS", "UNAVAILABLE"),
                "/api/v1/tasks/" + taskId + "/allowed-actions",
                null,
                null,
                "UNKNOWN",
                new WorkOrderWorkspaceSourceVersions(3),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "UNKNOWN", "q-1"));
        when(principals.current()).thenReturn(principal);
        when(workspaces.get(principal, "corr-m85", workOrderId)).thenReturn(workspace);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace", workOrderId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m85"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(header().string("X-Correlation-Id", "corr-m85"))
                .andExpect(jsonPath("$.header.externalOrderCode").value("EXT-1"))
                .andExpect(jsonPath("$.currentTaskSummary.taskType").value("SITE_SURVEY"))
                .andExpect(jsonPath("$.allowedActionLink").value("/api/v1/tasks/" + taskId + "/allowed-actions"))
                .andExpect(jsonPath("$.header.customerName").doesNotExist())
                .andExpect(jsonPath("$.header.customerMobile").doesNotExist())
                .andExpect(jsonPath("$.header.serviceAddress").doesNotExist())
                .andExpect(jsonPath("$.header.vehicleVin").doesNotExist());
        verify(workspaces).get(principal, "corr-m85", workOrderId);
    }

    @Test
    void workspaceSectionContractIsEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m87", Set.of());
        Instant now = Instant.parse("2026-07-16T11:00:00Z");
        UUID taskId = UUID.randomUUID();
        WorkOrderWorkspaceSection section = new WorkOrderWorkspaceSection(
                "TASKS",
                new WorkOrderWorkspaceSourceVersions(4),
                new WorkOrderWorkspaceMeta(now, "work-order-core-timeline.v1:gen:1", "FRESH", "q-2"),
                new WorkOrderWorkspaceTasksSectionData(
                        List.of(new WorkOrderWorkspaceTaskSummary(
                                taskId, "SITE_SURVEY", "HUMAN", "READY", "SURVEY", null, 1)),
                        null),
                null);
        when(principals.current()).thenReturn(principal);
        when(workspaces.getSection(eq(principal), eq("corr-sec"), eq(workOrderId),
                eq("TASKS"), isNull(), eq(50))).thenReturn(section);

        mvc.perform(get("/api/v1/work-orders/{id}/workspace/sections/{section}", workOrderId, "TASKS")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-sec"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.section").value("TASKS"))
                .andExpect(jsonPath("$.tasks.items[0].taskType").value("SITE_SURVEY"))
                .andExpect(jsonPath("$.timeline").value(nullValue()));
        verify(workspaces).getSection(principal, "corr-sec", workOrderId, "TASKS", null, 50);
    }
}
