package com.serviceos.task;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.*;
import com.serviceos.task.api.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** M70 任务范围、个人待办、稳定分页、详情及拒绝审计的真实 PostgreSQL 证据。 */
@Testcontainers(disabledWithoutDocker=true)
@SpringBootTest(classes=ServiceOsApplication.class,webEnvironment=SpringBootTest.WebEnvironment.NONE)
class TaskDirectoryPostgresIT {
 @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine")).withDatabaseName("serviceos").withUsername("serviceos_test").withPassword("serviceos_test");
 @DynamicPropertySource static void props(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
 @Autowired TaskDirectoryQueryService queries;@Autowired JdbcClient jdbc;@Autowired Flyway flyway;
 UUID projectA,projectB,taskA,taskB,tenantTask;
 @BeforeEach void seed(){jdbc.sql("""
  TRUNCATE TABLE aud_audit_record,tsk_task_assignment,tsk_task_assignment_batch,tsk_task_execution_attempt,
   tsk_task,prj_project_region,prj_project_network,prj_project,auth_role_field_policy,auth_role_grant,
   auth_role_capability,auth_role CASCADE
  """).update();projectA=project("A","CN-3702");projectB=project("B","CN-4403");
  taskA=task(projectA,"TASK-A","HUMAN",900,"READY",Instant.parse("2026-07-16T01:00:00Z"));
  taskB=task(projectB,"TASK-B","HUMAN",800,"READY",Instant.parse("2026-07-16T02:00:00Z"));
  tenantTask=task(null,"TENANT-REPAIR","AUTOMATED",1000,"PENDING",Instant.parse("2026-07-16T00:30:00Z"));
  assignment(taskA,"reader-a","CANDIDATE");seedRole("tenant-reader","TENANT","tenant-test");seedRole("reader-a","PROJECT",projectA.toString());seedRole("region-reader","REGION","CN-3702");}
 @Test void tenantAndProjectScopesFilterAndPageInPriorityOrder(){var tenant=queries.list(p("tenant-reader","tenant-test"),"corr-tenant",new TaskDirectoryQuery(null,null,null,null,null,2));assertThat(tenant.items()).extracting(TaskDirectoryItem::id).containsExactly(tenantTask,taskA);assertThat(queries.list(p("tenant-reader","tenant-test"),"corr-page",new TaskDirectoryQuery(null,null,null,null,tenant.nextCursor(),2)).items()).extracting(TaskDirectoryItem::id).containsExactly(taskB);var project=queries.list(p("reader-a","tenant-test"),"corr-project",new TaskDirectoryQuery(null,"HUMAN","READY","me",null,20));assertThat(project.items()).extracting(TaskDirectoryItem::id).containsExactly(taskA);var region=queries.list(p("region-reader","tenant-test"),"corr-region",new TaskDirectoryQuery(null,null,null,null,null,20));assertThat(region.items()).extracting(TaskDirectoryItem::id).containsExactly(taskA);}
 @Test void cursorBindsFiltersAndDetailReturnsFrozenReferences(){var first=queries.list(p("tenant-reader","tenant-test"),"corr-first",new TaskDirectoryQuery(null,null,null,null,null,1));assertThatThrownBy(()->queries.list(p("tenant-reader","tenant-test"),"corr-changed",new TaskDirectoryQuery(null,"HUMAN",null,null,first.nextCursor(),1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cursor");TaskDetail d=queries.get(p("reader-a","tenant-test"),"corr-detail",taskA);assertThat(d.formRef()).isEqualTo("survey.form");assertThat(d.configurationBundleDigest()).isEqualTo("b".repeat(64));assertThat(d.candidateUserIds()).containsExactly("reader-a");assertThat(d.inputVersionRefs()).isEmpty();}
 @Test void deniedAndCrossTenantDetailsFailClosed(){assertThatThrownBy(()->queries.get(p("reader-a","tenant-test"),"corr-deny",taskB)).isInstanceOfSatisfying(BusinessProblem.class,x->assertThat(x.code()).isEqualTo(ProblemCode.ACCESS_DENIED));assertThat(jdbc.sql("SELECT decision_code FROM aud_audit_record ORDER BY occurred_at DESC LIMIT 1").query(String.class).single()).isEqualTo("DENY");assertThatThrownBy(()->queries.get(p("tenant-reader","tenant-other"),"corr-cross",taskA)).isInstanceOfSatisfying(BusinessProblem.class,x->assertThat(x.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));}
 @Test void tenantTaskRequiresTenantWideGrantAndMigrationIsCurrent(){assertThat(queries.get(p("tenant-reader","tenant-test"),"corr-tenant-detail",tenantTask).task().id()).isEqualTo(tenantTask);assertThatThrownBy(()->queries.get(p("reader-a","tenant-test"),"corr-tenant-deny",tenantTask)).isInstanceOfSatisfying(BusinessProblem.class,x->assertThat(x.code()).isEqualTo(ProblemCode.ACCESS_DENIED));assertThat(jdbc.sql("SELECT risk_level FROM auth_capability WHERE capability_code='task.read'").query(String.class).single()).isEqualTo("NORMAL");assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("080");assertThat(flyway.info().applied()).hasSize(82);}
 private UUID project(String code,String region){UUID id=UUID.randomUUID();jdbc.sql("INSERT INTO prj_project(project_id,tenant_id,project_code,client_id,project_name,starts_on,project_status,aggregate_version,created_at) VALUES(:id,'tenant-test',:code,'client',:code,current_date,'ACTIVE',1,now())").param("id",id).param("code",code).update();jdbc.sql("INSERT INTO prj_project_region(project_region_id,tenant_id,project_id,region_code,valid_from,created_by,created_at) VALUES(:rid,'tenant-test',:id,:region,now()-interval '1 day','test',now())").param("rid",UUID.randomUUID()).param("id",id).param("region",region).update();return id;}
 private UUID task(UUID project,String type,String kind,int priority,String status,Instant next){UUID id=UUID.randomUUID(),wf=UUID.randomUUID(),stage=UUID.randomUUID(),node=UUID.randomUUID(),def=UUID.randomUUID(),bundle=UUID.randomUUID();jdbc.sql("""
  INSERT INTO tsk_task(task_id,tenant_id,task_type,task_kind,business_key,payload_digest,priority,status,next_run_at,attempt_count,max_attempts,correlation_id,version,created_at,updated_at,project_id,work_order_id,workflow_instance_id,stage_instance_id,workflow_node_instance_id,workflow_node_id,workflow_definition_version_id,workflow_definition_digest,configuration_bundle_id,configuration_bundle_digest,stage_code,form_ref)
  VALUES(:id,'tenant-test',:type,:kind,:business,:digest,:priority,:status,:next,0,3,'corr',1,:created,:created,:project,:workOrder,:wf,:stage,:node,:nodeCode,:def,:defDigest,:bundle,:bundleDigest,:stageCode,:formRef)
  """ )
  .param("id",id).param("type",type).param("kind",kind).param("business",type+id).param("digest","a".repeat(64)).param("priority",priority).param("status",status).param("next",java.sql.Timestamp.from(next)).param("created",java.sql.Timestamp.from(next.minusSeconds(priority))).param("project",project).param("workOrder",project==null?null:UUID.randomUUID()).param("wf",project==null?null:wf).param("stage",project==null?null:stage).param("node",project==null?null:node).param("nodeCode",project==null?null:"NODE").param("def",project==null?null:def).param("defDigest",project==null?null:"c".repeat(64)).param("bundle",project==null?null:bundle).param("bundleDigest",project==null?null:"b".repeat(64)).param("stageCode",project==null?null:"SURVEY").param("formRef",project==null?null:"survey.form").update();return id;}
 private void assignment(UUID task,String principal,String kind){jdbc.sql("INSERT INTO tsk_task_assignment(task_assignment_id,tenant_id,task_id,assignment_kind,principal_type,principal_id,status,source_type,source_id,effective_from,created_by,created_at) VALUES(:id,'tenant-test',:task,:kind,'USER',:principal,'ACTIVE','MANUAL','test',now()-interval '1 hour','test',now())").param("id",UUID.randomUUID()).param("task",task).param("kind",kind).param("principal",principal).update();}
 private void seedRole(String principal,String scope,String ref){UUID role=UUID.randomUUID();jdbc.sql("INSERT INTO auth_role(role_id,tenant_id,role_code,role_name,role_status,created_at) VALUES(:id,'tenant-test',:code,:code,'ACTIVE',now())").param("id",role).param("code",principal+"-role").update();jdbc.sql("INSERT INTO auth_role_capability(role_id,capability_code,granted_at) VALUES(:id,'task.read',now())").param("id",role).update();jdbc.sql("INSERT INTO auth_role_grant(grant_id,tenant_id,principal_id,role_id,scope_type,scope_ref,valid_from,source_code,approval_ref,created_at) VALUES(:id,'tenant-test',:principal,:role,:scope,:ref,now()-interval '1 day','TEST','m70',now())").param("id",UUID.randomUUID()).param("principal",principal).param("role",role).param("scope",scope).param("ref",ref).update();}
 private static CurrentPrincipal p(String id,String tenant){return new CurrentPrincipal(id,tenant,CurrentPrincipal.PrincipalType.USER,"m70",Set.of());}
}
