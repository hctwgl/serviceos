package com.serviceos.configuration.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.configuration.api.PricingShadowSnapshotQueryService;
import com.serviceos.configuration.api.PricingShadowSnapshotView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 影子定价试算只读：未认证 401；缺能力 403；成功 200。 */
@WebMvcTest(WorkOrderPricingSnapshotController.class)
@Import(SecurityConfiguration.class)
class WorkOrderPricingSnapshotControllerSecurityTest {
    private static final UUID WORK_ORDER = UUID.fromString("019f83d1-7777-7f8c-9505-36fe5c0e880c");

    @Autowired MockMvc mvc;
    @MockitoBean PricingShadowSnapshotQueryService queries;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedIsRejected() throws Exception {
        mvc.perform(get("/api/v1/work-orders/{workOrderId}/pricing-snapshots", WORK_ORDER))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCapabilityReturnsAccessDenied() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(queries.listByWorkOrder(eq(actor), any(), eq(WORK_ORDER)))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "The action is not allowed"));

        mvc.perform(get("/api/v1/work-orders/{workOrderId}/pricing-snapshots", WORK_ORDER)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    @Test
    void authenticatedReturnsOk() throws Exception {
        CurrentPrincipal actor = actor();
        UUID snapshotId = UUID.fromString("019f83d1-eeee-7f8c-9505-36fe5c0e8812");
        when(principals.current()).thenReturn(actor);
        when(queries.listByWorkOrder(eq(actor), any(), eq(WORK_ORDER)))
                .thenReturn(List.of(new PricingShadowSnapshotView(
                        snapshotId, WORK_ORDER, UUID.randomUUID(), UUID.randomUUID(),
                        "workorder.fulfilled", "platform.demo.pricing", "CNY", 28800L,
                        "SHADOW", "corr-shadow", Instant.parse("2026-07-17T03:00:00Z"))));

        mvc.perform(get("/api/v1/work-orders/{workOrderId}/pricing-snapshots", WORK_ORDER)
                        .with(jwt().jwt(token -> token.subject("external-subject")
                                .claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].snapshotId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.items[0].mode").value("SHADOW"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f83d1-1111-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "admin",
                Set.of());
    }
}
