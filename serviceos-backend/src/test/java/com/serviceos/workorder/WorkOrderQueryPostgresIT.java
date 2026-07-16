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
  wo_work_order,cfg_configuration_bundle_item,cfg_configuration_bundle,cfg_configuration_asset_version,
  prj_project,auth_role_field_policy,auth_role_grant,auth_role_capability,auth_role CASCADE
  """).update();}

 @Test void projectScopeFiltersPagesAndDetailWhileCursorBindsFilters(){
  Scope a=scope("tenant-test","A"); Scope b=scope("tenant-test","B");
  UUID wa=receive(a,"ORDER-A","a".repeat(64)); UUID wb=receive(b,"ORDER-B","b".repeat(64));
  seedRole("reader","PROJECT",a.projectId().toString()); CurrentPrincipal reader=principal("reader","tenant-test");
  var page=queries.list(reader,"corr-list",new WorkOrderQuery(null,null,"RECEIVED",null,1));
  assertThat(page.items()).extracting(WorkOrderView::id).containsExactly(wa);
  assertThat(page.items().getFirst().externalOrderCode()).isEqualTo("ORDER-A");
  assertThat(queries.get(reader,"corr-get",wa).workOrder().configurationBundleId()).isEqualTo(a.bundle().bundleId());
  assertThatThrownBy(()->queries.get(reader,"corr-deny",wb)).isInstanceOfSatisfying(BusinessProblem.class,
    p->assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
  assertThat(jdbc.sql("SELECT decision_code FROM aud_audit_record ORDER BY occurred_at DESC LIMIT 1").query(String.class).single()).isEqualTo("DENY");
  seedRole("tenant-reader","TENANT","tenant-test");
  var first=queries.list(principal("tenant-reader","tenant-test"),"corr-page",new WorkOrderQuery(null,null,null,null,1));
  assertThat(first.nextCursor()).isNotBlank();
  assertThatThrownBy(()->queries.list(principal("tenant-reader","tenant-test"),"corr-cursor",
    new WorkOrderQuery("BYD",null,"ACTIVE",first.nextCursor(),1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cursor");
 }

 @Test void crossTenantIsHiddenAndMigrationIsCurrent(){
  Scope a=scope("tenant-test","A"); UUID id=receive(a,"ORDER-A","c".repeat(64)); seedRole("reader","TENANT","tenant-test");
  assertThatThrownBy(()->queries.get(principal("reader","tenant-other"),"corr-cross",id))
    .isInstanceOfSatisfying(BusinessProblem.class,p->assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
  assertThat(jdbc.sql("SELECT risk_level FROM auth_capability WHERE capability_code='workOrder.read'").query(String.class).single()).isEqualTo("NORMAL");
  assertThat(jdbc.sql("SELECT count(*) FROM pg_indexes WHERE indexname='ix_wo_work_order_tenant_project_received'").query(Long.class).single()).isOne();
  assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("075"); assertThat(flyway.info().applied()).hasSize(77);
 }

 private Scope scope(String tenant,String code){UUID project=UUID.randomUUID();jdbc.sql("""
  INSERT INTO prj_project
  (project_id,tenant_id,project_code,client_id,project_name,starts_on,project_status,aggregate_version,created_at)
  VALUES (:id,:tenant,:code,'BYD',:name,current_date,'ACTIVE',1,now())
  """).param("id",project).param("tenant",tenant).param("code",code).param("name","项目"+code).update();
  String definition="{\"workflowCode\":\""+code+"\"}"; UUID asset=configurations.publishAsset(new PublishConfigurationAssetCommand(tenant,ConfigurationAssetType.WORKFLOW,code+"-WF","1.0.0","1.0.0",definition,Sha256.digest(definition))).versionId();
  var bundle=configurations.publishBundle(new PublishConfigurationBundleCommand(tenant,project,code+"-B","1.0.0","BYD_OCEAN","HOME_CHARGING_SURVEY_INSTALL","370000",Instant.now().minusSeconds(60),null,List.of(asset))); return new Scope(tenant,project,bundle);}
 private UUID receive(Scope s,String external,String digest){return commands.receive(new ReceiveExternalWorkOrderCommand(s.tenant(),s.projectId(),"BYD","BYD_OCEAN","HOME_CHARGING_SURVEY_INSTALL",external,digest,s.bundle().bundleId(),s.bundle().bundleCode(),s.bundle().bundleVersion(),s.bundle().manifestDigest(),"370000","370100","370102","敏感姓名","13800000000","敏感地址","VIN123456789",LocalDateTime.of(2026,7,15,10,0),"corr","cause")).workOrderId();}
 private void seedRole(String principal,String scope,String ref){UUID role=UUID.randomUUID();jdbc.sql("INSERT INTO auth_role(role_id,tenant_id,role_code,role_name,role_status,created_at) VALUES(:id,'tenant-test',:code,:code,'ACTIVE',now())").param("id",role).param("code",principal+"-role").update();jdbc.sql("INSERT INTO auth_role_capability(role_id,capability_code,granted_at) VALUES(:id,'workOrder.read',now())").param("id",role).update();jdbc.sql("INSERT INTO auth_role_grant(grant_id,tenant_id,principal_id,role_id,scope_type,scope_ref,valid_from,source_code,approval_ref,created_at) VALUES(:grant,'tenant-test',:principal,:role,:scope,:ref,now()-interval '1 day','TEST','m68',now())").param("grant",UUID.randomUUID()).param("principal",principal).param("role",role).param("scope",scope).param("ref",ref).update();}
 private static CurrentPrincipal principal(String id,String tenant){return new CurrentPrincipal(id,tenant,CurrentPrincipal.PrincipalType.USER,"m68-test",Set.of());}
 private record Scope(String tenant,UUID projectId,ConfigurationBundleReference bundle){}
}
