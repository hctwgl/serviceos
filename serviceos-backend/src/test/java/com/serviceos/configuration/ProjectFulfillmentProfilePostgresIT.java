package com.serviceos.configuration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.CreateProjectFulfillmentProfileCommand;
import com.serviceos.configuration.api.ProjectFulfillmentCompareImpact;
import com.serviceos.configuration.api.ProjectFulfillmentDocument;
import com.serviceos.configuration.api.ProjectFulfillmentDraftView;
import com.serviceos.configuration.api.ProjectFulfillmentManifestView;
import com.serviceos.configuration.api.ProjectFulfillmentProfileDetail;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentStageDraft;
import com.serviceos.configuration.api.ProjectFulfillmentResolveQuery;
import com.serviceos.configuration.api.ProjectFulfillmentResolver;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionView;
import com.serviceos.configuration.api.ProjectFulfillmentSchemeCount;
import com.serviceos.configuration.api.ProjectFulfillmentUsageSummary;
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
import java.util.Objects;
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
    UUID fulfillmentRoleId;

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
    void usageSummaryCountsActiveWorkOrdersAndSoftOmitsWithoutWorkOrderRead() {
        Instant receivedAt = Instant.parse("2026-07-20T02:00:00Z");
        seedActiveWorkOrder(UUID.randomUUID(), receivedAt);
        seedActiveWorkOrder(UUID.randomUUID(), receivedAt.plusSeconds(60));

        // 默认角色无 workOrder.read → soft-omit，不得伪装为 0
        ProjectFulfillmentUsageSummary omitted = profiles.usageSummary(
                principal(), "corr-usage-omit", projectId);
        assertThat(omitted.projectId()).isEqualTo(projectId);
        assertThat(omitted.activeWorkOrderCount()).isNull();
        assertThat(omitted.activeWorkOrderCountTruncated()).isNull();

        grantCapability("workOrder.read");
        CurrentPrincipal withWorkOrderRead = new CurrentPrincipal(
                ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of(
                "project.fulfillment.read",
                "workOrder.read"));
        ProjectFulfillmentUsageSummary counted = profiles.usageSummary(
                withWorkOrderRead, "corr-usage-count", projectId);
        assertThat(counted.activeWorkOrderCount()).isEqualTo(2);
        assertThat(counted.activeWorkOrderCountTruncated()).isFalse();
    }

    @Test
    void summarizeSchemeCountsSoftGateAndZeros() {
        ProjectFulfillmentProfileDetail created = profiles.create(principal(), meta("sum-c1"),
                new CreateProjectFulfillmentProfileCommand(
                        projectId, "HOME_CHARGING_SURVEY_INSTALL", "方案计数",
                        "试点", "HOME_CHARGING_SURVEY_INSTALL", null));
        assertThat(created.draftRevisionId()).isNotNull();

        List<ProjectFulfillmentSchemeCount> allowed = profiles.summarizeSchemeCounts(
                principal(), "corr-sum", List.of(projectId));
        assertThat(allowed).hasSize(1);
        assertThat(allowed.getFirst().projectId()).isEqualTo(projectId);
        assertThat(allowed.getFirst().publishedSchemeCount()).isZero();
        assertThat(allowed.getFirst().draftSchemeCount()).isEqualTo(1);

        CurrentPrincipal unauthorized = new CurrentPrincipal(
                "pfp-sum-no-grant", TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
        assertThat(profiles.summarizeSchemeCounts(unauthorized, "corr-sum-deny", List.of(projectId)))
                .isEmpty();
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
        assertThat(draft.document()).isNotNull();
        assertThat(draft.document().stages()).isNotEmpty();
        ProjectFulfillmentDraftView updated = profiles.updateDraft(principal(), meta("u1"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft.aggregateVersion(),
                        "标准家充履约 v2", "更新说明", draft.document(),
                        workflowVersionId, bundle.bundleId()));
        assertThat(updated.aggregateVersion()).isEqualTo(draft.aggregateVersion() + 1);
        assertThat(updated.workflowAssetVersionId()).isEqualTo(workflowVersionId);
        assertThat(updated.document().stages()).hasSize(draft.document().stages().size());

        assertThatThrownBy(() -> profiles.updateDraft(principal(), meta("u-conflict"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), draft.aggregateVersion(),
                        "冲突", null, draft.document(), workflowVersionId, bundle.bundleId())))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.VERSION_CONFLICT);

        List<ProjectFulfillmentValidationIssue> issues = profiles.validate(
                principal(), meta("v1"), projectId, created.profileId());
        assertThat(issues.stream().noneMatch(i -> "ERROR".equals(i.severity()))).isTrue();

        ProjectFulfillmentManifestView preview = profiles.compilePreview(
                principal(), meta("preview-1"), projectId, created.profileId());
        assertThat(preview.runbook()).isNotNull();
        assertThat(preview.runbook().serviceProductLabel()).isEqualTo("家充勘测安装");
        assertThat(preview.runbook().stageCount()).isGreaterThanOrEqualTo(1);
        assertThat(preview.runbook().stages().getFirst().ownerTypeLabel()).isNotBlank();
        assertThat(preview.manifestJson()).contains("stages");

        ProjectFulfillmentCompareImpact beforeBaseline = profiles.compareImpact(
                principal(), "corr-compare-none", projectId, created.profileId());
        assertThat(beforeBaseline.baselineKind()).isEqualTo("NONE");
        assertThat(beforeBaseline.impact().existingWorkOrdersScope()).contains("冻结");

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

        ProjectFulfillmentDraftView afterPublishDraft = profiles.getDraft(
                principal(), "corr-draft-2", projectId, created.profileId());
        ProjectFulfillmentDocument mutated = renameStage(
                afterPublishDraft.document(), "现场勘测", "现场勘测修订版");
        assertThat(mutated).isNotEqualTo(afterPublishDraft.document());
        profiles.updateDraft(principal(), meta("u-diff"),
                new UpdateProjectFulfillmentDraftCommand(
                        created.profileId(), afterPublishDraft.aggregateVersion(),
                        "标准家充履约 v2", "差异测试", mutated,
                        workflowVersionId, bundle.bundleId()));
        ProjectFulfillmentCompareImpact withBaseline = profiles.compareImpact(
                principal(), "corr-compare-pub", projectId, created.profileId());
        assertThat(withBaseline.baselineKind()).isEqualTo("PUBLISHED");
        assertThat(withBaseline.baselineVersionLabel()).isEqualTo("v1");
        assertThat(withBaseline.changeCount()).isGreaterThanOrEqualTo(1);
        assertThat(withBaseline.changes()).anyMatch(change ->
                change.summary().contains("现场勘测") || "STAGE".equals(change.category()));
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
                        "隔离测试", null, draft.document(),
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
                        "隔离测试 v2", "变更说明", draft2.document(),
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

    @Test
    void compareImpactRejectsCrossProjectAndUnauthorizedPrincipal() {
        ProjectFulfillmentProfileDetail created = profiles.create(principal(), meta("c-cmp-auth"),
                new CreateProjectFulfillmentProfileCommand(
                        projectId, "HOME_CHARGING_SURVEY_INSTALL", "鉴权测试",
                        null, "HOME_CHARGING_SURVEY_INSTALL", null));

        UUID foreignProjectId = UUID.randomUUID();
        assertThatThrownBy(() -> profiles.compareImpact(
                principal(), "corr-cross-project", foreignProjectId, created.profileId()))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.RESOURCE_NOT_FOUND);

        CurrentPrincipal unauthorized = new CurrentPrincipal(
                "pfp-no-grant", TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
        assertThatThrownBy(() -> profiles.compareImpact(
                unauthorized, "corr-no-grant", projectId, created.profileId()))
                .isInstanceOf(BusinessProblem.class)
                .extracting(ex -> ((BusinessProblem) ex).code())
                .isEqualTo(ProblemCode.ACCESS_DENIED);
    }

    private void seedGrants() {
        fulfillmentRoleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:id, :tenant, 'pfp-m378', '履约配置M378', 'ACTIVE', now())
                """).param("id", fulfillmentRoleId).param("tenant", TENANT).update();
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
                    """).param("id", fulfillmentRoleId).param("cap", capability).update();
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
                .param("role", fulfillmentRoleId)
                .update();
    }

    private void grantCapability(String capability) {
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:id, :cap, now())
                ON CONFLICT DO NOTHING
                """)
                .param("id", fulfillmentRoleId)
                .param("cap", capability)
                .update();
    }

    private void seedActiveWorkOrder(UUID workOrderId, Instant receivedAt) {
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status,
                    configuration_bundle_id, configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, activated_at, version
                ) VALUES (
                    :id, :tenantId, :projectId, 'BYD', 'BYD_OCEAN', 'HOME_CHARGING_SURVEY_INSTALL',
                    :externalOrderCode, :payloadDigest, 'ACTIVE',
                    :bundleId, 'PFP-BUNDLE', '1.0.0', :bundleDigest,
                    '370000', '370100', '370102',
                    '测试客户', '13800000000', '测试地址', 'VINPFP000000000001',
                    :receivedAt, :receivedAt, :receivedAt, 1)
                """)
                .param("id", workOrderId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("externalOrderCode", "PFP-" + workOrderId)
                .param("payloadDigest", Sha256.digest(workOrderId.toString()))
                .param("bundleId", bundle.bundleId())
                .param("bundleDigest", bundle.manifestDigest())
                .param("receivedAt", java.sql.Timestamp.from(receivedAt))
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

    private static ProjectFulfillmentDocument renameStage(
            ProjectFulfillmentDocument source, String fromName, String toName
    ) {
        return new ProjectFulfillmentDocument(
                source.schemaVersion(),
                source.orderTypeName(),
                source.supportedClientKinds(),
                source.stages().stream()
                        .map(stage -> Objects.equals(stage.stageName(), fromName)
                                ? new ProjectFulfillmentStageDraft(
                                        stage.stageCode(),
                                        toName,
                                        stage.sequence(),
                                        stage.stageType(),
                                        stage.taskType(),
                                        stage.ownerType(),
                                        stage.description(),
                                        stage.formRefs(),
                                        stage.evidenceRefs(),
                                        stage.actions(),
                                        stage.transitions(),
                                        stage.exceptionPaths(),
                                        stage.slaRef(),
                                        stage.terminal())
                                : stage)
                        .toList());
    }
}
