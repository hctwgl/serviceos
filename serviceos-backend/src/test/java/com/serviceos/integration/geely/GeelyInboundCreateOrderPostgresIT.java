package com.serviceos.integration.geely;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.ProjectFulfillmentTestSupport;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.integration.geely.infrastructure.GeelyAesCipher;
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
 * M311：吉利浩瀚本地 AES 入站 → 通用建单管道。
 *
 * <p>使用协议文档示例 AK/IV；不声称 Sandbox/开放平台签名已接入（BLOCKED_EXTERNAL）。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class GeelyInboundCreateOrderPostgresIT {
    private static final String ACCESS_KEY = "GENERAL_KEY_DEMO";
    private static final String AES_IV = "IV_DEMO_90123456";
    private static final String TENANT = "tenant-geely-it";
    private static final String PROJECT_CODE = "GEELY-IT";
    private static final String ENDPOINT = "/api/v1/integrations/geely/haohan/v1.3/notify_create_order";
    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m311-geely-it");

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
        registry.add("serviceos.integration.geely.access-key", () -> ACCESS_KEY);
        registry.add("serviceos.integration.geely.aes-iv", () -> AES_IV);
        registry.add("serviceos.integration.geely.tenant-id", () -> TENANT);
        registry.add("serviceos.integration.geely.project-code", () -> PROJECT_CODE);
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "m311-geely-private-storage-signing-key");
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
                    :projectId, :tenantId, :projectCode, 'GEELY', 'GEELY IT',
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
                {"workflowKey":"geely.linear","semanticVersion":"1.0.0","startNodeId":"START",
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
                TENANT, ConfigurationAssetType.WORKFLOW, "geely.linear", "1.0.0", "1.0.0",
                workflow, Sha256.digest(workflow))).versionId();
        // M335：CREATE_WORK_ORDER 强制 INBOUND Mapping。
        String integration = """
                {"mappingKey":"geely-create","version":"1.0.0","connectorCode":"GEELY","direction":"INBOUND","messageType":"CREATE_WORK_ORDER","fieldMappings":[
                  {"mappingId":"order","externalPath":"installProcessNo","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                  {"mappingId":"brand","internalPath":"brandCode","required":true,"constantValue":"GEELY","transform":"NONE"},
                  {"mappingId":"product","internalPath":"serviceProductCode","required":true,"constantValue":"HOME_CHARGING_SURVEY_INSTALL","transform":"NONE"},
                  {"mappingId":"province","externalPath":"province","internalPath":"provinceCode","required":true,"transform":"NONE"},
                  {"mappingId":"city","externalPath":"city","internalPath":"cityCode","required":true,"transform":"NONE"},
                  {"mappingId":"district","externalPath":"district","internalPath":"districtCode","required":true,"transform":"NONE"},
                  {"mappingId":"name","externalPath":"contactName","internalPath":"customerName","required":true,"transform":"TRIM"},
                  {"mappingId":"mobile","externalPath":"contactPhone","internalPath":"customerMobile","required":true,"transform":"NONE"},
                  {"mappingId":"address","externalPath":"address","internalPath":"serviceAddress","required":true,"transform":"TRIM"},
                  {"mappingId":"vin","externalPath":"vin","internalPath":"vehicleVin","required":true,"defaultValue":"GINGEELY0000000001","transform":"UPPER"},
                  {"mappingId":"dispatch","externalPath":"assignProviderTime","internalPath":"dispatchedAt","required":true,"transform":"DATE_ISO"}]}
                """.replaceAll("\\s+", "");
        UUID integrationId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT, ConfigurationAssetType.INTEGRATION, "geely-create", "1.0.0", "1.0.0",
                integration, Sha256.digest(integration))).versionId();
        ConfigurationBundleReference fulfillmentBundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "GEELY-BUNDLE", "1.0.0", "GEELY",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowId, integrationId)));
        ProjectFulfillmentTestSupport.seedPublishedProfile(
                jdbc, TENANT, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                fulfillmentBundle, workflowId, Instant.now().minusSeconds(30));
    }

    @Test
    void decryptsAndCreatesWorkOrderThroughGenericPipeline() throws Exception {
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("installProcessNo", "IN2026071900001");
        plain.put("workNo", "HW2026071900001");
        plain.put("assignProviderTime", "2026-07-19 10:00:00");
        plain.put("applyTime", "2026-07-19 09:00:00");
        plain.put("status", 1);
        plain.put("contactName", "吉利联系人");
        plain.put("contactPhone", "13800001111");
        plain.put("province", "370000");
        plain.put("city", "370100");
        plain.put("district", "370102");
        plain.put("address", "济南示例地址");
        plain.put("carBrand", "GEELY");
        plain.put("vin", "VINGEELY000000001");
        plain.put("carryProduct", 1);
        plain.put("licensePhotoSkip", 0);
        plain.put("productList", List.of(Map.of(
                "productName", "7kW", "productPower", 7.0, "packageInfo", "PKG")));
        String plainJson = objectMapper.writeValueAsString(plain);
        String cipher = GeelyAesCipher.encryptToBase64(plainJson, ACCESS_KEY, AES_IV);
        Map<String, Object> envelope = Map.of(
                "providerNo", "SEA",
                "timestamp", "20260719100000",
                "data", cipher);
        byte[] raw = objectMapper.writeValueAsBytes(envelope);

        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-geely-1")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));

        assertThat(jdbc.sql("SELECT client_code FROM wo_work_order").query(String.class).single())
                .isEqualTo("GEELY");
        assertThat(jdbc.sql("SELECT external_order_code FROM wo_work_order")
                .query(String.class).single())
                .isEqualTo("IN2026071900001");
        assertThat(jdbc.sql("SELECT connector_version_id FROM int_canonical_message")
                .query(String.class).single())
                .isEqualTo("geely-haohan-v1.3-local");

        mvc.perform(post(ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-geely-1-replay")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order").query(Long.class).single())
                .isEqualTo(1L);
    }
}
