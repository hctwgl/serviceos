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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class BydCpimInboundOrderHttpPostgresIT {
    private static final String APP_KEY = "byd-http-test-key";
    private static final String APP_SECRET = "byd-http-test-secret";
    private static final String TENANT_ID = "tenant-byd-http-test";
    private static final String PROJECT_CODE = "BYD-OCEAN-SD-HTTP";
    private static final String ENDPOINT = "/api/v1/integrations/byd/cpim/v7.3.1/install-orders";
    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m56-byd-inbound-it");

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
                () -> "m56-byd-inbound-private-storage-signing-key");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    ConfigurationService configurations;

    UUID projectId;
    UUID integrationAssetVersionId;

    @BeforeEach
    void clean() throws IOException {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
                    cfg_configuration_bundle_item,
                    cfg_configuration_bundle, cfg_configuration_asset_version,
                    prj_project, int_inbound_replay_guard,
                    int_canonical_message, int_inbound_envelope,
                    aud_audit_record CASCADE
                """).update();
        deleteRecursively(STORAGE_ROOT);
        Files.createDirectories(STORAGE_ROOT);
        projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, 'BYD', '比亚迪海洋山东试点',
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", TENANT_ID)
                .param("projectCode", PROJECT_CODE)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", java.time.OffsetDateTime.now())
                .update();
        integrationAssetVersionId = publishPilotBundle();
    }

    @Test
    void acceptsValidRequestAndSafelyReplaysSamePayload() throws Exception {
        Map<String, Object> payload = validPayload();
        String currentTime = protocolDate();
        String nonce = "nonce-http-001";
        String sign = sign(nonce, currentTime, payload);

        perform(payload, nonce, currentTime, sign)
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderCode").value("BYD-SD-HTTP-001"))
                .andExpect(jsonPath("$.replay").value(false));

        perform(payload, nonce, currentTime, sign)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("REPLAYED"))
                .andExpect(jsonPath("$.replay").value(true));

        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_envelope")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isEqualTo(1);
        String rawObjectRef = jdbc.sql("SELECT raw_payload_object_ref FROM int_inbound_envelope")
                .query(String.class).single();
        assertThat(Files.readString(STORAGE_ROOT.resolve(rawObjectRef)))
                .isEqualTo(objectMapper.writeValueAsString(payload));
        assertThat(jdbc.sql("""
                SELECT count(*) FROM rel_outbox_event
                 WHERE event_type='integration.canonical-message-processed'
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name='INBOUND_MESSAGE_PROCESSED'
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("""
                SELECT count(*) FROM aud_audit_record
                 WHERE action_name='INBOUND_INTEGRATION_MAPPING_APPLIED'
                """).query(Long.class).single()).isGreaterThanOrEqualTo(1);
        // M321：Mapping 物化后 mappingVersionId 必须是冻结资产 versionId，而非 OEM 常量。
        assertThat(jdbc.sql("""
                SELECT mapping_version_id FROM int_canonical_message
                """).query(String.class).single())
                .isEqualTo(integrationAssetVersionId.toString());
        assertThat(jdbc.sql("""
                SELECT tenant_id, project_id, configuration_bundle_code,
                       configuration_bundle_version, status
                  FROM wo_work_order WHERE external_order_code = 'BYD-SD-HTTP-001'
                """).query().singleRow())
                .containsEntry("tenant_id", TENANT_ID)
                .containsEntry("project_id", projectId)
                .containsEntry("configuration_bundle_code", "BYD-OCEAN-SD-PILOT")
                .containsEntry("configuration_bundle_version", "1.0.0")
                .containsEntry("status", "RECEIVED");

        // 业务幂等不依赖 Nonce；新 Nonce 的同一载荷仍返回原工单，不得重复创建。
        String secondNonce = "nonce-http-001-second";
        perform(payload, secondNonce, currentTime, sign(secondNonce, currentTime, payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPLAYED"));
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_envelope")
                .query(Long.class).single()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(DISTINCT canonical_message_id)
                  FROM int_inbound_envelope WHERE processing_status='COMPLETED'
                """).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void rejectsPayloadMutationForPreviouslyUsedNonce() throws Exception {
        Map<String, Object> original = validPayload();
        String currentTime = protocolDate();
        String nonce = "nonce-http-002";
        perform(original, nonce, currentTime, sign(nonce, currentTime, original))
                .andExpect(jsonPath("$.code").value("ACCEPTED"));

        Map<String, Object> mutated = new LinkedHashMap<>(original);
        mutated.put("contactAddress", "山东省济南市历下区另一地址");
        perform(mutated, nonce, currentTime, sign(nonce, currentTime, mutated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPLAY_CONFLICT"));
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_envelope")
                .query(Long.class).single()).isOne();
    }

    @Test
    void authenticatedInvalidBusinessPayloadIsRetainedAndConsumesNonce() throws Exception {
        Map<String, Object> invalid = validPayload();
        invalid.put("carBrand", "10");
        String currentTime = protocolDate();
        String nonce = "nonce-http-003";

        perform(invalid, nonce, currentTime, sign(nonce, currentTime, invalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"));
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT processing_status FROM int_inbound_envelope")
                .query(String.class).single()).isEqualTo("REJECTED");
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isZero();

        Map<String, Object> corrected = validPayload();
        perform(corrected, nonce, currentTime, sign(nonce, currentTime, corrected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("REPLAY_CONFLICT"));
    }

    @Test
    void rejectsInvalidSignatureWithoutWritingReplayState() throws Exception {
        Map<String, Object> payload = validPayload();
        String currentTime = protocolDate();

        perform(payload, "nonce-http-004", currentTime, "0".repeat(64))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SIGNATURE_MISMATCH"));
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_envelope")
                .query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isZero();
    }

    @Test
    void rejectsSameBusinessKeyWithDifferentPayloadAndKeepsOriginalWorkOrder() throws Exception {
        Map<String, Object> original = validPayload();
        String currentTime = protocolDate();
        String firstNonce = "nonce-http-order-conflict-1";
        perform(original, firstNonce, currentTime, sign(firstNonce, currentTime, original))
                .andExpect(jsonPath("$.code").value("ACCEPTED"));

        Map<String, Object> changed = new LinkedHashMap<>(original);
        changed.put("contactAddress", "山东省济南市历下区冲突地址2号");
        String secondNonce = "nonce-http-order-conflict-2";
        perform(changed, secondNonce, currentTime, sign(secondNonce, currentTime, changed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPLAY_CONFLICT"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("ORDER_CONFLICT:")));

        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM int_inbound_envelope
                 WHERE processing_status='REJECTED' AND result_code='REPLAY_CONFLICT'
                """).query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT service_address FROM wo_work_order")
                .query(String.class).single()).isEqualTo(original.get("contactAddress"));
    }

    @Test
    void retainsAuthenticatedOrderAsRejectedWhenNoBundleMatches() throws Exception {
        jdbc.sql("TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order, cfg_configuration_bundle_item, "
                + "cfg_configuration_bundle, cfg_configuration_asset_version CASCADE").update();
        Map<String, Object> payload = validPayload();
        String currentTime = protocolDate();
        String nonce = "nonce-http-no-config";

        perform(payload, nonce, currentTime, sign(nonce, currentTime, payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.startsWith("CONFIGURATION_NO_MATCH:")));

        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT processing_status FROM int_inbound_envelope")
                .query(String.class).single()).isEqualTo("REJECTED");
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isZero();
    }

    @Test
    void retainsReceivedEnvelopeAndRecoversAfterWorkOrderPersistenceFailure() throws Exception {
        jdbc.sql("""
                CREATE OR REPLACE FUNCTION wo_test_reject_insert()
                RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'forced work order failure';
                END;
                $$
                """).update();
        jdbc.sql("""
                CREATE TRIGGER trg_wo_test_reject_insert
                    BEFORE INSERT ON wo_work_order
                    FOR EACH ROW EXECUTE FUNCTION wo_test_reject_insert()
                """).update();
        Map<String, Object> payload = validPayload();
        String currentTime = protocolDate();
        String nonce = "nonce-http-transaction-rollback";
        try {

            assertThatThrownBy(() -> perform(
                    payload, nonce, currentTime, sign(nonce, currentTime, payload)).andReturn())
                    .rootCause()
                    .hasMessageContaining("forced work order failure");

            assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                    .query(Long.class).single()).isOne();
            assertThat(jdbc.sql("SELECT processing_status FROM int_inbound_envelope")
                    .query(String.class).single()).isEqualTo("RECEIVED");
            assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                    .query(Long.class).single()).isZero();
            assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                    .query(Long.class).single()).isZero();
        } finally {
            jdbc.sql("DROP TRIGGER IF EXISTS trg_wo_test_reject_insert ON wo_work_order").update();
            jdbc.sql("DROP FUNCTION IF EXISTS wo_test_reject_insert()").update();
        }

        perform(payload, nonce, currentTime, sign(nonce, currentTime, payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACCEPTED"));
        assertThat(jdbc.sql("SELECT processing_status FROM int_inbound_envelope")
                .query(String.class).single()).isEqualTo("COMPLETED");
        assertThat(jdbc.sql("SELECT count(*) FROM int_canonical_message")
                .query(Long.class).single()).isOne();
        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order")
                .query(Long.class).single()).isOne();
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            Map<String, Object> payload, String nonce, String currentTime, String sign) throws Exception {
        return mvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("APP_KEY", APP_KEY)
                .header("Nonce", nonce)
                .header("Cur_Time", currentTime)
                .header("Sign", sign)
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

    private UUID publishPilotBundle() {
        String workflow = "{\"workflowCode\":\"BYD_SURVEY_INSTALL_V1\"}";
        var asset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT_ID,
                ConfigurationAssetType.WORKFLOW,
                "BYD_SURVEY_INSTALL",
                "1.0.0",
                "1.0.0",
                workflow,
                Sha256.digest(workflow)));
        // M333：建单必填字段均由 INBOUND Mapping 提供；UPPER 证明 Mapping 权威。
        String integration = """
                {"mappingKey":"byd-create-http","version":"1.0.0","connectorCode":"BYD_CPIM","direction":"INBOUND","fieldMappings":[
                  {"mappingId":"order","externalPath":"orderCode","internalPath":"externalOrderCode","required":true,"transform":"UPPER"},
                  {"mappingId":"brand","externalPath":"brandCode","internalPath":"brandCode","required":true,"transform":"NONE"},
                  {"mappingId":"product","externalPath":"serviceProductCode","internalPath":"serviceProductCode","required":true,"transform":"NONE"},
                  {"mappingId":"province","externalPath":"provinceCode","internalPath":"provinceCode","required":true,"transform":"NONE"},
                  {"mappingId":"city","externalPath":"cityCode","internalPath":"cityCode","required":true,"transform":"NONE"},
                  {"mappingId":"district","externalPath":"areaCode","internalPath":"districtCode","required":true,"transform":"NONE"},
                  {"mappingId":"name","externalPath":"contactName","internalPath":"customerName","required":false,"transform":"TRIM"},
                  {"mappingId":"mobile","externalPath":"contactMobile","internalPath":"customerMobile","required":true,"transform":"NONE"},
                  {"mappingId":"address","externalPath":"contactAddress","internalPath":"serviceAddress","required":false,"transform":"TRIM"},
                  {"mappingId":"vin","externalPath":"vin","internalPath":"vehicleVin","required":false,"transform":"NONE"},
                  {"mappingId":"dispatch","externalPath":"dispatchTime","internalPath":"dispatchedAt","required":false,"transform":"DATE_ISO"}]}
                """.replaceAll("\\s+", "");
        var integrationAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                TENANT_ID,
                ConfigurationAssetType.INTEGRATION,
                "byd-create-http",
                "1.0.0",
                "1.0.0",
                integration,
                Sha256.digest(integration)));
        configurations.publishBundle(new PublishConfigurationBundleCommand(
                TENANT_ID,
                projectId,
                "BYD-OCEAN-SD-PILOT",
                "1.0.0",
                "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL",
                "370000",
                Instant.now().minusSeconds(3600),
                null,
                java.util.List.of(asset.versionId(), integrationAsset.versionId())));
        return integrationAsset.versionId();
    }

    @Test
    void mappingUpperMaterializesExternalOrderCodeOntoWorkOrder() throws Exception {
        Map<String, Object> payload = validPayload();
        payload.put("orderCode", "byd-sd-http-upper");
        String currentTime = protocolDate();
        String nonce = "nonce-http-upper-001";
        String sign = sign(nonce, currentTime, payload);

        perform(payload, nonce, currentTime, sign)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("ACCEPTED"));

        // 适配器兼容层保留原文大小写；Mapping UPPER 才是工单权威值。
        assertThat(jdbc.sql("""
                SELECT external_order_code FROM wo_work_order
                """).query(String.class).single()).isEqualTo("BYD-SD-HTTP-UPPER");
        assertThat(jdbc.sql("""
                SELECT business_key, mapping_version_id FROM int_canonical_message
                """).query().singleRow())
                .containsEntry("business_key", "BYD:INSTALL:BYD-SD-HTTP-UPPER")
                .containsEntry("mapping_version_id", integrationAssetVersionId.toString());
        String canonicalObjectRef = jdbc.sql("""
                SELECT payload_object_ref FROM int_canonical_message
                """).query(String.class).single();
        String canonicalJson = Files.readString(STORAGE_ROOT.resolve(canonicalObjectRef));
        assertThat(canonicalJson)
                .contains("\"mappingContentDigest\"")
                .contains(integrationAssetVersionId.toString())
                .contains("BYD-SD-HTTP-UPPER");
    }

    private static Map<String, Object> validPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", "BYD-SD-HTTP-001");
        payload.put("contactName", "测试用户");
        payload.put("contactMobile", "13800000000");
        payload.put("contactAddress", "山东省济南市历下区测试路1号");
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
        payload.put("vin", "LGXCE6CD0RA123456");
        payload.put("dealerName", "济南海洋网经销商");
        payload.put("rightCode", "RIGHT-HTTP-001");
        payload.put("orderAmount", 0);
        payload.put("source", "1");
        payload.put("channel", "CPIM");
        return payload;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
