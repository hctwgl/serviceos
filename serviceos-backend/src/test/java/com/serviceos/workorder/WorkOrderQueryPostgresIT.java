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
  sla_instance, tsk_task, wo_work_order,cfg_configuration_bundle_item,cfg_configuration_bundle,cfg_configuration_asset_version,
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
  // M435：新建时 updatedAt 与 receivedAt 同源，且二者均非 null。
  assertThat(page.items().getFirst().updatedAt()).isEqualTo(page.items().getFirst().receivedAt());
  var detail=queries.get(reader,"corr-get",wa).workOrder();
  assertThat(detail.configurationBundleId()).isEqualTo(a.bundle().bundleId());
  assertThat(detail.maskedCustomerName()).isEqualTo("敏***");
  assertThat(detail.maskedCustomerPhone()).isEqualTo("*******0000");
  assertThat(detail.currentStageCode()).isNull();
  assertThat(detail.currentClaimedBy()).isNull();
  assertThat(detail.currentAssigneeDisplayName()).isNull();
  assertThat(detail.updatedAt()).isEqualTo(detail.receivedAt());
  // M434：无 sla.read 时页级旁载省略（null），不伪造 []/0。
  assertThat(page.slaRiskSummaries()).isNull();
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
  assertThat(page.items().getFirst().currentClaimedBy()).isNull();
  assertThat(queries.get(reader,"corr-stage-get",wa).workOrder().currentStageCode()).isEqualTo("SURVEY");
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

 @Test void listExposesTotalCountAcrossPagesAndCapsAtLimit(){
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
  var capped=queries.list(reader,"corr-total-cap",new WorkOrderQuery(null,null,null,null,20));
  assertThat(capped.totalCount()).isEqualTo(100);
  assertThat(capped.totalCountTruncated()).isTrue();
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

 @Test void crossTenantIsHiddenAndMigrationIsCurrent(){
  Scope a=scope("tenant-test","A"); UUID id=receive(a,"ORDER-A","c".repeat(64)); seedRole("reader","TENANT","tenant-test");
  assertThatThrownBy(()->queries.get(principal("reader","tenant-other"),"corr-cross",id))
    .isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
  assertThat(jdbc.sql("SELECT risk_level FROM auth_capability WHERE capability_code='workOrder.read'").query(String.class).single()).isEqualTo("NORMAL");
  assertThat(jdbc.sql("SELECT count(*) FROM pg_indexes WHERE indexname='ix_wo_work_order_tenant_project_received'").query(Long.class).single()).isOne();
  assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("146");
  assertThat(flyway.info().applied()).hasSize(148);
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
 /** M436：批量夹具，跳过命令事件路径以快速铺满封顶计数。 */
 private void seedWorkOrderRow(Scope s,String external,String digest){
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
     '370000', '370100', '370102',
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
 private void seedRunningSla(Scope s,UUID workOrderId,UUID taskId){
  Objects.requireNonNull(s.slaPolicyVersionId(),"scope must include SLA policy");
  Instant now=Instant.parse("2026-07-15T04:00:00Z");
  jdbc.sql("""
   INSERT INTO sla_instance (
     sla_instance_id,tenant_id,project_id,work_order_id,task_id,sla_ref,
     policy_version_id,policy_semantic_version,policy_content_digest,
     clock_mode,target_duration_seconds,start_event_id,started_at,deadline_at,
     status,aggregate_version,correlation_id,created_at,updated_at
   ) VALUES (
     :id,:tenantId,:projectId,:workOrderId,:taskId,'survey.response.sla',
     :policyVersionId,'1.0.0',:policyDigest,
     'ELAPSED',3600,:eventId,:now,:deadline,
     'RUNNING',1,'corr-sla',:now,:now
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
   .param("now",java.sql.Timestamp.from(now))
   .param("deadline",java.sql.Timestamp.from(now.plusSeconds(3600)))
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
