package com.serviceos.integration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.ProjectFulfillmentTestSupport;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M273：BYD 与 REFERENCE_OEM 使用独立 Connector / Bundle 并行建单回归。
 *
 * <p>REFERENCE_OEM 侧为 SAMPLE；不覆盖真实第二家协议（BLOCKED_EXTERNAL）。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class DualOemInboundRegressionPostgresIT {
    private static final String BYD_KEY = "dual-byd-key";
    private static final String BYD_SECRET = "dual-byd-secret";
    private static final String BYD_TENANT = "tenant-dual-byd";
    private static final String BYD_PROJECT = "BYD-DUAL-IT";
    private static final String REF_KEY = "dual-ref-key";
    private static final String REF_SECRET = "dual-ref-secret";
    private static final String REF_TENANT = "tenant-dual-ref";
    private static final String REF_PROJECT = "REF-DUAL-IT";
    private static final Path STORAGE = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m273-dual-oem-it");

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
        registry.add("serviceos.integration.byd.cpim.app-key", () -> BYD_KEY);
        registry.add("serviceos.integration.byd.cpim.app-secret", () -> BYD_SECRET);
        registry.add("serviceos.integration.byd.cpim.zone-id", () -> "Asia/Shanghai");
        registry.add("serviceos.integration.byd.cpim.tenant-id", () -> BYD_TENANT);
        registry.add("serviceos.integration.byd.cpim.project-code", () -> BYD_PROJECT);
        registry.add("serviceos.integration.reference-oem.app-key", () -> REF_KEY);
        registry.add("serviceos.integration.reference-oem.app-secret", () -> REF_SECRET);
        registry.add("serviceos.integration.reference-oem.tenant-id", () -> REF_TENANT);
        registry.add("serviceos.integration.reference-oem.project-code", () -> REF_PROJECT);
        registry.add("serviceos.files.local.root", STORAGE::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "local-development-key-change-before-production-32bytes");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired ConfigurationService configurations;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
                    cfg_project_fulfillment_revision, cfg_project_fulfillment_profile,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project, int_inbound_replay_guard,
                    int_canonical_message, int_inbound_envelope CASCADE
                """).update();
        Files.createDirectories(STORAGE);
        seed(BYD_TENANT, BYD_PROJECT, "BYD", "BYD_OCEAN", "byd.dual.linear");
        seed(REF_TENANT, REF_PROJECT, "REFERENCE_OEM", "REFERENCE_BRAND", "ref.dual.linear");
    }

    @Test
    void bydAndReferenceOemUseIndependentConnectorsAndBundles() throws Exception {
        postByd("DUAL-BYD-1");
        postReference("DUAL-REF-1");

        List<String> clients = jdbc.sql(
                        "SELECT client_code FROM wo_work_order ORDER BY client_code")
                .query(String.class).list();
        assertThat(clients).containsExactly("BYD", "REFERENCE_OEM");

        List<String> connectors = jdbc.sql("""
                        SELECT DISTINCT connector_version_id FROM int_canonical_message
                         ORDER BY connector_version_id
                        """).query(String.class).list();
        assertThat(connectors).containsExactly("byd-cpim-v7.3.1", "reference-oem-sample-v1");

        // 冲突：同 REFERENCE 业务键不同正文
        Map<String, Object> conflict = referenceBody("DUAL-REF-1");
        conflict.put("customerName", "冲突客户");
        byte[] raw = objectMapper.writeValueAsBytes(conflict);
        String nonce = "ref-conflict-nonce";
        mvc.perform(post("/api/v1/integrations/reference-oem/sample/v1/install-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reference-Oem-Key", REF_KEY)
                        .header("X-Reference-Oem-Nonce", nonce)
                        .header("X-Reference-Oem-Signature",
                                ReferenceOemSampleSignature.sign(REF_SECRET, nonce, raw))
                        .header("X-Correlation-Id", "corr-dual-conflict")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order").query(Long.class).single())
                .isEqualTo(2L);
    }

    private void seed(
            String tenant, String projectCode, String clientId, String brand, String workflowKey
    ) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, :clientId, 'Dual OEM IT',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", tenant)
                .param("projectCode", projectCode)
                .param("clientId", clientId)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String workflow = ("""
                {"workflowKey":"%s","semanticVersion":"1.0.0","startNodeId":"START",
                 "nodes":[
                   {"nodeId":"START","nodeType":"START","name":"开始"},
                   {"nodeId":"TASK","nodeType":"SERVICE_TASK","name":"受理",
                    "stageCode":"INTAKE","taskType":"ASSIGN_COORDINATORS"},
                   {"nodeId":"END","nodeType":"END","name":"结束"}],
                 "transitions":[
                   {"transitionId":"t1","from":"START","to":"TASK"},
                   {"transitionId":"t2","from":"TASK","to":"END"}]}
                """).formatted(workflowKey).trim();
        UUID workflowId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenant, ConfigurationAssetType.WORKFLOW, workflowKey, "1.0.0", "1.0.0",
                workflow, Sha256.digest(workflow))).versionId();
        // M335：CREATE_WORK_ORDER 强制 INBOUND Mapping。
        String mappingKey;
        String connectorCode;
        String integration;
        if ("BYD".equals(clientId)) {
            mappingKey = "byd-dual";
            connectorCode = "BYD_CPIM";
            integration = "{\"mappingKey\":\"" + mappingKey + "\",\"version\":\"1.0.0\","
                    + "\"connectorCode\":\"" + connectorCode + "\",\"direction\":\"INBOUND\",\"messageType\":\"CREATE_WORK_ORDER\",\"fieldMappings\":["
                    + "{\"mappingId\":\"order\",\"externalPath\":\"orderCode\",\"internalPath\":\"externalOrderCode\",\"required\":true,\"transform\":\"TRIM\"},"
                    + "{\"mappingId\":\"brand\",\"internalPath\":\"brandCode\",\"required\":true,\"constantValue\":\"BYD_OCEAN\",\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"product\",\"internalPath\":\"serviceProductCode\",\"required\":true,\"constantValue\":\"HOME_CHARGING_SURVEY_INSTALL\",\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"province\",\"externalPath\":\"provinceCode\",\"internalPath\":\"provinceCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"city\",\"externalPath\":\"cityCode\",\"internalPath\":\"cityCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"district\",\"externalPath\":\"areaCode\",\"internalPath\":\"districtCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"name\",\"externalPath\":\"contactName\",\"internalPath\":\"customerName\",\"required\":true,\"transform\":\"TRIM\"},"
                    + "{\"mappingId\":\"mobile\",\"externalPath\":\"contactMobile\",\"internalPath\":\"customerMobile\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"address\",\"externalPath\":\"contactAddress\",\"internalPath\":\"serviceAddress\",\"required\":true,\"transform\":\"TRIM\"},"
                    + "{\"mappingId\":\"vin\",\"externalPath\":\"vin\",\"internalPath\":\"vehicleVin\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"dispatch\",\"externalPath\":\"dispatchTime\",\"internalPath\":\"dispatchedAt\",\"required\":true,\"transform\":\"DATE_ISO\"}"
                    + "]}";
        } else {
            mappingKey = "ref-dual";
            connectorCode = "REFERENCE_OEM";
            integration = "{\"mappingKey\":\"" + mappingKey + "\",\"version\":\"1.0.0\","
                    + "\"connectorCode\":\"" + connectorCode + "\",\"direction\":\"INBOUND\",\"messageType\":\"CREATE_WORK_ORDER\",\"fieldMappings\":["
                    + "{\"mappingId\":\"order\",\"externalPath\":\"externalOrderCode\",\"internalPath\":\"externalOrderCode\",\"required\":true,\"transform\":\"TRIM\"},"
                    + "{\"mappingId\":\"brand\",\"externalPath\":\"brandCode\",\"internalPath\":\"brandCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"product\",\"externalPath\":\"serviceProductCode\",\"internalPath\":\"serviceProductCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"province\",\"externalPath\":\"provinceCode\",\"internalPath\":\"provinceCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"city\",\"externalPath\":\"cityCode\",\"internalPath\":\"cityCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"district\",\"externalPath\":\"districtCode\",\"internalPath\":\"districtCode\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"name\",\"externalPath\":\"customerName\",\"internalPath\":\"customerName\",\"required\":true,\"transform\":\"TRIM\"},"
                    + "{\"mappingId\":\"mobile\",\"externalPath\":\"customerMobile\",\"internalPath\":\"customerMobile\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"address\",\"externalPath\":\"serviceAddress\",\"internalPath\":\"serviceAddress\",\"required\":true,\"transform\":\"TRIM\"},"
                    + "{\"mappingId\":\"vin\",\"externalPath\":\"vehicleVin\",\"internalPath\":\"vehicleVin\",\"required\":true,\"transform\":\"NONE\"},"
                    + "{\"mappingId\":\"dispatch\",\"externalPath\":\"dispatchedAt\",\"internalPath\":\"dispatchedAt\",\"required\":true,\"transform\":\"NONE\"}"
                    + "]}";
        }
        UUID integrationId = configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenant, ConfigurationAssetType.INTEGRATION, mappingKey, "1.0.0", "1.0.0",
                integration, Sha256.digest(integration))).versionId();
        ConfigurationBundleReference fulfillmentBundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                tenant, projectId, projectCode + "-BUNDLE", "1.0.0", brand,
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                null, List.of(workflowId, integrationId)));
        ProjectFulfillmentTestSupport.seedPublishedProfile(
                jdbc, tenant, projectId, "HOME_CHARGING_SURVEY_INSTALL",
                fulfillmentBundle, workflowId, Instant.now().minusSeconds(30));
    }

    private void postByd(String orderCode) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("contactName", "比亚迪客户");
        body.put("contactMobile", "13800000001");
        body.put("contactAddress", "山东省济南市历下区测试路1号");
        body.put("provinceCode", "370000");
        body.put("provinceName", "山东省");
        body.put("cityCode", "370100");
        body.put("cityName", "济南市");
        body.put("areaCode", "370102");
        body.put("areaName", "历下区");
        body.put("wallboxName", "比亚迪7kW交流充电桩");
        body.put("wallboxPower", "7kW");
        body.put("bringWallbox", "1");
        body.put("dispatchTime", "2026-07-18T12:00:00");
        body.put("carOwnerType", "1");
        body.put("type", "1");
        body.put("carBrand", "40");
        body.put("carSeries", "海豹");
        body.put("carModel", "海豹06 DM-i");
        body.put("vin", "LGXCE6CD0RA123456");
        body.put("dealerName", "济南海洋网经销商");
        body.put("rightCode", "RIGHT-DUAL-001");
        body.put("orderAmount", 0);
        body.put("source", "1");
        body.put("channel", "CPIM");
        byte[] raw = objectMapper.writeValueAsBytes(body);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        String nonce = "byd-dual-" + orderCode;
        String sign = new BydCpimSignatureVerifier(
                BYD_KEY, BYD_SECRET, Clock.systemUTC(), ZoneId.of("Asia/Shanghai"))
                .sign(nonce, today, body);
        var result = mvc.perform(post("/api/v1/integrations/byd/cpim/v7.3.1/install-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("APP_KEY", BYD_KEY)
                        .header("Nonce", nonce)
                        .header("Cur_Time", today.toString())
                        .header("Sign", sign)
                        .header("X-Correlation-Id", "corr-dual-byd")
                        .content(raw))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody = result.getResponse().getContentAsString();
        assertThat(responseBody)
                .as("BYD inbound response")
                .contains("\"success\":true");
    }

    private void postReference(String orderCode) throws Exception {
        Map<String, Object> body = referenceBody(orderCode);
        byte[] raw = objectMapper.writeValueAsBytes(body);
        String nonce = "ref-dual-" + orderCode;
        mvc.perform(post("/api/v1/integrations/reference-oem/sample/v1/install-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reference-Oem-Key", REF_KEY)
                        .header("X-Reference-Oem-Nonce", nonce)
                        .header("X-Reference-Oem-Signature",
                                ReferenceOemSampleSignature.sign(REF_SECRET, nonce, raw))
                        .header("X-Correlation-Id", "corr-dual-ref")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orderCode").value(orderCode));
    }

    private static Map<String, Object> referenceBody(String orderCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalOrderCode", orderCode);
        body.put("brandCode", "REFERENCE_BRAND");
        body.put("serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL");
        body.put("provinceCode", "370000");
        body.put("cityCode", "370100");
        body.put("districtCode", "370102");
        body.put("customerName", "参考客户");
        body.put("customerMobile", "13900000002");
        body.put("serviceAddress", "参考地址");
        body.put("vehicleVin", "VINREFDUAL0000001");
        body.put("dispatchedAt", LocalDateTime.of(2026, 7, 18, 12, 0).toString());
        return body;
    }
}
