package com.serviceos.integration.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.integration.api.OutboundDeliveryQueryService;
import com.serviceos.integration.api.OutboundDeliveryQueueItem;
import com.serviceos.integration.api.OutboundDeliveryQueuePage;
import com.serviceos.integration.api.OutboundDeliveryQueueQuery;
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

@WebMvcTest(OutboundDeliveryQueryController.class)
@Import(SecurityConfiguration.class)
class OutboundDeliveryQueryControllerSecurityTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private OutboundDeliveryQueryService deliveries;

    @MockitoBean
    private CurrentPrincipalProvider principals;

    @Test
    void authenticationAndSafeQueueContractAreEnforced() throws Exception {
        mvc.perform(get("/api/v1/outbound-deliveries"))
                .andExpect(status().isUnauthorized());

        UUID projectId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        CurrentPrincipal principal = new CurrentPrincipal(
                "reader", "tenant", CurrentPrincipal.PrincipalType.USER, "m99", Set.of());
        Instant now = Instant.parse("2026-07-16T19:00:00Z");
        OutboundDeliveryQueuePage page = new OutboundDeliveryQueuePage(
                List.of(new OutboundDeliveryQueueItem(
                        deliveryId, projectId, "byd-cpim-v7.3.1",
                        "byd-ocean-shandong-submit-review-v1", "SUBMIT_CLIENT_REVIEW",
                        "biz-1", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), "ORD-1", UUID.randomUUID(), "UNKNOWN",
                        null, null, 1L, 1, now, null, null)),
                null,
                now.plusSeconds(1));
        OutboundDeliveryQueueQuery query =
                new OutboundDeliveryQueueQuery(projectId, null, null, null, null, null, 50);
        when(principals.current()).thenReturn(principal);
        when(deliveries.list(principal, "corr-m99", query)).thenReturn(page);

        mvc.perform(get("/api/v1/outbound-deliveries")
                        .with(jwt())
                        .header("X-Correlation-Id", "corr-m99")
                        .queryParam("projectId", projectId.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "corr-m99"))
                .andExpect(jsonPath("$.items[0].deliveryId").value(deliveryId.toString()))
                .andExpect(jsonPath("$.items[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[0].sourceSnapshotDigest").doesNotExist())
                .andExpect(jsonPath("$.items[0].payloadDigest").doesNotExist())
                .andExpect(jsonPath("$.items[0].operatorPrincipalId").doesNotExist())
                .andExpect(jsonPath("$.items[0].payloadObjectRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].approvalRef").doesNotExist())
                .andExpect(jsonPath("$.items[0].reason").doesNotExist());
        verify(deliveries).list(principal, "corr-m99", query);
    }
}
