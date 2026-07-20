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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M343：REFERENCE / SAMPLE create→update→cancel 经强制 INBOUND Mapping。
 *
 * <p>不声称真实车企协议已接入（TBD_EXTERNAL_CONTRACT）。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class ReferenceOemInboundUpdateCancelPostgresIT {
    private static final String APP_KEY = "reference-oem-uc-key";
    private static final String APP_SECRET = "reference-oem-uc-secret";
    private static final String TENANT = "tenant-reference-oem-uc";
    private static final String PROJECT_CODE = "REFERENCE-OEM-UC";
    private static final String CREATE = "/api/v1/integrations/reference-oem/sample/v1/install-orders";
    private static final String UPDATE = "/api/v1/integrations/reference-oem/sample/v1/update-orders";
    private static final String CANCEL = "/api/v1/integrations/reference-oem/sample/v1/cancel-orders";
    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m343-reference-oem-uc-it");

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
                () -> "m343-reference-oem-uc-private-storage-signing-key");
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
                    int_inbound_replay_guard, int_canonical_message, int_inbound_envelope CASCADE
                """).update();
        Files.createDirectories(STORAGE_ROOT);
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'REFERENCE_OEM', 'REFERENCE OEM UC IT',
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
                {"workflowKey":"ref.oem.uc","semanticVersion":"1.0.0","startNodeId":"START",
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
                TENANT, ConfigurationAssetType.WORKFLOW, "ref.oem.uc", "1.0.0", "1.0.0",
                workflow, Sha256.digest(workflow))).versionId();
        String createIntegration = """
                {"mappingKey":"ref-uc-create","version":"1.0.0","connectorCode":"REFERENCE_OEM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER","fieldMappings":[
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
        String updateIntegration = """
                {"mappingKey":"ref-uc-update","version":"1.0.0","connectorCode":"REFERENCE_OEM","direction":"INBOUND","messageType":"UPDATE_WORK_ORDER","fieldMappings":[
                  {"mappingId":"order","externalPath":"externalOrderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                  {"mappingId":"name","externalPath":"customerName","internalPath":"customerName","required":true,"transform":"TRIM"},
                  {"mappingId":"mobile","externalPath":"customerMobile","internalPath":"customerMobile","required":true,"transform":"NONE"},
                  {"mappingId":"address","externalPath":"serviceAddress","internalPath":"serviceAddress","required":true,"transform":"TRIM"},
                  {"mappingId":"province","externalPath":"provinceCode","internalPath":"provinceCode","required":true,"transform":"NONE"},
                  {"mappingId":"city","externalPath":"cityCode","internalPath":"cityCode","required":true,"transform":"NONE"},
                  {"mappingId":"district","externalPath":"districtCode","internalPath":"districtCode","required":true,"transform":"NONE"}]}
                """.replaceAll("\\s+", "");
        String cancelIntegration = """
                {"mappingKey":"ref-uc-cancel","version":"1.0.0","connectorCode":"REFERENCE_OEM","direction":"INBOUND","messageType":"CANCEL_WORK_ORDER","fieldMappings":[
                  {"mappingId":"order","externalPath":"externalOrderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                  {"mappingId":"reason","externalPath":"reasonCode","internalPath":"reasonCode","required":true,"transform":"NONE"}]}
                """.replaceAll("\\s+", "");
        UUID createId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "ref-uc-create", "1.0.0", "1.0.0",
                createIntegration, Sha256.digest(createIntegration))).versionId();
        UUID updateId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "ref-uc-update", "1.0.0", "1.0.0",
                updateIntegration, Sha256.digest(updateIntegration))).versionId();
        UUID cancelId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "ref-uc-cancel", "1.0.0", "1.0.0",
                cancelIntegration, Sha256.digest(cancelIntegration))).versionId();
        ConfigurationBundleReference fulfillmentBundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "REFERENCE-OEM-UC-BUNDLE", "1.0.0", "REFERENCE_BRAND",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowId, createId, updateId, cancelId)));
        ProjectFulfillmentTestSupport.seedPublishedProfile(
                jdbc, TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                fulfillmentBundle, workflowId, Instant.now().minusSeconds(30));
    }

    @Test
    void createThenUpdateThenCancelThroughGenericPipelines() throws Exception {
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("externalOrderCode", "REF-OEM-UC-1");
        create.put("brandCode", "REFERENCE_BRAND");
        create.put("serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL");
        create.put("provinceCode", "370000");
        create.put("cityCode", "370100");
        create.put("districtCode", "370102");
        create.put("customerName", "原客户");
        create.put("customerMobile", "13900001111");
        create.put("serviceAddress", "原地址");
        create.put("vehicleVin", "VINREFUC000000001");
        create.put("dispatchedAt", LocalDateTime.of(2026, 7, 19, 11, 0).toString());
        postSigned(CREATE, create, "nonce-ref-uc-create", "corr-ref-uc-create")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isIn("RECEIVED", "ACTIVE");

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("externalOrderCode", "REF-OEM-UC-1");
        update.put("customerName", "新客户");
        update.put("customerMobile", "13900002222");
        update.put("serviceAddress", "新地址 9 号");
        update.put("provinceCode", "370000");
        update.put("cityCode", "370100");
        update.put("districtCode", "370102");
        postSigned(UPDATE, update, "nonce-ref-uc-update", "corr-ref-uc-update")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbc.sql("SELECT customer_name FROM wo_work_order").query(String.class).single())
                .isEqualTo("新客户");
        assertThat(jdbc.sql("SELECT service_address FROM wo_work_order").query(String.class).single())
                .isEqualTo("新地址 9 号");

        // 更新重放安全
        postSigned(UPDATE, update, "nonce-ref-uc-update", "corr-ref-uc-update-replay")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.replay").value(true));

        Map<String, Object> cancel = new LinkedHashMap<>();
        cancel.put("externalOrderCode", "REF-OEM-UC-1");
        cancel.put("reasonCode", "EXTERNAL_USER_CANCEL");
        cancel.put("cancelledAt", "2026-07-19T12:00:00");
        postSigned(CANCEL, cancel, "nonce-ref-uc-cancel", "corr-ref-uc-cancel")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isEqualTo("CANCELLED");

        postSigned(CANCEL, cancel, "nonce-ref-uc-cancel", "corr-ref-uc-cancel-replay")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.replay").value(true));
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order").query(Long.class).single())
                .isEqualTo(1L);
    }

    private org.springframework.test.web.servlet.ResultActions postSigned(
            String endpoint,
            Map<String, Object> body,
            String nonce,
            String correlationId
    ) throws Exception {
        byte[] raw = objectMapper.writeValueAsBytes(body);
        String signature = ReferenceOemSampleSignature.sign(APP_SECRET, nonce, raw);
        return mvc.perform(post(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Reference-Oem-Key", APP_KEY)
                .header("X-Reference-Oem-Nonce", nonce)
                .header("X-Reference-Oem-Signature", signature)
                .header("X-Correlation-Id", correlationId)
                .content(raw));
    }
}
