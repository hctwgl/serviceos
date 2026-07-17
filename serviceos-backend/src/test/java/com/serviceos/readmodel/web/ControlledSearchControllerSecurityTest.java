package com.serviceos.readmodel.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.readmodel.api.ControlledSearchHit;
import com.serviceos.readmodel.api.ControlledSearchMeta;
import com.serviceos.readmodel.api.ControlledSearchQueryService;
import com.serviceos.readmodel.api.ControlledSearchResult;
import com.serviceos.readmodel.api.ControlledSearchType;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** M192：受控搜索 HTTP 只信任 JWT 派生主体；未认证 401；缺能力 403。 */
@WebMvcTest(ControlledSearchController.class)
@Import(SecurityConfiguration.class)
class ControlledSearchControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean ControlledSearchQueryService searches;
    @MockitoBean CurrentPrincipalProvider principals;

    @Test
    void unauthenticatedSearchIsRejected() throws Exception {
        mvc.perform(get("/api/v1/search").param("q", "ab"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedSearchReturnsHits() throws Exception {
        CurrentPrincipal actor = actor();
        Instant now = Instant.parse("2026-07-17T12:00:00Z");
        when(principals.current()).thenReturn(actor);
        when(searches.search(eq(actor), eq("corr-search"), eq("ORDER-1"), isNull()))
                .thenReturn(new ControlledSearchResult(
                        List.of(new ControlledSearchHit(
                                "11111111-1111-4111-8111-111111111111",
                                ControlledSearchType.WORK_ORDER,
                                "11111111-1111-4111-8111-111111111111",
                                "****R-1",
                                "EXTERNAL_ORDER_CODE",
                                "/work-orders/11111111-1111-4111-8111-111111111111")),
                        new ControlledSearchMeta(
                                "digest",
                                List.of(ControlledSearchType.WORK_ORDER),
                                List.of(ControlledSearchType.WORK_ORDER),
                                List.of()),
                        now));

        mvc.perform(get("/api/v1/search")
                        .param("q", "ORDER-1")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("WORK_ORDER"))
                .andExpect(jsonPath("$.meta.qDigest").value("digest"));
    }

    @Test
    void missingCapabilityIsForbidden() throws Exception {
        CurrentPrincipal actor = actor();
        when(principals.current()).thenReturn(actor);
        when(searches.search(eq(actor), eq("corr-deny"), eq("ab"), isNull()))
                .thenThrow(new BusinessProblem(ProblemCode.ACCESS_DENIED, "缺少 search.read"));

        mvc.perform(get("/api/v1/search")
                        .param("q", "ab")
                        .with(jwt().jwt(token -> token.subject("external-subject").claim("tenant_id", "tenant-a")))
                        .header("X-Correlation-Id", "corr-deny"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(
                "019f81a1-0000-7f8c-9505-36fe5c0e8801",
                "tenant-a",
                CurrentPrincipal.PrincipalType.USER,
                "admin-web",
                Set.of());
    }
}
