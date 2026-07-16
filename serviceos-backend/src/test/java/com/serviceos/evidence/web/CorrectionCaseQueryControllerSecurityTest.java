package com.serviceos.evidence.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.evidence.api.CorrectionCaseQueryService;
import com.serviceos.evidence.api.CorrectionCaseQueueItem;
import com.serviceos.evidence.api.CorrectionCaseQueuePage;
import com.serviceos.evidence.api.CorrectionCaseQueueQuery;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
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

@WebMvcTest(CorrectionCaseQueryController.class)
@Import(SecurityConfiguration.class)
class CorrectionCaseQueryControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CorrectionCaseQueryService corrections;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationAndSafeQueueContractAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/correction-cases"))
                .andExpect(status().isUnauthorized());

        UUID projectId = UUID.randomUUID();
        UUID correctionCaseId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m98", Set.of());
        Instant now = Instant.parse("2026-07-16T18:00:00Z");
        CorrectionCaseQueuePage page = new CorrectionCaseQueuePage(
                List.of(new CorrectionCaseQueueItem(
                        correctionCaseId, projectId, UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), List.of("IMAGE.BLUR"), UUID.randomUUID(), "IN_PROGRESS",
                        now, null, null, null, 0)),
                null,
                now.plusSeconds(1));
        CorrectionCaseQueueQuery query =
                new CorrectionCaseQueueQuery(projectId, "IN_PROGRESS", null, null, null, 50);
        when(principals.current()).thenReturn(principal);
        when(corrections.list(principal, "corr-m98", query)).thenReturn(page);

        mvc.perform(get("/api/v1/correction-cases")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m98")
                        .queryParam("projectId", projectId.toString())
                        .queryParam("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-m98"))
                .andExpect(jsonPath("$.items[0].correctionCaseId").value(correctionCaseId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.items[0].sourceSnapshotContentDigest").doesNotExist())
                .andExpect(jsonPath("$.items[0].createdBy").doesNotExist())
                .andExpect(jsonPath("$.items[0].closedBy").doesNotExist())
                .andExpect(jsonPath("$.items[0].waivedBy").doesNotExist())
                .andExpect(jsonPath("$.items[0].waiveApprovalRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].waiveNote").doesNotExist());
        verify(corrections).list(principal, "corr-m98", query);
    }
}
