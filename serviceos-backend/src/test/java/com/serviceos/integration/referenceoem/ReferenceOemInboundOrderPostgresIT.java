package com.serviceos.integration.referenceoem;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.ProjectFulfillmentTestSupport;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.integration.referenceoem.infrastructure.ReferenceOemSampleSignature;
import com.serviceos.shared.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M272：REFERENCE / SAMPLE 第二家车企入站经通用管道建单。
 *
 * <p>本测试不声称真实车企协议已接入（TBD_EXTERNAL_CONTRACT）。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class ReferenceOemInboundOrderPostgresIT {
    private static final String APP_KEY = "reference-oem-it-key";
    private static final String APP_SECRET = "reference-oem-it-secret";
    private static final String TENANT = "tenant-reference-oem-it";
    private static final String PROJECT_CODE = "REFERENCE-OEM-IT";
    private static final String ENDPOINT = "/api/v1/integrations/reference-oem/sample/v1/install-orders";
    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m272-reference-oem-it");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("serviceos.integration.reference-oem.app-key", () -> APP_KEY);
        registry.add("serviceos.integration.reference-oem.app-secret", () -> APP_SECRET);
        registry.add("serviceos.integration.reference-oem.tenant-id", () -> TENANT);
        registry.add("serviceos.integration.reference-oem.project-code", () -> PROJECT_CODE);
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "m272-reference-oem-private-storage-signing-key");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired ConfigurationService configurations;

    @BeforeEach
    void clean() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
                    cfg_project_fulfillment_revision, cfg_project_fulfillment_profile,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    int_canonical_message, int_inbound_envelope CASCADE
                """).update();
        Files.createDirectories(STORAGE_ROOT);
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'REFERENCE_OEM', 'REFERENCE OEM IT',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT)
                .param("projectCode", PROJECT_CODE)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String workflow = """
                {"workflowKey":"ref.oem.linear","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK","nodeType":"SERVICE_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK"},
                   {"transitionId":"t2","from":"TASK","to":"END"}]}
                """.trim();
        UUID workflowId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "ref.oem.linear", "1.0.0", "1.0.0",
                workflow, Sha256.digest(workflow))).versionId();
        // M335：CREATE_WORK_ORDER 强制 INBOUND Mapping。
        String integration = """
                {"mappingKey":"ref-create","version":"1.0.0","connectorCode":"REFERENCE_OEM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER","fieldMappings":[
                  {"mappingId":"order","externalPath":"externalOrderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                  {"mappingId":"brand","externalPath":"brandCode","internalPath":"brandCode","required":true,"transform":"NONE"},
                  {"mappingId":"product","externalPath":"serviceProductCode","internalPath":"serviceProductCode","required":true,"transform":"NONE"},
                  {"mappingId":"province","externalPath":"provinceCode","internalPath":"provinceCode","required":true,"transform":"NONE"},
                  {"mappingId":"city","externalPath":"cityCode","internalPath":"cityCode","required":true,"transform":"NONE"},
                  {"mappingId":"district","externalPath":"districtCode","internalPath":"districtCode","required":true,"transform":"NONE"},
                  {"mappingId":"name","externalPath":"customerName","internalPath":"customerName","required":true,"transform":"TRIM"},
                  {"mappingId":"mobile","externalPath":"customerMobile","internalPath":"customerMobile","required":true,"transform":"NONE"},
                  {"mappingId":"address","externalPath":"serviceAddress","internalPath":"serviceAddress","required":true,"transform":"TRIM"},
                  {"mappingId":"vin","externalPath":"vehicleVin","internalPath":"vehicleVin","required":true,"transform":"NONE"},
                  {"mappingId":"dispatch","externalPath":"dispatchedAt","internalPath":"dispatchedAt","required":true,"transform":"NONE"}]}
                """.replaceAll("\\s+", "");
        UUID integrationId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "ref-create", "1.0.0", "1.0.0",
                integration, Sha256.digest(integration))).versionId();
        ConfigurationBundleReference fulfillmentBundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "REFERENCE-OEM-BUNDLE", "1.0.0", "REFERENCE_BRAND",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, java.util.List.of(workflowId, integrationId)));
        ProjectFulfillmentTestSupport.seedPublishedProfile(
                jdbc, TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                fulfillmentBundle, workflowId, Instant.now().minusSeconds(30));
    }

    @Test
    void sampleConnectorCreatesWorkOrderThroughGenericPipeline() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalOrderCode", "REF-OEM-ORDER-1");
        body.put("brandCode", "REFERENCE_BRAND");
        body.put("serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL");
        body.put("provinceCode", "370000");
        body.put("cityCode", "370100");
        body.put("districtCode", "370102");
        body.put("customerName", "样例客户");
        body.put("customerMobile", "13900000000");
        body.put("serviceAddress", "样例地址");
        body.put("vehicleVin", "VINREF00000000001");
        body.put("dispatchedAt", LocalDateTime.of(2026, 7, 18, 11, 0).toString());
        byte[] raw = objectMapper.writeValueAsBytes(body);
        String nonce = "nonce-ref-1";
        String signature = ReferenceOemSampleSignature.sign(APP_SECRET, nonce, raw);

        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reference-Oem-Key", APP_KEY)
                        .header("X-Reference-Oem-Nonce", nonce)
                        .header("X-Reference-Oem-Signature", signature)
                        .header("X-Correlation-Id", "corr-ref-oem-1")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orderCode").value("REF-OEM-ORDER-1"))
                .andExpect(jsonPath("$.adapterVersion").value("reference-oem-sample-v1"));

        assertThat(jdbc.sql("SELECT client_code FROM wo_work_order").query(String.class).single())
                .isEqualTo("REFERENCE_OEM");
        assertThat(jdbc.sql("SELECT connector_version_id FROM int_canonical_message")
                .query(String.class).single())
                .isEqualTo("reference-oem-sample-v1");

        // 同 nonce 重放
        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reference-Oem-Key", APP_KEY)
                        .header("X-Reference-Oem-Nonce", nonce)
                        .header("X-Reference-Oem-Signature", signature)
                        .header("X-Correlation-Id", "corr-ref-oem-1-replay")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.replay").value(true));
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order").query(Long.class).single())
                .isEqualTo(1L);
    }

    @Test
    void rejectsWhenFrozenBundleHasNoInboundMapping() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
                    cfg_project_fulfillment_revision, cfg_project_fulfillment_profile,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, int_inbound_replay_guard,
                    int_canonical_message, int_inbound_envelope CASCADE
                """).update();
        UUID projectId = jdbc.sql("""
                SELECT project_id FROM prj_project WHERE tenant_id = :tenant LIMIT 1
                """).param("tenant", TENANT).query(UUID.class).single();
        String workflow = """
                {"workflowKey":"ref.oem.nomap","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[{"transitionId":"t1","from":"START","to":"END"}]}
                """.trim();
        UUID workflowId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.WORKFLOW, "ref.oem.nomap", "1.0.0", "1.0.0",
                workflow, Sha256.digest(workflow))).versionId();
        ConfigurationBundleReference fulfillmentBundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "REFERENCE-OEM-NOMAP", "1.0.0", "REFERENCE_BRAND",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, java.util.List.of(workflowId)));
        ProjectFulfillmentTestSupport.seedPublishedProfile(
                jdbc, TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                fulfillmentBundle, workflowId, Instant.now().minusSeconds(30));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalOrderCode", "REF-OEM-NOMAP-1");
        body.put("brandCode", "REFERENCE_BRAND");
        body.put("serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL");
        body.put("provinceCode", "370000");
        body.put("cityCode", "370100");
        body.put("districtCode", "370102");
        body.put("customerName", "无 Mapping 客户");
        body.put("customerMobile", "13900000099");
        body.put("serviceAddress", "无 Mapping 地址");
        body.put("vehicleVin", "VINREFNOMAP0000001");
        body.put("dispatchedAt", LocalDateTime.of(2026, 7, 18, 12, 0).toString());
        byte[] raw = objectMapper.writeValueAsBytes(body);
        String nonce = "nonce-ref-nomap";
        String signature = ReferenceOemSampleSignature.sign(APP_SECRET, nonce, raw);

        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reference-Oem-Key", APP_KEY)
                        .header("X-Reference-Oem-Nonce", nonce)
                        .header("X-Reference-Oem-Signature", signature)
                        .header("X-Correlation-Id", "corr-ref-nomap")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order").query(Long.class).single())
                .isZero();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM int_canonical_message WHERE processing_status = 'COMPLETED'
                """).query(Long.class).single()).isZero();
    }
}
