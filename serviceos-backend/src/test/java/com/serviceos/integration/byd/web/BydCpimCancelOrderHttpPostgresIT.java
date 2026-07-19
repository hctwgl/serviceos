package com.serviceos.integration.byd.web;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class BydCpimCancelOrderHttpPostgresIT {
    private static final String APP_KEY = "byd-cancel-http-key";
    private static final String APP_SECRET = "byd-cancel-http-secret";
    private static final String TENANT_ID = "tenant-byd-cancel-http";
    private static final String PROJECT_CODE = "BYD-OCEAN-SD-CANCEL";
    private static final String CREATE_ENDPOINT = "/api/v1/integrations/byd/cpim/v7.3.1/install-orders";
    private static final String CANCEL_ENDPOINT = "/api/v1/integrations/byd/cpim/v7.3.1/cancel-orders";
    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m300-byd-cancel-it");

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
        registry.add("serviceos.integration.byd.cpim.app-key", () -> APP_KEY);
        registry.add("serviceos.integration.byd.cpim.app-secret", () -> APP_SECRET);
        registry.add("serviceos.integration.byd.cpim.zone-id", () -> "Asia/Shanghai");
        registry.add("serviceos.integration.byd.cpim.tenant-id", () -> TENANT_ID);
        registry.add("serviceos.integration.byd.cpim.project-code", () -> PROJECT_CODE);
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "m300-byd-cancel-private-storage-signing-key");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired ConfigurationService configurations;

    UUID projectId;

    @BeforeEach
    void clean() throws IOException {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project, int_inbound_replay_guard,
                    int_canonical_message, int_inbound_envelope, aud_audit_record CASCADE
                """).update();
        if (Files.exists(STORAGE_ROOT)) {
            try (var paths = Files.walk(STORAGE_ROOT)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        Files.createDirectories(STORAGE_ROOT);
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'BYD', '取消入站试点',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT_ID)
                .param("projectCode", PROJECT_CODE)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", java.time.OffsetDateTime.now())
                .update();
        String workflow = "{\"workflowCode\":\"BYD_SURVEY_INSTALL_V1\"}";
        var asset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT_ID, ConfigurationAssetType.WORKFLOW, "BYD_SURVEY_INSTALL",
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        // M339：同一 Bundle 共存 CREATE + CANCEL Mapping（按 messageType 区分）。
        String createIntegration = """
                {"mappingKey":"byd-cancel-create","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"CREATE_WORK_ORDER","fieldMappings":[
                  {"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                  {"mappingId":"brand","internalPath":"brandCode","required":true,"constantValue":"BYD_OCEAN","transform":"NONE"},
                  {"mappingId":"product","internalPath":"serviceProductCode","required":true,"constantValue":"HOME_CHARGING_SURVEY_INSTALL","transform":"NONE"},
                  {"mappingId":"province","externalPath":"provinceCode","internalPath":"provinceCode","required":true,"transform":"NONE"},
                  {"mappingId":"city","externalPath":"cityCode","internalPath":"cityCode","required":true,"transform":"NONE"},
                  {"mappingId":"district","externalPath":"areaCode","internalPath":"districtCode","required":true,"transform":"NONE"},
                  {"mappingId":"name","externalPath":"contactName","internalPath":"customerName","required":true,"transform":"TRIM"},
                  {"mappingId":"mobile","externalPath":"contactMobile","internalPath":"customerMobile","required":true,"transform":"NONE"},
                  {"mappingId":"address","externalPath":"contactAddress","internalPath":"serviceAddress","required":true,"transform":"TRIM"},
                  {"mappingId":"vin","externalPath":"vin","internalPath":"vehicleVin","required":true,"transform":"NONE"},
                  {"mappingId":"dispatch","externalPath":"dispatchTime","internalPath":"dispatchedAt","required":true,"transform":"DATE_ISO"}]}
                """.replaceAll("\\s+", "");
        String cancelIntegration = """
                {"mappingKey":"byd-cancel-cancel","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","messageType":"CANCEL_WORK_ORDER","fieldMappings":[
                  {"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"TRIM"},
                  {"mappingId":"reason","internalPath":"reasonCode","required":true,"constantValue":"EXTERNAL_USER_CANCEL","transform":"NONE"},
                  {"mappingId":"approval","externalPath":"cancelReason","internalPath":"approvalRef","required":false,"transform":"TRIM"}]}
                """.replaceAll("\\s+", "");
        var createAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT_ID, ConfigurationAssetType.INTEGRATION, "byd-cancel-create",
                "1.0.0", "1.0.0", createIntegration, Sha256.digest(createIntegration)));
        var cancelAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT_ID, ConfigurationAssetType.INTEGRATION, "byd-cancel-cancel",
                "1.0.0", "1.0.0", cancelIntegration, Sha256.digest(cancelIntegration)));
        configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT_ID, projectId, "BYD-OCEAN-SD-PILOT", "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(3600),
                null, java.util.List.of(asset.versionId(), createAsset.versionId(), cancelAsset.versionId())));
    }

    @Test
    void createThenCancelMarksWorkOrderCancelledAndReplaysSafely() throws Exception {
        Map<String, Object> createPayload = createPayload();
        String currentTime = protocolDate();
        perform(CREATE_ENDPOINT, createPayload, "nonce-create-1", currentTime)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACCEPTED"));
        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isEqualTo("RECEIVED");

        Map<String, Object> cancelPayload = cancelPayload();
        perform(CANCEL_ENDPOINT, cancelPayload, "nonce-cancel-1", currentTime)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderCode").value("BYD-SD-CANCEL-001"));

        assertThat(jdbc.sql("SELECT status FROM wo_work_order").query(String.class).single())
                .isEqualTo("CANCELLED");
        assertThat(jdbc.sql("SELECT cancel_reason_code FROM wo_work_order").query(String.class).single())
                .isEqualTo("EXTERNAL_USER_CANCEL");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event WHERE event_type='workorder.cancelled'
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM int_canonical_message WHERE message_type='CANCEL_WORK_ORDER'
                """).query(Long.class).single()).isOne();

        perform(CANCEL_ENDPOINT, cancelPayload, "nonce-cancel-1", currentTime)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPLAYED"));
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order").query(Long.class).single()).isOne();
    }

    @Test
    void cancelWithoutExistingWorkOrderRejectsFailClosed() throws Exception {
        Map<String, Object> cancelPayload = cancelPayload();
        String currentTime = protocolDate();
        perform(CANCEL_ENDPOINT, cancelPayload, "nonce-cancel-missing", currentTime)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("WORK_ORDER_NOT_FOUND"));
        assertThat(jdbc.sql("SELECT processing_status FROM int_inbound_envelope")
                .query(String.class).single()).isEqualTo("REJECTED");
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            String endpoint, Map<String, Object> payload, String nonce, String currentTime
    ) throws Exception {
        return mvc.perform(post(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("APP_KEY", APP_KEY)
                .header("Nonce", nonce)
                .header("Cur_Time", currentTime)
                .header("Sign", sign(nonce, currentTime, payload))
                .content(objectMapper.writeValueAsString(payload)));
    }

    private String sign(String nonce, String currentTime, Map<String, Object> payload) {
        return new BydCpimSignatureVerifier(
                APP_KEY, APP_SECRET, Clock.systemUTC(), ZoneId.of("Asia/Shanghai"))
                .sign(nonce, LocalDate.parse(currentTime), payload);
    }

    private static String protocolDate() {
        return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
    }

    private static Map<String, Object> createPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", "BYD-SD-CANCEL-001");
        payload.put("contactName", "取消测试用户");
        payload.put("contactMobile", "13800000001");
        payload.put("contactAddress", "山东省济南市历下区取消路1号");
        payload.put("provinceCode", "370000");
        payload.put("provinceName", "山东省");
        payload.put("cityCode", "370100");
        payload.put("cityName", "济南市");
        payload.put("areaCode", "370102");
        payload.put("areaName", "历下区");
        payload.put("wallboxName", "比亚迪7kW交流充电桩");
        payload.put("wallboxPower", "7kW");
        payload.put("bringWallbox", "1");
        payload.put("dispatchTime", "2026-07-13T10:00:00");
        payload.put("carOwnerType", "1");
        payload.put("type", "1");
        payload.put("carBrand", "40");
        payload.put("carSeries", "海豹");
        payload.put("carModel", "海豹06 DM-i");
        payload.put("vin", "LGXCE6CD0RA654321");
        payload.put("dealerName", "济南海洋网经销商");
        payload.put("rightCode", "RIGHT-CANCEL-001");
        payload.put("orderAmount", 0);
        payload.put("source", "1");
        payload.put("channel", "CPIM");
        return payload;
    }

    private static Map<String, Object> cancelPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", "BYD-SD-CANCEL-001");
        payload.put("cancelDate", "2026-07-19 15:30:00");
        payload.put("cancelReason", "用户主动取消");
        return payload;
    }
}
