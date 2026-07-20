package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.CreateProjectFulfillmentProfileCommand;
import com.serviceos.configuration.api.ProjectFulfillmentDraftView;
import com.serviceos.configuration.api.ProjectFulfillmentProfileDetail;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentResolveQuery;
import com.serviceos.configuration.api.ProjectFulfillmentResolver;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionView;
import com.serviceos.configuration.api.ProjectFulfillmentValidationIssue;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.UpdateProjectFulfillmentDraftCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** M378：项目履约 Profile/Revision 生命周期与不可变发布。 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectFulfillmentProfilePostgresIT {
    private static final String TENANT = "tenant-pfp-m378-it";
    private static final String ACTOR = "pfp-operator";
    private static final String PROJECT_CODE = "PFP-M378";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @org.springframework.test.context.DynamicPropertySource
    static void properties(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("serviceos.outbox.worker-id", () -> "pfp-m378-it");
    }

    @Autowired ProjectFulfillmentProfileService profiles;
    @Autowired ProjectFulfillmentResolver resolver;
    @Autowired ConfigurationService configurations;
    @Autowired JdbcClient jdbc;

    UUID projectId;
    UUID workflowVersionId;
    ConfigurationBundleReference bundle;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE wo_work_order,
                    cfg_project_fulfillment_revision, cfg_project_fulfillment_profile,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    aud_audit_record, auth_role_grant, auth_role_capability, auth_role
                    CASCADE
                """).update();
        seedGrants();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'BYD', '履约配置试点项目',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("projectCode", PROJECT_CODE)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String definition = "{"
                + "\"workflowKey\":\"byd.survey-install\",\"semanticVersion\":\"1.0.0\",\"startNodeId\":\"START\","
                + "\"nodes\":["
                + "{\"nodeId\":\"START\",\"nodeType\":\"START\",\"name\":\"start\"},"
                + "{\"nodeId\":\"TASK_A\",\"nodeType\":\"SERVICE_TASK\",\"name\":\"task-a\","
                + "\"stageCode\":\"STAGE_A\",\"taskType\":\"DESIGNER_TASK\"},"
                + "{\"nodeId\":\"END\",\"nodeType\":\"END\",\"name\":\"end\"}],"
                + "\"transitions\":["
                + "{\"transitionId\":\"t1\",\"from\":\"START\",\"to\":\"TASK_A\"},"
                + "{\"transitionId\":\"t2\",\"from\":\"TASK_A\",\"to\":\"END\"}]}";
        workflowVersionId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "byd.survey-install", "1.0.0",
                "1.0.0", definition, Sha256.digest(definition))).versionId();
        bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "PFP-BUNDLE", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000",
                Instant.now().minusSeconds(60), null,
                List.of(workflowVersionId)));
    }

    @Test
    void createListUpdateValidatePublishAndResolve() {
        ProjectFulfillmentProfileDetail created = profiles.create(principal(), meta("c1"),
                new CreateProjectFulfillmentProfileCommand(
                        projectId, "HOME_CHARGING_SURVEY_INSTALL", "标准家充履约",
                        "试点", "HOME_CHARGING_SURVEY_INSTALL", null));
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.draftRevisionId()).isNotNull();
        assertThat(profiles.list(principal(), "corr-list", projectId)).hasSize(1);

        assertThatThrownBy(() -> profiles.create(principal(), meta("c-dup"),
                new CreateProjectFulfillmentProfileCommand(
                        projectId, "HOME_CHARGING_SURVEY_INSTALL", "重复", null, null, null)))
                .isInstanceOf(BusinessProblem.class);

        ProjectFulfillmentDraftView draft = profiles.getDraft(
                principal(), "corr-draft", projectId, created.profileId());
        ProjectFulfillmentDraftView updated = profiles.updateDraft(principal(), meta("u1"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft.aggregateVersion(),
                        "标准家充履约 v2", "更新说明", draft.documentJson(),
                        workflowVersionId, bundle.bundleId()));
        assertThat(updated.aggregateVersion()).isEqualTo(draft.aggregateVersion() + 1);
        assertThat(updated.workflowAssetVersionId()).isEqualTo(workflowVersionId);

        assertThatThrownBy(() -> profiles.updateDraft(principal(), meta("u-conflict"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft.aggregateVersion(),
                        "冲突", null, draft.documentJson(), workflowVersionId, bundle.bundleId())))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VERSION_CONFLICT);

        List<ProjectFulfillmentValidationIssue> issues = profiles.validate(
                principal(), meta("v1"), projectId, created.profileId());
        assertThat(issues.stream().noneMatch(i -> "ERROR".equals(i.severity()))).isTrue();

        ProjectFulfillmentProfileDetail beforePublish = profiles.get(
                principal(), "corr-get", projectId, created.profileId());
        ProjectFulfillmentRevisionView published = profiles.publish(
                principal(), meta("p1"), projectId, created.profileId(),
                beforePublish.aggregateVersion(), Instant.now().plusSeconds(5), "首发");
        assertThat(published.revisionStatus()).isEqualTo("PUBLISHED");
        assertThat(published.versionNo()).isEqualTo(1);
        assertThat(published.contentDigest()).matches("[0-9a-f]{64}");
        assertThat(published.manifestJson()).contains("HOME_CHARGING_SURVEY_INSTALL");

        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE cfg_project_fulfillment_revision
                   SET validation_json = '{"ok":true}'::jsonb
                 WHERE revision_id = :id
                """).param("id", published.revisionId()).update())
                .isInstanceOf(Exception.class);

        var resolved = resolver.resolve(new ProjectFulfillmentResolveQuery(
                TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                Instant.now().plusSeconds(10), "ADMIN_WEB", "BYD_OCEAN", "370000"));
        assertThat(resolved.revisionId()).isEqualTo(published.revisionId());
        assertThat(resolved.configurationBundleId()).isEqualTo(bundle.bundleId());
    }

    @Test
    void suspendBlocksResolveAndPublishedVersionsStayIsolated() {
        ProjectFulfillmentProfileDetail created = profiles.create(principal(), meta("c-iso"),
                new CreateProjectFulfillmentProfileCommand(
                        projectId, "HOME_CHARGING_SURVEY_INSTALL", "隔离测试",
                        null, "HOME_CHARGING_SURVEY_INSTALL", null));
        ProjectFulfillmentDraftView draft = profiles.getDraft(
                principal(), "corr-iso", projectId, created.profileId());
        profiles.updateDraft(principal(), meta("u-iso"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft.aggregateVersion(),
                        "隔离测试", null, draft.documentJson(),
                        workflowVersionId, bundle.bundleId()));
        ProjectFulfillmentProfileDetail ready = profiles.get(
                principal(), "corr-iso2", projectId, created.profileId());
        Instant t1 = Instant.now().plusSeconds(5);
        ProjectFulfillmentRevisionView v1 = profiles.publish(
                principal(), meta("p-iso1"), projectId, created.profileId(),
                ready.aggregateVersion(), t1, "v1");
        var resolvedV1 = resolver.resolve(new ProjectFulfillmentResolveQuery(
                TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                t1.plusSeconds(1), null, "BYD_OCEAN", "370000"));
        assertThat(resolvedV1.revisionId()).isEqualTo(v1.revisionId());

        ProjectFulfillmentProfileDetail afterV1 = profiles.get(
                principal(), "corr-iso3", projectId, created.profileId());
        ProjectFulfillmentDraftView draft2 = profiles.getDraft(
                principal(), "corr-iso4", projectId, created.profileId());
        profiles.updateDraft(principal(), meta("u-iso2"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft2.aggregateVersion(),
                        "隔离测试 v2", "变更说明", draft2.documentJson(),
                        workflowVersionId, bundle.bundleId()));
        ProjectFulfillmentProfileDetail ready2 = profiles.get(
                principal(), "corr-iso5", projectId, created.profileId());
        Instant t2 = Instant.now().plusSeconds(60);
        ProjectFulfillmentRevisionView v2 = profiles.publish(
                principal(), meta("p-iso2"), projectId, created.profileId(),
                ready2.aggregateVersion(), t2, "v2");
        assertThat(v2.versionNo()).isEqualTo(2);
        assertThat(resolver.resolve(new ProjectFulfillmentResolveQuery(
                TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                t1.plusSeconds(10), null, "BYD_OCEAN", "370000")).revisionId())
                .isEqualTo(v1.revisionId());
        assertThat(resolver.resolve(new ProjectFulfillmentResolveQuery(
                TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                t2.plusSeconds(1), null, "BYD_OCEAN", "370000")).revisionId())
                .isEqualTo(v2.revisionId());

        ProjectFulfillmentProfileDetail active = profiles.get(
                principal(), "corr-iso6", projectId, created.profileId());
        profiles.suspend(principal(), meta("s-iso"), projectId, created.profileId(),
                active.aggregateVersion());
        assertThatThrownBy(() -> resolver.resolve(new ProjectFulfillmentResolveQuery(
                TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                t2.plusSeconds(30), null, "BYD_OCEAN", "370000")))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.PROJECT_FULFILLMENT_PROFILE_SUSPENDED);
    }

    @Test
    void publishWithoutBundleFailsClosed() {
        ProjectFulfillmentProfileDetail created = profiles.create(principal(), meta("c2"),
                new CreateProjectFulfillmentProfileCommand(
                        projectId, "HOME_CHARGING_SURVEY_INSTALL", "无 Bundle",
                        null, "BLANK", null));
        assertThatThrownBy(() -> profiles.publish(
                principal(), meta("p-fail"), projectId, created.profileId(),
                created.aggregateVersion(), Instant.now().plusSeconds(30), null))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VALIDATION_FAILED);
    }

    private void seedGrants() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'pfp-m378', '履约配置M378', 'ACTIVE', now())
                """).param("id", roleId).param("tenant", TENANT).update();
        for (String capability : List.of(
                "project.fulfillment.read",
                "project.fulfillment.create",
                "project.fulfillment.draft.write",
                "project.fulfillment.validate",
                "project.fulfillment.publish",
                "project.fulfillment.suspend",
                "project.fulfillment.resume",
                "project.fulfillment.revision.read",
                "project.fulfillment.snapshot.read",
                "project.fulfillment.techRef.read")) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:id, :cap, now())
                    """).param("id", roleId).param("cap", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grant, :tenant, :principal, :role, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST', 'm378', now()
                )
                """)
                .param("grant", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", ACTOR)
                .param("role", roleId)
                .update();
    }

    private static CurrentPrincipal principal() {
        return new CurrentPrincipal(ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of(
                "project.fulfillment.read",
                "project.fulfillment.create",
                "project.fulfillment.draft.write",
                "project.fulfillment.validate",
                "project.fulfillment.publish",
                "project.fulfillment.suspend",
                "project.fulfillment.resume",
                "project.fulfillment.revision.read",
                "project.fulfillment.snapshot.read",
                "project.fulfillment.techRef.read"));
    }

    private static CommandMetadata meta(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }
}
