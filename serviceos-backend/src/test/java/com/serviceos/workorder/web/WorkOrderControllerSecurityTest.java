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
import java.time.LocalDate;
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
  String claimant=UUID.randomUUID().toString();
  WorkOrderView view=new WorkOrderView(id,"tenant-trusted",project,"BYD","OCEAN","INSTALL","EXT-1","RECEIVED",bundle,"B","1.0.0","a".repeat(64),"370000","370100","370102",Instant.parse("2026-07-15T02:00:00Z"),Instant.parse("2026-07-15T03:00:00Z"),Instant.parse("2026-07-15T03:30:00Z"),null,null,3,"王*","*******0000","杭州市***","SURVEY","RUNNING",claimant,"演示师傅","net-1","青岛网点","tech-1","现场师傅");
  when(principals.current()).thenReturn(principal); when(queries.list(eq(principal),eq("corr-list"),any())).thenReturn(new WorkOrderPage(List.of(view),null,Instant.now(),null,1,false));
  when(queries.get(principal,"corr-get",id)).thenReturn(new WorkOrderDetail(view,Instant.now()));
  mvc.perform(get("/api/v1/work-orders").with(jwt()).header("X-Correlation-Id","corr-list").queryParam("clientCode","BYD").queryParam("status","RECEIVED").queryParam("districtCode","370102").queryParam("currentStageCode","SURVEY").queryParam("currentTaskStatus","RUNNING").queryParam("currentNetworkId","bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb").queryParam("currentTechnicianId","cccccccc-cccc-4ccc-8ccc-cccccccccccc").queryParam("slaRisk","NEAR").queryParam("receivedFrom","2026-03-01").queryParam("receivedTo","2026-03-31").queryParam("limit","20"))
    .andExpect(status().isOk()).andExpect(header().string("X-Correlation-Id","corr-list")).andExpect(jsonPath("$.items[0].id").value(id.toString())).andExpect(jsonPath("$.items[0].maskedCustomerPhone").value("*******0000")).andExpect(jsonPath("$.items[0].updatedAt").value("2026-07-15T03:30:00Z")).andExpect(jsonPath("$.items[0].currentStageCode").value("SURVEY")).andExpect(jsonPath("$.items[0].currentTaskStatus").value("RUNNING")).andExpect(jsonPath("$.items[0].currentClaimedBy").value(claimant)).andExpect(jsonPath("$.items[0].currentAssigneeDisplayName").value("演示师傅")).andExpect(jsonPath("$.items[0].currentNetworkDisplayName").value("青岛网点")).andExpect(jsonPath("$.items[0].currentTechnicianDisplayName").value("现场师傅")).andExpect(jsonPath("$.totalCount").value(1)).andExpect(jsonPath("$.totalCountTruncated").value(false)).andExpect(jsonPath("$.items[0].customerMobile").doesNotExist());
  mvc.perform(get("/api/v1/work-orders/{id}",id).with(jwt()).header("X-Correlation-Id","corr-get"))
    .andExpect(status().isOk()).andExpect(header().string("ETag","\"3\"")).andExpect(jsonPath("$.workOrder.id").value(id.toString())).andExpect(jsonPath("$.workOrder.maskedCustomerName").value("王*")).andExpect(jsonPath("$.workOrder.currentStageCode").value("SURVEY")).andExpect(jsonPath("$.workOrder.currentAssigneeDisplayName").value("演示师傅")).andExpect(jsonPath("$.workOrder.currentNetworkDisplayName").value("青岛网点"));
  verify(queries).list(eq(principal),eq("corr-list"),argThat(q->"BYD".equals(q.clientCode())&&"RECEIVED".equals(q.status())&&"370102".equals(q.districtCode())&&"SURVEY".equals(q.currentStageCode())&&"RUNNING".equals(q.currentTaskStatus())&&UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb").equals(q.currentNetworkId())&&UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc").equals(q.currentTechnicianId())&&"NEAR".equals(q.slaRisk())&&LocalDate.of(2026,3,1).equals(q.receivedFrom())&&LocalDate.of(2026,3,31).equals(q.receivedTo())&&q.limit()==20));
 }
}
