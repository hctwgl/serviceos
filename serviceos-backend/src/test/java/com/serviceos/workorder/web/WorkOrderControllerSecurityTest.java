package com.serviceos.workorder.web;

import com.serviceos.bootstrap.SecurityConfiguration;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.CurrentPrincipalProvider;
import com.serviceos.workorder.api.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkOrderController.class)
@Import(SecurityConfiguration.class)
class WorkOrderControllerSecurityTest {
 @Autowired MockMvc mvc; @MockitoBean WorkOrderQueryService queries; @MockitoBean CurrentPrincipalProvider principals;
 @Test void unauthenticatedIsRejected() throws Exception {mvc.perform(get("/api/v1/work-orders")).andExpect(status().isUnauthorized());}
 @Test void trustedPrincipalDrivesListAndDetailWithEtag() throws Exception {
  UUID id=UUID.randomUUID(),project=UUID.randomUUID(),bundle=UUID.randomUUID();
  CurrentPrincipal principal=new CurrentPrincipal("reader","tenant-trusted",CurrentPrincipal.PrincipalType.USER,"admin",Set.of());
  WorkOrderView view=new WorkOrderView(id,"tenant-trusted",project,"BYD","OCEAN","INSTALL","EXT-1","RECEIVED",bundle,"B","1.0.0","a".repeat(64),"370000","370100","370102",Instant.parse("2026-07-15T02:00:00Z"),Instant.parse("2026-07-15T03:00:00Z"),null,null,3,"王*","*******0000","杭州市***","SURVEY");
  when(principals.current()).thenReturn(principal); when(queries.list(eq(principal),eq("corr-list"),any())).thenReturn(new WorkOrderPage(List.of(view),null,Instant.now()));
  when(queries.get(principal,"corr-get",id)).thenReturn(new WorkOrderDetail(view,Instant.now()));
  mvc.perform(get("/api/v1/work-orders").with(jwt()).header("X-Correlation-Id","corr-list").queryParam("clientCode","BYD").queryParam("status","RECEIVED").queryParam("limit","20"))
    .andExpect(status().isOk()).andExpect(header().string("X-Correlation-Id","corr-list")).andExpect(jsonPath("$.items[0].id").value(id.toString())).andExpect(jsonPath("$.items[0].maskedCustomerPhone").value("*******0000")).andExpect(jsonPath("$.items[0].currentStageCode").value("SURVEY")).andExpect(jsonPath("$.items[0].customerMobile").doesNotExist());
  mvc.perform(get("/api/v1/work-orders/{id}",id).with(jwt()).header("X-Correlation-Id","corr-get"))
    .andExpect(status().isOk()).andExpect(header().string("ETag","\"3\"")).andExpect(jsonPath("$.workOrder.id").value(id.toString())).andExpect(jsonPath("$.workOrder.maskedCustomerName").value("王*")).andExpect(jsonPath("$.workOrder.currentStageCode").value("SURVEY"));
  verify(queries).list(eq(principal),eq("corr-list"),argThat(q->"BYD".equals(q.clientCode())&&"RECEIVED".equals(q.status())&&q.limit()==20));
 }
}
