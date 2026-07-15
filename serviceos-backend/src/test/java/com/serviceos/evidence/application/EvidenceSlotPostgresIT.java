package com.serviceos.evidence.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionEvaluationException;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.evidence.api.EvidenceSlotQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxMessageHandler;
import com.serviceos.shared.Sha256;
import com.serviceos.task.api.CreateWorkflowTaskCommand;
import com.serviceos.task.api.TaskSchedulingService;
import com.serviceos.task.api.WorkflowTaskKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class)
class EvidenceSlotPostgresIT {
    private static final String TENANT = "tenant-evidence-slot-it";

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
    }

    @Autowired ConfigurationService configurations;
    @Autowired TaskSchedulingService tasks;
    @Autowired EvidenceSlotQueryService slotQueries;
    @Autowired List<OutboxMessageHandler> handlers;
    @Autowired JdbcClient jdbc;

    UUID projectId;

    @BeforeEach
    void setUp() {
        jdbc.sql("""
                TRUNCATE TABLE evd_evidence_slot, evd_task_evidence_resolution,
                    rel_inbox_record, rel_outbox_event, aud_audit_record,
                    auth_role_grant, auth_role_capability, auth_role,
                    tsk_task, wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project CASCADE
                """).update();
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, 'EVD-SLOT-IT', 'BYD', '资料槽位测试项目',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId).param("tenantId", TENANT)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now()).update();
    }

    @Test
    void resolvesFixedSlotsFromFrozenStageAndReplaysWithoutDuplicates() {
        UUID survey = publishEvidence("survey.site", fixedSurveyDefinition());
        UUID installation = publishEvidence("installation.done", """
                {"templateKey":"installation.done","version":"1.0.0","stage":"INSTALLATION",
                 "items":[{"evidenceKey":"charger.photo","name":"安装照片","mediaType":"PHOTO",
                   "required":true,"capture":{"minCount":1,"maxCount":5}}]}
                """);
        ConfigurationBundleReference bundle = publishBundle(List.of(survey, installation));
        UUID taskId = createTask(bundle, "SURVEY");
        OutboxMessage created = taskCreated(taskId);

        handler().handle(created);
        handler().handle(created);
        seedEvidenceReadGrant();

        assertThat(jdbc.sql("SELECT stage_code FROM tsk_task WHERE task_id = :taskId")
                .param("taskId", taskId).query(String.class).single()).isEqualTo("SURVEY");
        assertThat(jdbc.sql("""
                SELECT requirement_code || ':' || required_flag || ':' || min_count || ':'
                       || COALESCE(max_count::text, 'null') || ':' || status_projection
                  FROM evd_evidence_slot WHERE tenant_id = :tenantId AND task_id = :taskId
                 ORDER BY requirement_code
                """).param("tenantId", TENANT).param("taskId", taskId)
                .query(String.class).list())
                .containsExactly(
                        "site.note:false:0:null:SATISFIED",
                        "site.photo:true:1:3:MISSING");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_task_evidence_resolution")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_inbox_record WHERE status = 'SUCCEEDED'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record WHERE action_name = 'EVIDENCE_SLOTS_RESOLVED'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'evidence.slots-resolved'")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(slotQueries.listForTask(principal(), "corr-evidence-read", taskId))
                .extracting(slot -> slot.requirementCode() + ":" + slot.status())
                .containsExactly("site.note:SATISFIED", "site.photo:MISSING");

        UUID slotId = jdbc.sql("SELECT slot_id FROM evd_evidence_slot WHERE required_flag")
                .query(UUID.class).single();
        assertThatThrownBy(() -> jdbc.sql("""
                UPDATE evd_evidence_slot SET min_count = 2 WHERE slot_id = :slotId
                """).param("slotId", slotId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("evidence slot definition is immutable");
    }

    @Test
    void conditionalRequirementCreatesSlotWhenBrandMatches() {
        UUID conditional = publishEvidence("survey.conditional", """
                {"templateKey":"survey.conditional","version":"1.0.0","stage":"SURVEY",
                 "items":[{"evidenceKey":"pole.photo","name":"立柱照片","mediaType":"PHOTO",
                   "required":false,"requiredWhen":{"language":"SERVICEOS_EXPR_V1",
                     "source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}]}
                """);
        ConfigurationBundleReference bundle = publishBundle(List.of(conditional));
        UUID taskId = createTask(bundle, "SURVEY", "BYD_OCEAN");

        handler().handle(taskCreated(taskId));

        assertThat(jdbc.sql("""
                SELECT requirement_code || ':' || required_flag || ':' || min_count
                  FROM evd_evidence_slot WHERE tenant_id = :tenantId AND task_id = :taskId
                """).param("tenantId", TENANT).param("taskId", taskId)
                .query(String.class).list())
                .containsExactly("pole.photo:true:1");
        assertThat(jdbc.sql("""
                SELECT resolver_version FROM evd_task_evidence_resolution WHERE task_id = :taskId
                """).param("taskId", taskId).query(String.class).single())
                .isEqualTo("CONDITIONAL_EVIDENCE_V1");
    }

    @Test
    void conditionalRequirementOmitsSlotWhenBrandDoesNotMatch() {
        UUID conditional = publishEvidence("survey.conditional", """
                {"templateKey":"survey.conditional","version":"1.0.0","stage":"SURVEY",
                 "items":[{"evidenceKey":"pole.photo","name":"立柱照片","mediaType":"PHOTO",
                   "required":false,"requiredWhen":{"language":"SERVICEOS_EXPR_V1",
                     "source":"workOrder.brandCode == \\"BYD_OCEAN\\""}}]}
                """);
        ConfigurationBundleReference bundle = publishBundle(List.of(conditional));
        UUID taskId = createTask(bundle, "SURVEY", "OTHER_BRAND");

        handler().handle(taskCreated(taskId));

        assertThat(jdbc.sql("SELECT count(*) FROM evd_evidence_slot WHERE task_id = :taskId")
                .param("taskId", taskId).query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT slot_count FROM evd_task_evidence_resolution WHERE task_id = :taskId")
                .param("taskId", taskId).query(Integer.class).single()).isZero();
        // false 条件不会生成槽位，因此必须在解析级不可变事实中保留输入摘要与决策解释。
        assertThat(jdbc.sql("""
                SELECT condition_input_digest || ':' || (resolution_explanation #>> '{conditions,0,result}')
                  FROM evd_task_evidence_resolution WHERE task_id = :taskId
                """).param("taskId", taskId).query(String.class).single())
                .matches("[0-9a-f]{64}:false");
    }

    @Test
    void missingAuthoritativeConditionalContextFailsClosedAndRollsBackEveryConsumerFact() {
        UUID conditional = publishEvidence("survey.conditional", """
                {"templateKey":"survey.conditional","version":"1.0.0","stage":"SURVEY",
                 "items":[{"evidenceKey":"pole.photo","name":"立柱照片","mediaType":"PHOTO",
                   "required":false,"requiredWhen":{"language":"SERVICEOS_EXPR_V1",
                     "source":"region.districtCode == \\\"370102\\\""}}]}
                """);
        ConfigurationBundleReference bundle = publishBundle(List.of(conditional));
        UUID taskId = createTask(bundle, "SURVEY", "BYD_OCEAN", "");

        assertThatThrownBy(() -> handler().handle(taskCreated(taskId)))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("缺少权威值");
        assertThat(jdbc.sql("SELECT count(*) FROM evd_task_evidence_resolution")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM evd_evidence_slot")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_inbox_record")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM rel_outbox_event WHERE event_type = 'evidence.slots-resolved'")
                .query(Long.class).single()).isZero();
    }

    @Test
    void noMatchingStagePersistsAuthoritativeEmptyResolution() {
        UUID installation = publishEvidence("installation.done", """
                {"templateKey":"installation.done","version":"1.0.0","stage":"INSTALLATION",
                 "items":[{"evidenceKey":"charger.photo","name":"安装照片","mediaType":"PHOTO",
                   "required":true}]}
                """);
        ConfigurationBundleReference bundle = publishBundle(List.of(installation));
        UUID taskId = createTask(bundle, "SURVEY");

        handler().handle(taskCreated(taskId));

        assertThat(jdbc.sql("SELECT slot_count FROM evd_task_evidence_resolution WHERE task_id = :taskId")
                .param("taskId", taskId).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM evd_evidence_slot")
                .query(Long.class).single()).isZero();
    }

    private UUID publishEvidence(String key, String definition) {
        String normalized = definition.trim();
        return configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.EVIDENCE, key, "1.0.0", "1.0.0",
                normalized, Sha256.digest(normalized))).versionId();
    }

    private ConfigurationBundleReference publishBundle(List<UUID> assets) {
        return configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "EVD-SLOT-BUNDLE-" + UUID.randomUUID(), "1.0.0",
                "BYD_OCEAN", "HOME_CHARGING", "370000", Instant.now().minusSeconds(60),
                null, assets));
    }

    private UUID createTask(ConfigurationBundleReference bundle, String stageCode) {
        return createTask(bundle, stageCode, "BYD_OCEAN");
    }

    private UUID createTask(ConfigurationBundleReference bundle, String stageCode, String brandCode) {
        return createTask(bundle, stageCode, brandCode, "370102");
    }

    private UUID createTask(
            ConfigurationBundleReference bundle, String stageCode, String brandCode, String districtCode
    ) {
        UUID workOrderId = seedWorkOrder(bundle, brandCode, districtCode);
        return tasks.createWorkflowTask(new CreateWorkflowTaskCommand(
                TENANT, projectId, workOrderId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SITE_SURVEY", UUID.randomUUID(), "a".repeat(64),
                bundle.bundleId(), bundle.manifestDigest(), stageCode,
                "SITE_SURVEY", WorkflowTaskKind.HUMAN, null, "work-order:evidence-slot",
                "b".repeat(64), 100, Instant.now(), 1,
                "corr-evidence-slot", "cause-evidence-slot")).taskId();
    }

    private UUID seedWorkOrder(
            ConfigurationBundleReference bundle, String brandCode, String districtCode
    ) {
        UUID workOrderId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO wo_work_order (
                    id, tenant_id, project_id, client_code, brand_code, service_product_code,
                    external_order_code, payload_digest, status,
                    configuration_bundle_id, configuration_bundle_code, configuration_bundle_version,
                    configuration_bundle_digest, province_code, city_code, district_code,
                    customer_name, customer_mobile, service_address, vehicle_vin,
                    external_dispatched_at, received_at, activated_at, version
                ) VALUES (
                    :id, :tenantId, :projectId, 'BYD', :brandCode, 'HOME_CHARGING',
                    :externalOrderCode, :payloadDigest, 'ACTIVE',
                    :bundleId, :bundleCode, :bundleVersion, :bundleDigest,
                    '370000', '370100', :districtCode,
                    '测试客户', '13800000000', '测试地址', 'VIN123456789012345',
                    now(), now(), now(), 1
                )
                """)
                .param("id", workOrderId)
                .param("tenantId", TENANT)
                .param("projectId", projectId)
                .param("brandCode", brandCode)
                .param("districtCode", districtCode)
                .param("externalOrderCode", "EVD-SLOT-" + workOrderId)
                .param("payloadDigest", "c".repeat(64))
                .param("bundleId", bundle.bundleId())
                .param("bundleCode", bundle.bundleCode())
                .param("bundleVersion", bundle.bundleVersion())
                .param("bundleDigest", bundle.manifestDigest())
                .update();
        return workOrderId;
    }

    private OutboxMessageHandler handler() {
        return handlers.stream().filter(handler -> handler.supports("task.created", 1))
                .findFirst().orElseThrow();
    }

    private OutboxMessage taskCreated(UUID taskId) {
        Map<String, Object> row = jdbc.sql("""
                SELECT outbox_id, event_id, module_name, event_type, schema_version,
                       aggregate_type, aggregate_id, aggregate_version, tenant_id,
                       correlation_id, causation_id, partition_key, payload::text AS payload,
                       payload_digest, occurred_at
                  FROM rel_outbox_event
                 WHERE event_type = 'task.created' AND aggregate_id = :taskId
                """).param("taskId", taskId.toString()).query().singleRow();
        return new OutboxMessage(
                uuid(row, "outbox_id"), uuid(row, "event_id"), text(row, "module_name"),
                text(row, "event_type"), number(row, "schema_version").intValue(),
                text(row, "aggregate_type"), text(row, "aggregate_id"),
                number(row, "aggregate_version").longValue(), text(row, "tenant_id"),
                text(row, "correlation_id"), text(row, "causation_id"), text(row, "partition_key"),
                text(row, "payload"), text(row, "payload_digest"),
                instant(row, "occurred_at"), 1);
    }

    private static String fixedSurveyDefinition() {
        return """
                {"templateKey":"survey.site","version":"1.0.0","stage":"SURVEY",
                 "items":[
                   {"evidenceKey":"site.photo","name":"现场照片","mediaType":"PHOTO","required":true,
                    "capture":{"minCount":1,"maxCount":3,"requireGps":true}},
                   {"evidenceKey":"site.note","name":"补充资料","mediaType":"DOCUMENT","required":false}
                 ]}
                """;
    }

    private void seedEvidenceReadGrant() {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenantId, 'evidence-reader', '资料读取人', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenantId", TENANT).update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, 'evidence.read', now())
                """).param("roleId", roleId).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at)
                VALUES (:grantId, :tenantId, 'evidence-reader', :roleId, 'PROJECT', :projectId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'M37-EVIDENCE-SLOT', now())
                """).param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("roleId", roleId).param("projectId", projectId.toString()).update();
    }

    private CurrentPrincipal principal() {
        return new CurrentPrincipal(
                "evidence-reader", TENANT, CurrentPrincipal.PrincipalType.USER,
                "evidence-it", Set.of());
    }

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static Number number(Map<String, Object> row, String key) {
        return (Number) row.get(key);
    }

    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("不支持的数据库时间类型: " + value.getClass().getName());
    }
}
