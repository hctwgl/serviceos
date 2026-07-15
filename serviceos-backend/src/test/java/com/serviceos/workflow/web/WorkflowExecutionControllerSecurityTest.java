package com.serviceos.workflow.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.*;
import com.serviceos.workflow.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowExecutionController.class) @Import(SecurityConfiguration.class)
class WorkflowExecutionControllerSecurityTest {
 @Autowired MockMvc mvc;@MockitoBean WorkflowExecutionQueryService queries;@MockitoBean CurrentPrincipalProvider principals;
 @Test void authenticationAndTrustedPrincipalProtectProjection() throws Exception {UUID id=UUID.randomUUID();mvc.perform(get("/api/v1/work-orders/{id}/stages",id)).andExpect(status().isUnauthorized());CurrentPrincipal p=new CurrentPrincipal("reader","tenant",CurrentPrincipal.PrincipalType.USER,"admin",Set.of());when(principals.current()).thenReturn(p);when(queries.get(p,"corr-m69-stage",id)).thenReturn(new WorkflowExecutionProjection(null,List.of(),Instant.parse("2026-07-16T00:00:00Z")));mvc.perform(get("/api/v1/work-orders/{id}/stages",id).with(jwt()).header("X-Correlation-Id","corr-m69-stage")).andExpect(status().isOk()).andExpect(header().string("X-Correlation-Id","corr-m69-stage")).andExpect(jsonPath("$.workflow").doesNotExist()).andExpect(jsonPath("$.stages").isEmpty());verify(queries).get(p,"corr-m69-stage",id);}
}
