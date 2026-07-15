package com.serviceos.task.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.*;
import com.serviceos.task.api.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkOrderTaskController.class) @Import(SecurityConfiguration.class)
class WorkOrderTaskControllerSecurityTest {
 @Autowired MockMvc mvc;@MockitoBean WorkOrderTaskQueryService queries;@MockitoBean CurrentPrincipalProvider principals;
 @Test void authenticationAndTrustedPrincipalProtectTaskPage() throws Exception {UUID id=UUID.randomUUID();mvc.perform(get("/api/v1/work-orders/{id}/tasks",id)).andExpect(status().isUnauthorized());CurrentPrincipal p=new CurrentPrincipal("reader","tenant",CurrentPrincipal.PrincipalType.USER,"admin",Set.of());when(principals.current()).thenReturn(p);when(queries.list(eq(p),eq("corr-m69-task"),eq(id),isNull(),eq(20))).thenReturn(new WorkOrderTaskPage(List.of(),null,Instant.parse("2026-07-16T00:00:00Z")));mvc.perform(get("/api/v1/work-orders/{id}/tasks",id).with(jwt()).header("X-Correlation-Id","corr-m69-task").queryParam("limit","20")).andExpect(status().isOk()).andExpect(header().string("X-Correlation-Id","corr-m69-task")).andExpect(jsonPath("$.items").isEmpty());verify(queries).list(p,"corr-m69-task",id,null,20);}
}
