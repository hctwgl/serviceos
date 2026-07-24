package com.serviceos.workorder;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.*;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** M68 实时范围、稳定游标、tenant 隔离和拒绝审计的真实 PostgreSQL 证据。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes=ServiceOsApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class WorkOrderQueryPostgresIT {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"))
         .withDatabaseName("serviceos").withUsername("serviceos_test").withPassword("serviceos_test");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
 @Autowired WorkOrderCommandService commands; @Autowired WorkOrderQueryService queries;
 @Autowired ConfigurationService configurations; @Autowired JdbcClient jdbc; @Autowired Flyway flyway;

 @BeforeEach void clean(){jdbc.sql("""
  TRUNCATE TABLE aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
  sla_instance, dsp_service_assignment, tsk_task, wo_work_order,
  net_technician_profile, net_service_network, net_partner_organization,
  cfg_configuration_bundle_item,cfg_configuration_bundle,cfg_configuration_asset_version,
  prj_project,auth_role_field_policy,auth_role_grant,auth_role_capability,auth_role,
  idn_person_profile, idn_security_principal CASCADE
  """).update();}

 @Test void projectScopeFiltersPagesAndDetailWhileCursorBindsFilters(){
  Scope a=scope("tenant-test","A"); Scope b=scope("tenant-test","B");
  UUID wa=receive(a,"ORDER-A","a".repeat(64)); UUID wb=receive(b,"ORDER-B","b".repeat(64));
  seedRole("reader","PROJECT",a.projectId().toString()); CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-list",new WorkOrderQuery(null,null,"RECEIVED",null,1));
  assertThat(page.items()).extracting(WorkOrderView::id).containsExactly(wa);
  assertThat(page.items().getFirst().externalOrderCode()).isEqualTo("ORDER-A");
  // M429：目录返回脱敏客户联系；receive 夹具原文为 敏感姓名 / 13800000000 / 敏感地址
  assertThat(page.items().getFirst().maskedCustomerName()).isEqualTo("敏***");
  assertThat(page.items().getFirst().maskedCustomerPhone()).isEqualTo("*******0000");
  assertThat(page.items().getFirst().maskedServiceAddress()).isEqualTo("敏***");
  assertThat(page.items().getFirst().maskedCustomerPhone()).doesNotContain("138");
  assertThat(page.items().getFirst().maskedServiceAddress()).doesNotContain("敏感地址");
  // M432/M433：无 ACTIVE 任务时阶段/责任人为 null，不发明值。
  assertThat(page.items().getFirst().currentStageCode()).isNull();
  assertThat(page.items().getFirst().currentClaimedBy()).isNull();
  assertThat(page.items().getFirst().currentAssigneeDisplayName()).isNull();
  assertThat(page.items().getFirst().currentNetworkId()).isNull();
  assertThat(page.items().getFirst().currentTechnicianId()).isNull();
  // M435：新建时 updatedAt 与 receivedAt 同源，且二者均非 null。
  assertThat(page.items().getFirst().updatedAt()).isEqualTo(page.items().getFirst().receivedAt());
  var detail=queries.get(reader,"corr-get",wa).workOrder();
  assertThat(detail.configurationBundleId()).isEqualTo(a.bundle().bundleId());
  assertThat(detail.maskedCustomerName()).isEqualTo("敏***");
  assertThat(detail.maskedCustomerPhone()).isEqualTo("*******0000");
  assertThat(detail.currentStageCode()).isNull();
  assertThat(detail.currentClaimedBy()).isNull();
  assertThat(detail.currentAssigneeDisplayName()).isNull();
  assertThat(detail.currentNetworkDisplayName()).isNull();
  assertThat(detail.currentTechnicianDisplayName()).isNull();
  assertThat(detail.updatedAt()).isEqualTo(detail.receivedAt());
  // M434：无 sla.read 时页级旁载省略（null），不伪造 []/0。
  assertThat(page.slaRiskSummaries()).isNull();
  // M450：无 operations.exception.read 时页级旁载省略（null），不伪造 []/0。
  assertThat(page.exceptionSummaries()).isNull();
  // M436：同筛选总数；本页仅 A 项目 1 条（status=RECEIVED）。
  assertThat(page.totalCount()).isEqualTo(1);
  assertThat(page.totalCountTruncated()).isFalse();
  assertThatThrownBy(()->queries.get(reader,"corr-deny",wb)).isInstanceOfSatisfying(BusinessProblem.class,
    p->assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
  assertThat(jdbc.sql("SELECT decision_code FROM aud_audit_record ORDER BY occurred_at DESC LIMIT 1").query(String.class).single()).isEqualTo("DENY");
  seedRole("tenant-reader","TENANT","tenant-test");
  var first=queries.list(principal("tenant-reader","tenant-test"),"corr-page",new WorkOrderQuery(null,null,null,null,1));
  assertThat(first.nextCursor()).isNotBlank();
  assertThatThrownBy(()->queries.list(principal("tenant-reader","tenant-test"),"corr-cursor",
    new WorkOrderQuery("BYD",null,"ACTIVE",first.nextCursor(),1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cursor");
 }

 @Test void listAndDetailExposeCurrentStageCodeFromActiveTask(){
  Scope a=scope("tenant-test","A");
  UUID wa=receive(a,"ORDER-STAGE","d".repeat(64));
  seedActiveTask(a,wa,"SURVEY",null,null);
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-stage-list",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.items()).extracting(WorkOrderView::id).containsExactly(wa);
  assertThat(page.items().getFirst().currentStageCode()).isEqualTo("SURVEY");
  assertThat(page.items().getFirst().currentTaskType()).isEqualTo("SITE_SURVEY");
  assertThat(page.items().getFirst().currentClaimedBy()).isNull();
  assertThat(queries.get(reader,"corr-stage-get",wa).workOrder().currentStageCode()).isEqualTo("SURVEY");
  assertThat(queries.get(reader,"corr-stage-get",wa).workOrder().currentTaskType()).isEqualTo("SITE_SURVEY");
 }

 @Test void listAndDetailExposeWaitEventStageWithoutActiveTask(){
  Scope a=scope("tenant-test","A");
  UUID waiting=receive(a,"ORDER-WAIT-OEM","1".repeat(64));
  UUID surveyOnly=receive(a,"ORDER-TASK-STAGE","2".repeat(64));
  seedActiveWorkflowStage(a,waiting,"CLIENT_CALLBACK",4);
  seedActiveTask(a,surveyOnly,"SURVEY",null,null);
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");

  var page=queries.list(reader,"corr-wait-list",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.items()).filteredOn(item->item.id().equals(waiting)).singleElement().satisfies(item->{
   assertThat(item.currentStageCode()).isEqualTo("CLIENT_CALLBACK");
   assertThat(item.currentTaskType()).isNull();
   assertThat(item.currentTaskStatus()).isNull();
  });
  assertThat(queries.get(reader,"corr-wait-get",waiting).workOrder().currentStageCode())
    .isEqualTo("CLIENT_CALLBACK");

  var filtered=queries.list(reader,"corr-wait-filter",
    new WorkOrderQuery(null,null,null,null,null,null,null,"CLIENT_CALLBACK",
      null,null,null,null,null,null,null,null,null,20));
  assertThat(filtered.items()).extracting(WorkOrderView::id).containsExactly(waiting);
  assertThat(filtered.totalCount()).isEqualTo(1);
 }

 @Test void listAndDetailExposeCurrentAssigneeFromClaimedTask(){
  Scope a=scope("tenant-test","A");
  UUID wa=receive(a,"ORDER-ASSIGNEE","e".repeat(64));
  UUID claimant=UUID.randomUUID();
  seedPerson(a.tenant(),claimant,"演示师傅");
  seedActiveTask(a,wa,"SURVEY","CLAIMED",claimant.toString());
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-assignee-list",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.items().getFirst().currentClaimedBy()).isEqualTo(claimant.toString());
  assertThat(page.items().getFirst().currentAssigneeDisplayName()).isEqualTo("演示师傅");
  var detail=queries.get(reader,"corr-assignee-get",wa).workOrder();
  assertThat(detail.currentClaimedBy()).isEqualTo(claimant.toString());
  assertThat(detail.currentAssigneeDisplayName()).isEqualTo("演示师傅");
 }

 @Test void listFiltersByRegionCodes(){
  Scope a=scope("tenant-test","A");
  UUID hangzhou=receive(a,"ORDER-HZ","3".repeat(64));
  seedWorkOrderRow(a,"ORDER-SZ","4".repeat(64),"440000","440300","440305");
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byDistrict=queries.list(reader,"corr-region-district",
    new WorkOrderQuery(null,null,null,null,null,null,"370102",null,null,null,null,null,null,null,null,null,null,20));
  assertThat(byDistrict.items()).extracting(WorkOrderView::id).containsExactly(hangzhou);
  assertThat(byDistrict.totalCount()).isEqualTo(1);
  var byProvince=queries.list(reader,"corr-region-province",
    new WorkOrderQuery(null,null,null,null,"440000",null,null,null,null,null,null,null,null,null,null,null,null,20));
  assertThat(byProvince.items()).extracting(WorkOrderView::externalOrderCode).containsExactly("ORDER-SZ");
  var none=queries.list(reader,"corr-region-none",
    new WorkOrderQuery(null,null,null,null,"110000",null,null,null,null,null,null,null,null,null,null,null,null,20));
  assertThat(none.items()).isEmpty();
  assertThat(none.totalCount()).isZero();
 }

 @Test void listAndDetailExposeCurrentNetworkAndTechnicianFromActiveAssignment(){
  Scope a=scope("tenant-test","A");
  UUID wa=receive(a,"ORDER-NET-TECH","7".repeat(64));
  UUID taskId=seedActiveTask(a,wa,"SURVEY",null,null);
  UUID networkId=UUID.randomUUID();
  UUID techProfileId=UUID.randomUUID();
  UUID techPrincipal=UUID.randomUUID();
  seedNetworkAndTechnician(a.tenant(),networkId,"青岛服务网点",techProfileId,techPrincipal,"青岛师傅甲");
  seedActiveAssignment(a,wa,taskId,"NETWORK",networkId.toString());
  seedActiveAssignment(a,wa,taskId,"TECHNICIAN",techProfileId.toString());
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-net-tech-list",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.items().getFirst().currentNetworkId()).isEqualTo(networkId.toString());
  assertThat(page.items().getFirst().currentNetworkDisplayName()).isEqualTo("青岛服务网点");
  assertThat(page.items().getFirst().currentTechnicianId()).isEqualTo(techProfileId.toString());
  assertThat(page.items().getFirst().currentTechnicianDisplayName()).isEqualTo("青岛师傅甲");
  var detail=queries.get(reader,"corr-net-tech-get",wa).workOrder();
  assertThat(detail.currentNetworkDisplayName()).isEqualTo("青岛服务网点");
  assertThat(detail.currentTechnicianDisplayName()).isEqualTo("青岛师傅甲");
 }

 @Test void listFiltersByCurrentStageCode(){
  Scope a=scope("tenant-test","A");
  UUID survey=receive(a,"ORDER-SURVEY-FILTER","5".repeat(64));
  UUID install=receive(a,"ORDER-INSTALL-FILTER","6".repeat(64));
  seedActiveTask(a,survey,"SURVEY",null,null);
  seedActiveTask(a,install,"INSTALLATION",null,null);
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var bySurvey=queries.list(reader,"corr-stage-filter",
    new WorkOrderQuery(null,null,null,null,null,null,null,"SURVEY",null,null,null,null,null,null,null,null,null,20));
  assertThat(bySurvey.items()).extracting(WorkOrderView::id).containsExactly(survey);
  assertThat(bySurvey.items().getFirst().currentStageCode()).isEqualTo("SURVEY");
  assertThat(bySurvey.totalCount()).isEqualTo(1);
  var byInstall=queries.list(reader,"corr-stage-install",
    new WorkOrderQuery(null,null,null,null,null,null,null,"INSTALLATION",null,null,null,null,null,null,null,null,null,20));
  assertThat(byInstall.items()).extracting(WorkOrderView::id).containsExactly(install);
  var none=queries.list(reader,"corr-stage-none",
    new WorkOrderQuery(null,null,null,null,null,null,null,"REPAIR",null,null,null,null,null,null,null,null,null,20));
  assertThat(none.items()).isEmpty();
  assertThat(none.totalCount()).isZero();
  assertThatThrownBy(()->queries.list(reader,"corr-stage-invalid",
    new WorkOrderQuery(null,null,null,null,null,null,null,"survey",null,null,null,null,null,null,null,null,null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currentStageCode");
 }

 @Test void listFiltersByCurrentTaskStatus(){
  Scope a=scope("tenant-test","A");
  UUID ready=receive(a,"ORDER-TASK-READY","7".repeat(64));
  UUID running=receive(a,"ORDER-TASK-RUNNING","8".repeat(64));
  UUID runner=UUID.randomUUID();
  seedPerson(a.tenant(),runner,"执行师傅");
  seedActiveTask(a,ready,"SURVEY","READY",null);
  // RUNNING 须满足 ck_tsk_human_lifecycle：claimed_by/claimed_at/started_at 均非空。
  seedActiveTask(a,running,"SURVEY","RUNNING",runner.toString());
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byReady=queries.list(reader,"corr-task-status-ready",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,"READY",null,null,null,null,null,null,null,null,20));
  assertThat(byReady.items()).extracting(WorkOrderView::id).containsExactly(ready);
  assertThat(byReady.items().getFirst().currentTaskStatus()).isEqualTo("READY");
  assertThat(byReady.totalCount()).isEqualTo(1);
  var byRunning=queries.list(reader,"corr-task-status-running",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,"RUNNING",null,null,null,null,null,null,null,null,20));
  assertThat(byRunning.items()).extracting(WorkOrderView::id).containsExactly(running);
  assertThat(byRunning.items().getFirst().currentTaskStatus()).isEqualTo("RUNNING");
  assertThatThrownBy(()->queries.list(reader,"corr-task-status-invalid",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,"SUCCEEDED",null,null,null,null,null,null,null,null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("currentTaskStatus");
 }

 @Test void listFiltersByReviewCorrectionStatus(){
  Scope a=scope("tenant-test","A");
  UUID reviewOpenWo=receive(a,"ORDER-REV-OPEN","1".repeat(64));
  UUID correctionWo=receive(a,"ORDER-CORR-ACTIVE","2".repeat(64));
  UUID noneWo=receive(a,"ORDER-REV-NONE","3".repeat(64));
  UUID reviewTask=seedActiveTask(a,reviewOpenWo,"SURVEY",null,null);
  UUID correctionTask=seedActiveTask(a,correctionWo,"SURVEY",null,null);
  seedActiveTask(a,noneWo,"SURVEY",null,null);
  seedOpenReviewCase(a,reviewTask);
  seedOpenCorrectionCase(a,correctionTask);
  seedRole("reader","PROJECT",a.projectId().toString(),"workOrder.read","evidence.read");
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byReview=queries.list(reader,"corr-rev-open",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,"REVIEW_OPEN",null,null,20));
  assertThat(byReview.items()).extracting(WorkOrderView::id).containsExactly(reviewOpenWo);
  assertThat(byReview.totalCount()).isEqualTo(1);
  var byCorrection=queries.list(reader,"corr-corr-active",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,"CORRECTION_ACTIVE",null,null,20));
  assertThat(byCorrection.items()).extracting(WorkOrderView::id).containsExactly(correctionWo);
  assertThatThrownBy(()->queries.list(reader,"corr-rev-invalid",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,"OPEN",null,null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reviewCorrectionStatus");
  // 缺 evidence.read → 空集失败关闭，不泄露审核/整改事实
  seedRole("wo-only","PROJECT",a.projectId().toString(),"workOrder.read");
  CurrentPrincipal woOnly=principal("wo-only","tenant-test");
  var denied=queries.list(woOnly,"corr-rev-denied",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,"REVIEW_OPEN",null,null,20));
  assertThat(denied.items()).isEmpty();
  assertThat(denied.totalCount()).isZero();
 }

 @Test void listFiltersByKeywordQ(){
  Scope a=scope("tenant-test","A");
  UUID byCode=receive(a,"ORDER-KW-ALPHA","a".repeat(64));
  UUID byPhone=receive(a,"ORDER-KW-OTHER","b".repeat(64));
  // 调整手机后四位为可检索值
  jdbc.sql("UPDATE wo_work_order SET customer_mobile='13900001234' WHERE id=:id")
    .param("id",byPhone).update();
  jdbc.sql("UPDATE wo_work_order SET customer_name='张三丰', service_address='杭州市西湖区文一路1号' WHERE id=:id")
    .param("id",byCode).update();
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byExternal=queries.list(reader,"corr-kw-code",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"KW-ALPHA",null,20));
  assertThat(byExternal.items()).extracting(WorkOrderView::id).containsExactly(byCode);
  assertThat(byExternal.totalCount()).isEqualTo(1);
  var byLast4=queries.list(reader,"corr-kw-phone",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"1234",null,20));
  assertThat(byLast4.items()).extracting(WorkOrderView::id).containsExactly(byPhone);
  var byName=queries.list(reader,"corr-kw-name",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"张三",null,20));
  assertThat(byName.items()).extracting(WorkOrderView::id).containsExactly(byCode);
  var byAddress=queries.list(reader,"corr-kw-addr",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"文一路",null,20));
  assertThat(byAddress.items()).extracting(WorkOrderView::id).containsExactly(byCode);
  assertThatThrownBy(()->queries.list(reader,"corr-kw-full-phone",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"13900001234",null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("full phone");
  assertThatThrownBy(()->queries.list(reader,"corr-kw-short",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,null,null,null,"a",null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("too short");
 }

 @Test void listFiltersByCurrentNetworkId(){
  Scope a=scope("tenant-test","A");
  UUID matched=receive(a,"ORDER-NET-MATCH","a".repeat(64));
  UUID other=receive(a,"ORDER-NET-OTHER","b".repeat(64));
  UUID matchedTask=seedActiveTask(a,matched,"SURVEY",null,null);
  UUID otherTask=seedActiveTask(a,other,"SURVEY",null,null);
  UUID networkA=UUID.randomUUID();
  UUID networkB=UUID.randomUUID();
  UUID techProfile=UUID.randomUUID();
  UUID techPrincipal=UUID.randomUUID();
  seedNetworkAndTechnician(a.tenant(),networkA,"筛选网点甲",techProfile,techPrincipal,"师傅甲");
  seedNetworkAndTechnician(a.tenant(),networkB,"筛选网点乙",UUID.randomUUID(),UUID.randomUUID(),"师傅乙");
  seedActiveAssignment(a,matched,matchedTask,"NETWORK",networkA.toString());
  seedActiveAssignment(a,other,otherTask,"NETWORK",networkB.toString());
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byNetwork=queries.list(reader,"corr-network-filter",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,networkA,null,null,null,null,null,null,null,20));
  assertThat(byNetwork.items()).extracting(WorkOrderView::id).containsExactly(matched);
  assertThat(byNetwork.items().getFirst().currentNetworkId()).isEqualTo(networkA.toString());
  assertThat(byNetwork.totalCount()).isEqualTo(1);
  var none=queries.list(reader,"corr-network-none",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,UUID.randomUUID(),null,null,null,null,null,null,null,20));
  assertThat(none.items()).isEmpty();
 assertThat(none.totalCount()).isZero();
 }

 @Test void listFiltersUnassignedNetworkByResponsibilityFactInsteadOfStage(){
  Scope a=scope("tenant-test","A");
  UUID assigned=receive(a,"ORDER-NET-ASSIGNED","e".repeat(64));
  UUID unassigned=receive(a,"ORDER-NET-UNASSIGNED","f".repeat(64));
  UUID fulfilled=receive(a,"ORDER-NET-FULFILLED","0".repeat(64));
  UUID assignedTask=seedActiveTask(a,assigned,"SURVEY",null,null);
  seedActiveTask(a,unassigned,"INSTALLATION",null,null);
  jdbc.sql("""
    UPDATE wo_work_order
       SET status='FULFILLED', activated_at=now(), fulfilled_at=now(), version=version+1
     WHERE id=:id
    """).param("id",fulfilled).update();
  UUID network=UUID.randomUUID();
  seedNetworkAndTechnician(a.tenant(),network,"责任事实网点",UUID.randomUUID(),UUID.randomUUID(),"责任事实师傅");
  seedActiveAssignment(a,assigned,assignedTask,"NETWORK",network.toString());
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");

  var page=queries.list(reader,"corr-network-unassigned",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,
      "NETWORK_UNASSIGNED",null,null,null,null,null,null,20));
  assertThat(page.items()).extracting(WorkOrderView::id).containsExactly(unassigned);
  assertThat(page.totalCount()).isEqualTo(1);
  assertThatThrownBy(()->queries.list(reader,"corr-network-unassigned-invalid",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,
      "UNASSIGNED",null,null,null,null,null,null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("responsibilityStatus");
 }

 @Test void listFiltersByCurrentTechnicianId(){
  Scope a=scope("tenant-test","A");
  UUID matched=receive(a,"ORDER-TECH-MATCH","c".repeat(64));
  UUID other=receive(a,"ORDER-TECH-OTHER","d".repeat(64));
  UUID matchedTask=seedActiveTask(a,matched,"SURVEY",null,null);
  UUID otherTask=seedActiveTask(a,other,"SURVEY",null,null);
  UUID network=UUID.randomUUID();
  UUID techA=UUID.randomUUID();
  UUID techB=UUID.randomUUID();
  seedNetworkAndTechnician(a.tenant(),network,"师傅筛选网点",techA,UUID.randomUUID(),"筛选师傅甲");
  seedNetworkAndTechnician(a.tenant(),UUID.randomUUID(),"师傅筛选网点乙",techB,UUID.randomUUID(),"筛选师傅乙");
  seedActiveAssignment(a,matched,matchedTask,"TECHNICIAN",techA.toString());
  seedActiveAssignment(a,other,otherTask,"TECHNICIAN",techB.toString());
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byTech=queries.list(reader,"corr-tech-filter",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,techA,null,null,null,null,null,null,20));
  assertThat(byTech.items()).extracting(WorkOrderView::id).containsExactly(matched);
  assertThat(byTech.items().getFirst().currentTechnicianId()).isEqualTo(techA.toString());
  assertThat(byTech.totalCount()).isEqualTo(1);
  var none=queries.list(reader,"corr-tech-none",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,UUID.randomUUID(),null,null,null,null,null,null,20));
  assertThat(none.items()).isEmpty();
  assertThat(none.totalCount()).isZero();
 }

 @Test void listExposesExactTotalCountAcrossPagesBeyondFormerCap(){
  Scope a=scope("tenant-test","A");
  receive(a,"ORDER-TOTAL-1","1".repeat(64));
  receive(a,"ORDER-TOTAL-2","2".repeat(64));
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var first=queries.list(reader,"corr-total-page",new WorkOrderQuery(null,null,null,null,1));
  assertThat(first.items()).hasSize(1);
  assertThat(first.nextCursor()).isNotBlank();
  assertThat(first.totalCount()).isEqualTo(2);
  assertThat(first.totalCountTruncated()).isFalse();
  for(int i=0;i<99;i++){
   seedWorkOrderRow(a,"ORDER-CAP-"+i,"c".repeat(64));
  }
  // M444：超过原 100 封顶后仍返回精确全量；truncatedated 恒 false。
  var exact=queries.list(reader,"corr-total-exact",new WorkOrderQuery(null,null,null,null,20));
  assertThat(exact.totalCount()).isEqualTo(101);
  assertThat(exact.totalCountTruncated()).isFalse();
 }

 @Test void listAndDetailExposeIndependentUpdatedAtAfterActivate(){
  Scope a=scope("tenant-test","A");
  UUID wa=receive(a,"ORDER-UPDATED","9".repeat(64));
  Instant receivedAt=jdbc.sql("SELECT received_at FROM wo_work_order WHERE id=:id")
    .param("id",wa).query(Instant.class).single();
  Instant beforeActivate=jdbc.sql("SELECT updated_at FROM wo_work_order WHERE id=:id")
    .param("id",wa).query(Instant.class).single();
  assertThat(beforeActivate).isEqualTo(receivedAt);
  commands.activate(new ActivateWorkOrderCommand(
    a.tenant(),wa,UUID.randomUUID(),"corr-activate"));
  Instant afterActivate=jdbc.sql("SELECT updated_at FROM wo_work_order WHERE id=:id")
    .param("id",wa).query(Instant.class).single();
  assertThat(afterActivate).isAfter(receivedAt);
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-updated-list",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.items().getFirst().updatedAt()).isEqualTo(afterActivate);
  assertThat(page.items().getFirst().receivedAt()).isEqualTo(receivedAt);
  assertThat(page.items().getFirst().updatedAt()).isNotEqualTo(page.items().getFirst().receivedAt());
  var detail=queries.get(reader,"corr-updated-get",wa).workOrder();
  assertThat(detail.updatedAt()).isEqualTo(afterActivate);
  assertThat(detail.receivedAt()).isEqualTo(receivedAt);
 }

 @Test void listFiltersByReceivedAt(){
  Scope a=scope("tenant-test","A");
  UUID early=receive(a,"ORDER-RECV-EARLY","1".repeat(64));
  UUID late=receive(a,"ORDER-RECV-LATE","2".repeat(64));
  ZoneId shanghai=ZoneId.of("Asia/Shanghai");
  Instant day1=LocalDate.of(2026,3,1).atStartOfDay(shanghai).toInstant().plusSeconds(3600);
  Instant day3=LocalDate.of(2026,3,3).atStartOfDay(shanghai).toInstant().plusSeconds(3600);
  jdbc.sql("UPDATE wo_work_order SET received_at=:t WHERE id=:id")
    .param("t",java.sql.Timestamp.from(day1)).param("id",early).update();
  jdbc.sql("UPDATE wo_work_order SET received_at=:t WHERE id=:id")
    .param("t",java.sql.Timestamp.from(day3)).param("id",late).update();
  seedRole("reader","PROJECT",a.projectId().toString());
  CurrentPrincipal reader=principal("reader","tenant-test");
  var range=queries.list(reader,"corr-recv-range",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,LocalDate.of(2026,3,1),LocalDate.of(2026,3,3),null,null,null,20));
  assertThat(range.items()).extracting(WorkOrderView::id).containsExactlyInAnyOrder(early,late);
  assertThat(range.totalCount()).isEqualTo(2);
  var singleDay=queries.list(reader,"corr-recv-day",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,LocalDate.of(2026,3,1),LocalDate.of(2026,3,1),null,null,null,20));
  assertThat(singleDay.items()).extracting(WorkOrderView::id).containsExactly(early);
  var none=queries.list(reader,"corr-recv-none",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,LocalDate.of(2026,3,10),LocalDate.of(2026,3,11),null,null,null,20));
  assertThat(none.items()).isEmpty();
  assertThatThrownBy(()->queries.list(reader,"corr-recv-invalid",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,null,LocalDate.of(2026,3,5),LocalDate.of(2026,3,1),null,null,null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("receivedTo");
 }

 @Test void listFiltersBySlaRisk(){
  Scope a=scopeWithSla("tenant-test","A");
  UUID openWo=receive(a,"ORDER-SLA-OPEN","e".repeat(64));
  UUID breachedWo=receive(a,"ORDER-SLA-BREACH","f".repeat(64));
  UUID noneWo=receive(a,"ORDER-SLA-NONE","0".repeat(64));
  UUID openTask=seedActiveTask(a,openWo,"SURVEY",null,null);
  UUID breachTask=seedActiveTask(a,breachedWo,"SURVEY",null,null);
  seedActiveTask(a,noneWo,"SURVEY",null,null);
  jdbc.sql("UPDATE tsk_task SET sla_ref='survey.response.sla' WHERE task_id IN (:a,:b)")
    .param("a",openTask).param("b",breachTask).update();
  seedRunningSla(a,openWo,openTask);
  seedBreachedSla(a,breachedWo,breachTask);
  seedRole("reader","PROJECT",a.projectId().toString(),"workOrder.read","sla.read");
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byOpen=queries.list(reader,"corr-sla-open",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,"OPEN",null,null,null,null,null,20));
  assertThat(byOpen.items()).extracting(WorkOrderView::id).containsExactlyInAnyOrder(openWo,breachedWo);
  assertThat(byOpen.totalCount()).isEqualTo(2);
  var byBreach=queries.list(reader,"corr-sla-breach",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,"BREACHED",null,null,null,null,null,20));
  assertThat(byBreach.items()).extracting(WorkOrderView::id).containsExactly(breachedWo);
  assertThat(byBreach.totalCount()).isEqualTo(1);
  assertThatThrownBy(()->queries.list(reader,"corr-sla-invalid",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,"WARN",null,null,null,null,null,20)))
    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("slaRisk");
 }

 @Test void listFiltersByNearSlaRiskWithinThirtyMinutes(){
  Scope a=scopeWithSla("tenant-test","A");
  UUID nearWo=receive(a,"ORDER-SLA-NEAR","a".repeat(64));
  UUID farWo=receive(a,"ORDER-SLA-FAR","b".repeat(64));
  UUID breachedWo=receive(a,"ORDER-SLA-NEAR-BREACH","c".repeat(64));
  UUID nearTask=seedActiveTask(a,nearWo,"SURVEY",null,null);
  UUID farTask=seedActiveTask(a,farWo,"SURVEY",null,null);
  UUID breachTask=seedActiveTask(a,breachedWo,"SURVEY",null,null);
  jdbc.sql("UPDATE tsk_task SET sla_ref='survey.response.sla' WHERE task_id IN (:a,:b,:c)")
    .param("a",nearTask).param("b",farTask).param("c",breachTask).update();
  Instant now=Instant.now();
  seedSla(a,nearWo,nearTask,"RUNNING",now,now.plusSeconds(15*60));
  seedSla(a,farWo,farTask,"RUNNING",now,now.plusSeconds(60*60));
  seedSla(a,breachedWo,breachTask,"BREACHED",now.minusSeconds(3600),now.minusSeconds(60));
  seedRole("reader","PROJECT",a.projectId().toString(),"workOrder.read","sla.read");
  CurrentPrincipal reader=principal("reader","tenant-test");
  var byNear=queries.list(reader,"corr-sla-near",
    new WorkOrderQuery(null,null,null,null,null,null,null,null,null,null,null,"NEAR",null,null,null,null,null,20));
  assertThat(byNear.items()).extracting(WorkOrderView::id).containsExactly(nearWo);
  assertThat(byNear.totalCount()).isEqualTo(1);
 }

 @Test void listExposesSlaRiskSummariesWhenSlaReadGranted(){
  Scope a=scopeWithSla("tenant-test","A");
  UUID wa=receive(a,"ORDER-SLA","f".repeat(64));
  UUID taskId=seedActiveTask(a,wa,"SURVEY",null,null);
  jdbc.sql("UPDATE tsk_task SET sla_ref='survey.response.sla' WHERE task_id=:id").param("id",taskId).update();
  seedRunningSla(a,wa,taskId);
  seedRole("reader","PROJECT",a.projectId().toString(),"workOrder.read","sla.read");
  CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-sla-list",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.slaRiskSummaries()).isNotNull();
  assertThat(page.slaRiskSummaries()).hasSize(1);
  assertThat(page.slaRiskSummaries().getFirst().workOrderId()).isEqualTo(wa);
  assertThat(page.slaRiskSummaries().getFirst().openCount()).isEqualTo(1);
  assertThat(page.slaRiskSummaries().getFirst().breachedCount()).isEqualTo(0);
 }

 @Test void listExposesExceptionSummariesWhenExceptionReadGranted(){
  Scope a=scope("tenant-test","A");
  UUID wa=receive(a,"ORDER-EXC","1".repeat(64));
  UUID none=receive(a,"ORDER-EXC-NONE","2".repeat(64));
  UUID taskId=seedActiveTask(a,wa,"SURVEY",null,null);
  seedOpenException(a,wa,taskId,"m450-a");
  seedOpenException(a,wa,taskId,"m450-b");
  seedRole("reader","PROJECT",a.projectId().toString(),"workOrder.read","operations.exception.read");
  CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-exc-list",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.exceptionSummaries()).isNotNull();
  assertThat(page.exceptionSummaries()).hasSize(1);
  assertThat(page.exceptionSummaries().getFirst().workOrderId()).isEqualTo(wa);
  assertThat(page.exceptionSummaries().getFirst().openCount()).isEqualTo(2);
  assertThat(page.items()).extracting(WorkOrderView::id).contains(wa,none);
 }

 @Test void listOmitsExceptionSummariesWithoutExceptionRead(){
  Scope a=scope("tenant-test","A");
  UUID wa=receive(a,"ORDER-EXC-DENY","3".repeat(64));
  UUID taskId=seedActiveTask(a,wa,"SURVEY",null,null);
  seedOpenException(a,wa,taskId,"m450-deny");
  seedRole("reader","PROJECT",a.projectId().toString(),"workOrder.read");
  CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-exc-deny",new WorkOrderQuery(null,null,null,null,20));
  assertThat(page.exceptionSummaries()).isNull();
 }

 @Test void crossTenantIsHiddenAndMigrationIsCurrent(){
  Scope a=scope("tenant-test","A"); UUID id=receive(a,"ORDER-A","c".repeat(64)); seedRole("reader","TENANT","tenant-test");
  assertThatThrownBy(()->queries.get(principal("reader","tenant-other"),"corr-cross",id))
    .isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
  assertThat(jdbc.sql("SELECT risk_level FROM auth_capability WHERE capability_code='workOrder.read'").query(String.class).single()).isEqualTo("NORMAL");
  assertThat(jdbc.sql("SELECT count(*) FROM pg_indexes WHERE indexname='ix_wo_work_order_tenant_project_received'").query(Long.class).single()).isOne();
  assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("153");
  assertThat(flyway.info().applied()).hasSize(155);
 }

 private Scope scope(String tenant,String code){return scope(tenant,code,false);}
 private Scope scopeWithSla(String tenant,String code){return scope(tenant,code,true);}
 private Scope scope(String tenant,String code,boolean withSla){
  UUID project=UUID.randomUUID();
  jdbc.sql("""
  INSERT INTO prj_project
  (project_id,tenant_id,project_code,client_id,project_name,starts_on,project_status,aggregate_version,created_at)
  VALUES (:id,:tenant,:code,'BYD',:name,current_date,'ACTIVE',1,now())
  """).param("id",project).param("tenant",tenant).param("code",code).param("name","项目"+code).update();
  String definition="{\"workflowCode\":\""+code+"\"}";
  UUID workflowAsset=configurations.publishAsset(new PublishConfigurationAssetCommand(tenant,ConfigurationAssetType.WORKFLOW,code+"-WF","1.0.0","1.0.0",definition,Sha256.digest(definition))).versionId();
  List<UUID> assets=new ArrayList<>(List.of(workflowAsset));
  UUID slaPolicyVersionId=null;
  String slaPolicyDigest=null;
  if(withSla){
   String slaDefinition="{\"policyKey\":\"survey.response.sla\",\"version\":\"1.0.0\",\"subjectType\":\"TASK\",\"taskTypes\":[\"SITE_SURVEY\"],\"startEvent\":\"TASK_CREATED\",\"stopEvent\":\"TASK_COMPLETED\",\"clockMode\":\"ELAPSED\",\"targetDurationSeconds\":3600}";
   slaPolicyDigest=Sha256.digest(slaDefinition);
   slaPolicyVersionId=configurations.publishAsset(new PublishConfigurationAssetCommand(tenant,ConfigurationAssetType.SLA,"survey.response.sla","1.0.0","1.0.0",slaDefinition,slaPolicyDigest)).versionId();
   assets.add(slaPolicyVersionId);
  }
  var bundle=configurations.publishBundle(new PublishConfigurationBundleCommand(tenant,project,code+"-B","1.0.0","BYD_OCEAN","HOME_CHARGING_SURVEY_INSTALL","370000",Instant.now().minusSeconds(60),null,assets));
  return new Scope(tenant,project,bundle,slaPolicyVersionId,slaPolicyDigest);
 }
 private UUID receive(Scope s,String external,String digest){return commands.receive(new ReceiveExternalWorkOrderCommand(s.tenant(),s.projectId(),"BYD","BYD_OCEAN","HOME_CHARGING_SURVEY_INSTALL",external,digest,s.bundle().bundleId(),s.bundle().bundleCode(),s.bundle().bundleVersion(),s.bundle().manifestDigest(),"370000","370100","370102","敏感姓名","13800000000","敏感地址","VIN123456789",LocalDateTime.of(2026,7,15,10,0),"corr","cause")).workOrderId();}
 /** M436/M437：批量夹具，跳过命令事件路径以快速铺满封顶计数或指定区域。 */
 private void seedWorkOrderRow(Scope s,String external,String digest){
  seedWorkOrderRow(s,external,digest,"370000","370100","370102");
 }
 private void seedWorkOrderRow(Scope s,String external,String digest,
         String province,String city,String district){
  Instant receivedAt=Instant.parse("2026-07-15T03:00:00Z");
  jdbc.sql("""
   INSERT INTO wo_work_order (
     id, tenant_id, project_id, client_code, brand_code, service_product_code,
     external_order_code, payload_digest, status,
     configuration_bundle_id, configuration_bundle_code, configuration_bundle_version,
     configuration_bundle_digest, province_code, city_code, district_code,
     customer_name, customer_mobile, service_address, vehicle_vin,
     external_dispatched_at, received_at, updated_at, version
   ) VALUES (
     :id, :tenantId, :projectId, 'BYD', 'BYD_OCEAN', 'HOME_CHARGING_SURVEY_INSTALL',
     :external, :digest, 'RECEIVED',
     :bundleId, :bundleCode, :bundleVersion, :bundleDigest,
     :province, :city, :district,
     '敏感姓名', '13800000000', '敏感地址', 'VIN123456789',
     :dispatchedAt, :receivedAt, :receivedAt, 1
   )
   """)
   .param("id",UUID.randomUUID())
   .param("tenantId",s.tenant())
   .param("projectId",s.projectId())
   .param("external",external)
   .param("digest",digest)
   .param("bundleId",s.bundle().bundleId())
   .param("bundleCode",s.bundle().bundleCode())
   .param("bundleVersion",s.bundle().bundleVersion())
   .param("bundleDigest",s.bundle().manifestDigest())
   .param("province",province)
   .param("city",city)
   .param("district",district)
   .param("dispatchedAt",LocalDateTime.of(2026,7,15,10,0))
   .param("receivedAt",java.sql.Timestamp.from(receivedAt))
   .update();
 }
 private UUID seedActiveTask(Scope s,UUID workOrderId,String stageCode,String status,String claimedBy){
  Instant now=Instant.parse("2026-07-15T04:00:00Z");
  String taskStatus=status==null?"READY":status;
  UUID taskId=UUID.randomUUID();
  jdbc.sql("""
   INSERT INTO tsk_task (
     task_id,tenant_id,task_type,task_kind,business_key,payload_digest,
     priority,status,next_run_at,attempt_count,max_attempts,correlation_id,
     version,created_at,updated_at,project_id,work_order_id,
     workflow_instance_id,stage_instance_id,workflow_node_instance_id,
     workflow_node_id,workflow_definition_version_id,workflow_definition_digest,
     form_ref,configuration_bundle_id,configuration_bundle_digest,stage_code,
     claimed_by,claimed_at,started_at
   ) VALUES (
     :id,:tenantId,'SITE_SURVEY','HUMAN',:businessKey,:digest,
     500,:status,:now,0,3,'corr-stage',1,:now,:now,:projectId,:workOrderId,
     :workflowId,:stageId,:nodeId,'SURVEY_NODE',:definitionId,:definitionDigest,
     'FORM',:bundleId,:bundleDigest,:stageCode,
     :claimedBy,:claimedAt,:startedAt
   )
   """)
   .param("id",taskId)
   .param("tenantId",s.tenant())
   .param("businessKey","m432:"+workOrderId)
   .param("digest","a".repeat(64))
   .param("now",java.sql.Timestamp.from(now))
   .param("status",taskStatus)
   .param("projectId",s.projectId())
   .param("workOrderId",workOrderId)
   .param("workflowId",UUID.randomUUID())
   .param("stageId",UUID.randomUUID())
   .param("nodeId",UUID.randomUUID())
   .param("definitionId",UUID.randomUUID())
   .param("definitionDigest","b".repeat(64))
   .param("bundleId",s.bundle().bundleId())
   .param("bundleDigest",s.bundle().manifestDigest())
   .param("stageCode",stageCode)
   .param("claimedBy",claimedBy)
   .param("claimedAt",claimedBy==null?null:java.sql.Timestamp.from(now))
   // CLAIMED 要求 started_at IS NULL；RUNNING 才填 started_at。
   .param("startedAt","RUNNING".equals(taskStatus)?java.sql.Timestamp.from(now):null)
   .update();
  return taskId;
 }
 private void seedActiveWorkflowStage(Scope s,UUID workOrderId,String stageCode,int sequenceNo){
  UUID workflowId=UUID.randomUUID();
  Instant now=Instant.parse("2026-07-15T05:00:00Z");
  jdbc.sql("""
   INSERT INTO wfl_workflow_instance (
     workflow_instance_id,tenant_id,project_id,work_order_id,configuration_bundle_id,
     configuration_bundle_digest,workflow_definition_version_id,workflow_key,workflow_version,definition_digest,
     status,start_event_id,correlation_id,version,started_at
   ) VALUES (
     :workflowId,:tenant,:projectId,:workOrderId,:bundleId,
     :bundleDigest,:definitionId,'BYD_HOME_CHARGING','1.0.0',:digest,
     'ACTIVE',:startEventId,'corr-wait-stage',1,:now
   )
   """)
   .param("workflowId",workflowId)
   .param("tenant",s.tenant())
   .param("projectId",s.projectId())
   .param("workOrderId",workOrderId)
   .param("bundleId",s.bundle().bundleId())
   .param("bundleDigest",s.bundle().manifestDigest())
   .param("definitionId",UUID.randomUUID())
   .param("digest","c".repeat(64))
   .param("startEventId",UUID.randomUUID())
   .param("now",java.sql.Timestamp.from(now))
   .update();
  jdbc.sql("""
   INSERT INTO wfl_stage_instance (
     stage_instance_id,tenant_id,workflow_instance_id,work_order_id,stage_code,
     sequence_no,status,activation_event_id,version,activated_at
   ) VALUES (
     :stageId,:tenant,:workflowId,:workOrderId,:stageCode,
     :sequenceNo,'ACTIVE',:activationEventId,1,:now
   )
   """)
   .param("stageId",UUID.randomUUID())
   .param("tenant",s.tenant())
   .param("workflowId",workflowId)
   .param("workOrderId",workOrderId)
   .param("stageCode",stageCode)
   .param("sequenceNo",sequenceNo)
   .param("activationEventId",UUID.randomUUID())
   .param("now",java.sql.Timestamp.from(now))
   .update();
 }
 /** M447：最小 OPEN ReviewCase（经 snapshot → task → work_order）。 */
 private void seedOpenReviewCase(Scope s,UUID taskId){
  UUID snapshotId=seedEvidenceSnapshot(s,taskId,"open-review");
  String digest=Sha256.digest("open-review:"+snapshotId);
  jdbc.sql("""
   INSERT INTO evd_review_case (
     review_case_id, tenant_id, project_id, task_id, evidence_set_snapshot_id,
     snapshot_content_digest, scope_type, origin, policy_version, status,
     created_by, created_at, decided_at
   ) VALUES (
     :id, :tenant, :project, :task, :snapshot, :digest,
     'EVIDENCE_SET_SNAPSHOT', 'INTERNAL', 'POLICY_V1', 'OPEN',
     'fixture', now(), NULL)
   """)
   .param("id",UUID.randomUUID())
   .param("tenant",s.tenant())
   .param("project",s.projectId())
   .param("task",taskId)
   .param("snapshot",snapshotId)
   .param("digest",digest)
   .update();
 }
 /** M447：REJECTED Review + OPEN CorrectionCase。 */
 private void seedOpenCorrectionCase(Scope s,UUID taskId){
  UUID snapshotId=seedEvidenceSnapshot(s,taskId,"open-correction");
  String digest=Sha256.digest("open-correction:"+snapshotId);
  UUID reviewCaseId=UUID.randomUUID();
  UUID reviewDecisionId=UUID.randomUUID();
  jdbc.sql("""
   INSERT INTO evd_review_case (
     review_case_id, tenant_id, project_id, task_id, evidence_set_snapshot_id,
     snapshot_content_digest, scope_type, origin, policy_version, status,
     created_by, created_at, decided_at
   ) VALUES (
     :id, :tenant, :project, :task, :snapshot, :digest,
     'EVIDENCE_SET_SNAPSHOT', 'INTERNAL', 'POLICY_V1', 'REJECTED',
     'fixture', now(), now())
   """)
   .param("id",reviewCaseId)
   .param("tenant",s.tenant())
   .param("project",s.projectId())
   .param("task",taskId)
   .param("snapshot",snapshotId)
   .param("digest",digest)
   .update();
  jdbc.sql("""
   INSERT INTO evd_review_decision (
     review_decision_id, tenant_id, project_id, review_case_id,
     decision_ordinal, decision, decision_source, reason_codes,
     note, approval_ref, decided_by, decided_at
   ) VALUES (
     :id, :tenant, :project, :review,
     1, 'REJECTED', 'INTERNAL', '["MISSING_PHOTO"]'::jsonb,
     NULL, NULL, 'fixture', now())
   """)
   .param("id",reviewDecisionId)
   .param("tenant",s.tenant())
   .param("project",s.projectId())
   .param("review",reviewCaseId)
   .update();
  jdbc.sql("""
   INSERT INTO evd_correction_case (
     correction_case_id, tenant_id, project_id, task_id,
     source_review_case_id, source_review_decision_id,
     source_evidence_set_snapshot_id, source_snapshot_content_digest,
     reason_codes, status, created_by, created_at
   ) VALUES (
     :id, :tenant, :project, :task,
     :review, :decision, :snapshot, :digest,
     '["MISSING_PHOTO"]'::jsonb, 'OPEN', 'fixture', now())
   """)
   .param("id",UUID.randomUUID())
   .param("tenant",s.tenant())
   .param("project",s.projectId())
   .param("task",taskId)
   .param("review",reviewCaseId)
   .param("decision",reviewDecisionId)
   .param("snapshot",snapshotId)
   .param("digest",digest)
   .update();
 }
 private UUID seedEvidenceSnapshot(Scope s,UUID taskId,String marker){
  UUID resolutionId=UUID.randomUUID();
  String eventDigest=Sha256.digest(taskId+marker);
  jdbc.sql("""
   INSERT INTO evd_task_evidence_resolution (
     resolution_id, tenant_id, project_id, task_id, configuration_bundle_id,
     configuration_bundle_digest, stage_code, source_event_id, source_event_digest,
     resolver_version, condition_input_digest, resolution_explanation,
     generation_no, condition_fact_type, condition_fact_ref, condition_fact_revision,
     slot_count, resolved_at)
   VALUES (
     :id, :tenant, :project, :task, :bundle, :digest, 'SURVEY', :event,
     :eventDigest, 'FIXED_EVIDENCE_V1',
     '44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a',
     CAST('{"kind":"TEST_FIXED_CONTEXT"}' AS jsonb),
     1, 'TASK_CREATED', CAST(:event AS varchar), 0, 0, now())
   """)
   .param("id",resolutionId)
   .param("tenant",s.tenant())
   .param("project",s.projectId())
   .param("task",taskId)
   .param("bundle",s.bundle().bundleId())
   .param("digest",s.bundle().manifestDigest())
   .param("event",UUID.randomUUID())
   .param("eventDigest",eventDigest)
   .update();
  UUID snapshotId=UUID.randomUUID();
  String snapshotDigest=Sha256.digest(marker+":"+snapshotId);
  jdbc.sql("""
   INSERT INTO evd_evidence_set_snapshot (
     evidence_set_snapshot_id, tenant_id, project_id, task_id, resolution_id,
     purpose, member_count, content_digest, eligibility_summary, created_by, created_at
   ) VALUES (
     :id, :tenant, :project, :task, :resolution,
     'TASK_SUBMISSION', 0, :digest, '{}'::jsonb, 'fixture', now())
   """)
   .param("id",snapshotId)
   .param("tenant",s.tenant())
   .param("project",s.projectId())
   .param("task",taskId)
   .param("resolution",resolutionId)
   .param("digest",snapshotDigest)
   .update();
  return snapshotId;
 }
 private void seedNetworkAndTechnician(String tenant,UUID networkId,String networkName,
         UUID techProfileId,UUID techPrincipal,String techName){
  UUID partner=UUID.randomUUID();
  String codeSuffix=networkId.toString().substring(0,8);
  jdbc.sql("""
   INSERT INTO net_partner_organization (
     partner_organization_id, tenant_id, partner_code, partner_name,
     partner_status, aggregate_version, created_at, updated_at
   ) VALUES (:id,:tenant,:code,:name,'ACTIVE',1,now(),now())
   """).param("id",partner).param("tenant",tenant)
   .param("code","P-"+codeSuffix).param("name","Partner "+codeSuffix).update();
  jdbc.sql("""
   INSERT INTO net_service_network (
     service_network_id, tenant_id, partner_organization_id, network_code,
     network_name, network_status, aggregate_version, created_at, updated_at
   ) VALUES (:id,:tenant,:partner,:code,:name,'ACTIVE',1,now(),now())
   """).param("id",networkId).param("tenant",tenant).param("partner",partner)
   .param("code","N-"+codeSuffix).param("name",networkName).update();
  jdbc.sql("""
   INSERT INTO idn_security_principal (
     principal_id,tenant_id,principal_type,principal_status,
     aggregate_version,created_at,updated_at
   ) VALUES (:id,:tenant,'USER','ACTIVE',1,now(),now())
   """).param("id",techPrincipal).param("tenant",tenant).update();
  jdbc.sql("""
   INSERT INTO net_technician_profile (
     technician_profile_id, tenant_id, principal_id, display_name, profile_status,
     aggregate_version, created_at, updated_at
   ) VALUES (:id,:tenant,:principal,:name,'ACTIVE',1,now(),now())
   """).param("id",techProfileId).param("tenant",tenant).param("principal",techPrincipal).param("name",techName).update();
 }
 private void seedActiveAssignment(Scope s,UUID workOrderId,UUID taskId,String level,String assigneeId){
  Instant now=Instant.parse("2026-07-15T05:00:00Z");
  jdbc.sql("""
   INSERT INTO dsp_service_assignment (
     service_assignment_id, tenant_id, work_order_id, task_id,
     responsibility_level, assignee_id, business_type, source_decision_id,
     status, activation_saga_id, effective_from, created_by, created_at,
     authority_assignment_id, authority_version,
     fence_decision_id, fence_policy_version
   ) VALUES (
     :id, :tenant, :workOrderId, :taskId,
     :level, :assignee, 'INSTALLATION', :decision,
     'ACTIVE', :saga, :now, 'test', :now,
     :authorityId, 1,
     :fenceDecision, :fencePolicy
   )
   """)
   .param("id",UUID.randomUUID())
   .param("tenant",s.tenant())
   .param("workOrderId",workOrderId)
   .param("taskId",taskId)
   .param("level",level)
   .param("assignee",assigneeId)
   .param("decision","decision://"+level)
   .param("saga",UUID.randomUUID())
   .param("now",java.sql.Timestamp.from(now))
   .param("authorityId","authority://"+level)
   .param("fenceDecision","fence://"+level)
   .param("fencePolicy","fence-policy-v1")
   .update();
 }
 private void seedRunningSla(Scope s,UUID workOrderId,UUID taskId){
  Instant now=Instant.parse("2026-07-15T04:00:00Z");
  seedSla(s,workOrderId,taskId,"RUNNING",now,now.plusSeconds(3600));
 }
 /** M450：OPEN 运营异常夹具；project_id 使用工单所属项目。 */
 private void seedOpenException(Scope s,UUID workOrderId,UUID taskId,String marker){
  Instant openedAt=Instant.parse("2026-07-15T05:00:00Z");
  jdbc.sql("""
   INSERT INTO ops_operational_exception (
     exception_id, tenant_id, project_id, source_type, source_id, source_attempt_id,
     source_task_type, category_code, severity_code, error_code, status,
     work_order_id, task_id, occurrence_count, correlation_id,
     opened_at, last_detected_at, aggregate_version
   ) VALUES (
     :id, :tenant, :projectId, 'TEST', :sourceId, :attemptId,
     'operations.test', 'AUTOMATION_FINAL_FAILURE', 'P2', 'TEST_FAILURE', 'OPEN',
     :workOrderId, :taskId, 1, :corr,
     :openedAt, :openedAt, 1
   )
   """)
   .param("id",UUID.randomUUID())
   .param("tenant",s.tenant())
   .param("projectId",s.projectId())
   .param("sourceId","m450-"+marker)
   .param("attemptId",UUID.randomUUID())
   .param("workOrderId",workOrderId)
   .param("taskId",taskId)
   .param("corr","corr-m450-"+marker)
   .param("openedAt",java.sql.Timestamp.from(openedAt))
   .update();
 }
 private void seedBreachedSla(Scope s,UUID workOrderId,UUID taskId){
  Instant now=Instant.parse("2026-07-15T04:00:00Z");
  Instant deadline=now.plusSeconds(3600);
  seedSla(s,workOrderId,taskId,"BREACHED",now,deadline);
 }
 private void seedSla(Scope s,UUID workOrderId,UUID taskId,String status,Instant startedAt,Instant deadline){
  Objects.requireNonNull(s.slaPolicyVersionId(),"scope must include SLA policy");
  boolean breached="BREACHED".equals(status);
  jdbc.sql("""
   INSERT INTO sla_instance (
     sla_instance_id,tenant_id,project_id,work_order_id,task_id,sla_ref,
     policy_version_id,policy_semantic_version,policy_content_digest,
     clock_mode,target_duration_seconds,start_event_id,started_at,deadline_at,
     status,breached_at,breach_detected_at,aggregate_version,correlation_id,created_at,updated_at
   ) VALUES (
     :id,:tenantId,:projectId,:workOrderId,:taskId,'survey.response.sla',
     :policyVersionId,'1.0.0',:policyDigest,
     'ELAPSED',3600,:eventId,:now,:deadline,
     :status,:breachedAt,:breachDetectedAt,1,'corr-sla',:now,:now
   )
   """)
   .param("id",UUID.randomUUID())
   .param("tenantId",s.tenant())
   .param("projectId",s.projectId())
   .param("workOrderId",workOrderId)
   .param("taskId",taskId)
   .param("policyVersionId",s.slaPolicyVersionId())
   .param("policyDigest",s.slaPolicyDigest())
   .param("eventId",UUID.randomUUID())
   .param("now",java.sql.Timestamp.from(startedAt))
   .param("deadline",java.sql.Timestamp.from(deadline))
   .param("status",status)
   .param("breachedAt",breached?java.sql.Timestamp.from(deadline):null)
   .param("breachDetectedAt",breached?java.sql.Timestamp.from(deadline.plusSeconds(1)):null)
   .update();
 }
 private void seedPerson(String tenant,UUID principalId,String displayName){
  jdbc.sql("""
   INSERT INTO idn_security_principal (
     principal_id,tenant_id,principal_type,principal_status,
     aggregate_version,created_at,updated_at
   ) VALUES (:id,:tenant,'USER','ACTIVE',1,now(),now())
   """).param("id",principalId).param("tenant",tenant).update();
  jdbc.sql("""
   INSERT INTO idn_person_profile (
     principal_id,tenant_id,display_name,employee_number,
     profile_version,created_at,updated_at,updated_by
   ) VALUES (:id,:tenant,:name,NULL,1,now(),now(),'test')
   """).param("id",principalId).param("tenant",tenant).param("name",displayName).update();
 }
 private void seedRole(String principal,String scope,String ref,String... capabilities){
  UUID role=UUID.randomUUID();
  jdbc.sql("INSERT INTO auth_role(role_id,tenant_id,role_code,role_name,role_status,created_at) VALUES(:id,'tenant-test',:code,:code,'ACTIVE',now())")
    .param("id",role).param("code",principal+"-role").update();
  String[] caps=capabilities==null||capabilities.length==0?new String[]{"workOrder.read"}:capabilities;
  for(String capability:caps){
   jdbc.sql("INSERT INTO auth_role_capability(role_id,capability_code,granted_at) VALUES(:id,:cap,now())")
     .param("id",role).param("cap",capability).update();
  }
  jdbc.sql("INSERT INTO auth_role_grant(grant_id,tenant_id,principal_id,role_id,scope_type,scope_ref,valid_from,source_code,approval_ref,created_at) VALUES(:grant,'tenant-test',:principal,:role,:scope,:ref,now()-interval '1 day','TEST','m68',now())")
    .param("grant",UUID.randomUUID()).param("principal",principal).param("role",role).param("scope",scope).param("ref",ref).update();
 }
 private static CurrentPrincipal principal(String id,String tenant){return new CurrentPrincipal(id,tenant,CurrentPrincipal.PrincipalType.USER,"m68-test",Set.of());}
 private record Scope(String tenant,UUID projectId,ConfigurationBundleReference bundle,UUID slaPolicyVersionId,String slaPolicyDigest){}
}
