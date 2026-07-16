package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.WorkOrderActivitySummary;
import com.serviceos.readmodel.api.WorkOrderActivitySummaryQueryService;
import com.serviceos.readmodel.api.WorkOrderTimelineItem;
import com.serviceos.readmodel.api.WorkOrderWorkspace.WorkOrderWorkspaceMeta;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkOrderActivitySummaryController.class)
@Import(SecurityConfiguration.class)
class WorkOrderActivitySummaryControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkOrderActivitySummaryQueryService summaries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationSafeContractAndCursorRejectionAreEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        mvc.perform(get("/api/v1/work-orders/{id}/activity-summary", workOrderId))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m93", Set.of());
        Instant occurredAt = Instant.parse("2026-07-16T16:00:00Z");
        WorkOrderTimelineItem item = new WorkOrderTimelineItem(
                UUID.randomUUID(), "TASK", "task.released", 1, occurredAt,
                occurredAt.plusSeconds(1), "technician-m93", "Task", UUID.randomUUID(), 3,
                "SITE_SURVEY", "SHIFT_ENDED", "corr-event",
                "TASK_RELEASED", 1);
        WorkOrderActivitySummary summary = new WorkOrderActivitySummary(
                9,
                List.of(item),
                occurredAt.plusSeconds(1),
                new WorkOrderWorkspaceMeta(
                        occurredAt.plusSeconds(2), "work-order-core-timeline.v1:gen:1",
                        "FRESH", "q-m93"));
        when(principals.current()).thenReturn(principal);
        when(summaries.get(principal, "corr-m93", workOrderId, 5)).thenReturn(summary);

        mvc.perform(get("/api/v1/work-orders/{id}/activity-summary", workOrderId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m93"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"9\""))
                .andExpect(header().string("X-Correlation-Id", "corr-m93"))
                .andExpect(jsonPath("$.items[0].eventType").value("task.released"))
                .andExpect(jsonPath("$.items[0].outcomeCode").value("SHIFT_ENDED"))
                .andExpect(jsonPath("$.meta.freshnessStatus").value("FRESH"))
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.items[0].note").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
        verify(summaries).get(principal, "corr-m93", workOrderId, 5);

        mvc.perform(get("/api/v1/work-orders/{id}/activity-summary", workOrderId)
                        .with(jwt())
                        .queryParam("cursor", "cursor-not-accepted"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }
}
