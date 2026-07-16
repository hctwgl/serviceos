package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.WorkOrderTimelineItem;
import com.serviceos.readmodel.api.WorkOrderTimelinePage;
import com.serviceos.readmodel.api.WorkOrderTimelineQueryService;
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

@WebMvcTest(WorkOrderTimelineController.class)
@Import(SecurityConfiguration.class)
class WorkOrderTimelineControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WorkOrderTimelineQueryService timelines;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationAndSafeTimelineContractAreEnforced() throws Exception {
        UUID workOrderId = UUID.randomUUID();
        mvc.perform(get("/api/v1/work-orders/{id}/timeline", workOrderId))
                .andExpect(status().isUnauthorized());

        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m73", Set.of());
        Instant occurredAt = Instant.parse("2026-07-16T01:00:00Z");
        WorkOrderTimelineItem item = new WorkOrderTimelineItem(
                UUID.randomUUID(), "APPOINTMENT", "appointment.rescheduled", 1, occurredAt,
                occurredAt.plusSeconds(2), null, "Appointment", UUID.randomUUID(), 3,
                "SURVEY", "RESCHEDULED", "corr-event", "APPOINTMENT_RESCHEDULED", 1);
        WorkOrderTimelinePage page = new WorkOrderTimelinePage(
                7, List.of(item), "next", occurredAt.plusSeconds(3),
                occurredAt.plusSeconds(2), "UNKNOWN");
        when(principals.current()).thenReturn(principal);
        when(timelines.list(principal, "corr-m73", workOrderId, "cursor", 20))
                .thenReturn(page);

        mvc.perform(get("/api/v1/work-orders/{id}/timeline", workOrderId)
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m73")
                        .queryParam("cursor", "cursor")
                        .queryParam("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"7\""))
                .andExpect(header().string("X-Correlation-Id", "corr-m73"))
                .andExpect(jsonPath("$.items[0].eventType").value("appointment.rescheduled"))
                .andExpect(jsonPath("$.items[0].outcomeCode").value("RESCHEDULED"))
                .andExpect(jsonPath("$.items[0].category").value("APPOINTMENT"))
                .andExpect(jsonPath("$.freshnessStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].payload").doesNotExist())
                .andExpect(jsonPath("$.items[0].contactedPartyRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].noShowPartyRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].resultRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].errorMessage").doesNotExist());
        verify(timelines).list(principal, "corr-m73", workOrderId, "cursor", 20);
    }
}
