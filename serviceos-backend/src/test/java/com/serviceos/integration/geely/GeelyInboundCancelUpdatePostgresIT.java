package com.serviceos.integration.geely;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
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
 * M314：吉利本地 AES 更新/取消入站 → 通用管道。
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class GeelyInboundCancelUpdatePostgresIT {
    private static final String ACCESS_KEY = "GENERAL_KEY_DEMO";
    private static final String AES_IV = "IV_DEMO_90123456";
    private static final String TENANT = "tenant-geely-cu-it";
    private static final String PROJECT_CODE = "GEELY-CU-IT";
    private static final String CREATE = "/api/v1/integrations/geely/haohan/v1.3/notify_create_order";
    private static final String UPDATE = "/api/v1/integrations/geely/haohan/v1.3/notify_update_order_info";
    private static final String CLOSE = "/api/v1/integrations/geely/haohan/v1.3/notify_close_order";
    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m314-geely-cu-it");

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
                () -> "m314-geely-private-storage-signing-key");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired ConfigurationService configurations;

    @BeforeEach
    void clean() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
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
                    :projectId, :tenantId, :projectCode, 'GEELY', 'GEELY CU IT',
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
                {"workflowKey":"geely.cu.linear","semanticVersion":"1.0.0","startNodeId":"START",
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
                TENANT, ConfigurationAssetType.WORKFLOW, "geely.cu.linear", "1.0.0", "1.0.0",
                workflow, Sha256.digest(workflow))).versionId();
        // M335：CREATE_WORK_ORDER 强制 INBOUND Mapping。
        String integration = """
                {"mappingKey":"geely-cu-create","version":"1.0.0","connectorCode":"GEELY","direction":"INBOUND","fieldMappings":[
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
                TENANT, ConfigurationAssetType.INTEGRATION, "geely-cu-create", "1.0.0", "1.0.0",
                integration, Sha256.digest(integration))).versionId();
        configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT, projectId, "GEELY-CU-BUNDLE", "1.0.0", "GEELY",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowId, integrationId)));
    }

    @Test
    void createThenUpdateThenCloseThroughGenericPipelines() throws Exception {
        Map<String, Object> create = new LinkedHashMap<>();
        create.put("installProcessNo", "IN2026071900901");
        create.put("workNo", "HW2026071900901");
        create.put("assignProviderTime", "2026-07-19 10:00:00");
        create.put("applyTime", "2026-07-19 09:00:00");
        create.put("status", 1);
        create.put("contactName", "原联系人");
        create.put("contactPhone", "13800002222");
        create.put("province", "370000");
        create.put("city", "370100");
        create.put("district", "370102");
        create.put("address", "原地址");
        create.put("carBrand", "GEELY");
        create.put("carryProduct", 1);
        create.put("licensePhotoSkip", 0);
        create.put("productList", List.of(Map.of(
                "productName", "7kW", "productPower", 7.0, "packageInfo", "PKG")));
        postEncrypted(CREATE, create, "corr-geely-cu-create");

        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isIn("RECEIVED", "ACTIVE");

        postEncrypted(UPDATE, Map.of(
                "installProcessNo", "IN2026071900901",
                "contactName", "新联系人",
                "contactPhone", "13900003333",
                "province", "370000",
                "city", "370100",
                "district", "370102",
                "address", "新地址大道 9 号"),
                "corr-geely-cu-update");

        assertThat(jdbc.sql("SELECT customer_name FROM wo_work_order").query(String.class).single())
                .isEqualTo("新联系人");
        assertThat(jdbc.sql("SELECT service_address FROM wo_work_order").query(String.class).single())
                .isEqualTo("新地址大道 9 号");

        postEncrypted(CLOSE, Map.of(
                "installProcessNo", "IN2026071900901",
                "closeReasonCode", "USER_CANCEL"),
                "corr-geely-cu-close");

        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isEqualTo("CANCELLED");
    }

    private void postEncrypted(String endpoint, Map<String, Object> plain, String correlationId)
            throws Exception {
        String plainJson = objectMapper.writeValueAsString(new LinkedHashMap<>(plain));
        String cipher = GeelyAesCipher.encryptToBase64(plainJson, ACCESS_KEY, AES_IV);
        byte[] raw = objectMapper.writeValueAsBytes(Map.of(
                "providerNo", "SEA",
                "timestamp", "20260719120000",
                "data", cipher));
        var result = mvc.perform(post(endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", correlationId)
                        .content(raw))
                .andReturn();
        if (result.getResponse().getStatus() != 200) {
            throw new AssertionError("status=" + result.getResponse().getStatus()
                    + " body=" + result.getResponse().getContentAsString()
                    + " resolved=" + (result.getResolvedException() == null ? null
                    : result.getResolvedException().toString()));
        }
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString()).contains("\"code\":\"0\"");
    }
}
