package com.serviceos.integration;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.geely.infrastructure.GeelyAesCipher;
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
 * M320：同一运行时并行冒烟 BYD / REFERENCE_OEM / GEELY 入站建单。
 *
 * <p>不声称吉利 Sandbox 真实联调；仅证明三适配器可共存并共用通用建单管道。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
@SpringBootTest(classes = ServiceOsApplication.class)
class MultiOemParallelCreateSmokePostgresIT {
    private static final String BYD_KEY = "multi-oem-byd-key";
    private static final String BYD_SECRET = "multi-oem-byd-secret";
    private static final String BYD_TENANT = "tenant-multi-byd";
    private static final String BYD_PROJECT = "MULTI-BYD";

    private static final String REF_KEY = "multi-oem-ref-key";
    private static final String REF_SECRET = "multi-oem-ref-secret";
    private static final String REF_TENANT = "tenant-multi-ref";
    private static final String REF_PROJECT = "MULTI-REF";

    private static final String GEELY_KEY = "GENERAL_KEY_DEMO";
    private static final String GEELY_IV = "IV_DEMO_90123456";
    private static final String GEELY_TENANT = "tenant-multi-geely";
    private static final String GEELY_PROJECT = "MULTI-GEELY";

    private static final Path STORAGE_ROOT = Path.of(
            System.getProperty("java.io.tmpdir"), "serviceos-m320-multi-oem-smoke");

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
        registry.add("serviceos.files.local.root", STORAGE_ROOT::toString);
        registry.add("serviceos.files.local.signing-key",
                () -> "m320-multi-oem-private-storage-signing-key-32b");

        registry.add("serviceos.integration.byd.cpim.app-key", () -> BYD_KEY);
        registry.add("serviceos.integration.byd.cpim.app-secret", () -> BYD_SECRET);
        registry.add("serviceos.integration.byd.cpim.zone-id", () -> "Asia/Shanghai");
        registry.add("serviceos.integration.byd.cpim.tenant-id", () -> BYD_TENANT);
        registry.add("serviceos.integration.byd.cpim.project-code", () -> BYD_PROJECT);

        registry.add("serviceos.integration.reference-oem.app-key", () -> REF_KEY);
        registry.add("serviceos.integration.reference-oem.app-secret", () -> REF_SECRET);
        registry.add("serviceos.integration.reference-oem.tenant-id", () -> REF_TENANT);
        registry.add("serviceos.integration.reference-oem.project-code", () -> REF_PROJECT);

        registry.add("serviceos.integration.geely.access-key", () -> GEELY_KEY);
        registry.add("serviceos.integration.geely.aes-iv", () -> GEELY_IV);
        registry.add("serviceos.integration.geely.tenant-id", () -> GEELY_TENANT);
        registry.add("serviceos.integration.geely.project-code", () -> GEELY_PROJECT);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcClient jdbc;
    @Autowired ConfigurationService configurations;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.sql("""
                TRUNCATE TABLE rel_outbox_publish_attempt, rel_outbox_event, wo_work_order,
                    cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, int_canonical_message, int_inbound_envelope,
                    int_inbound_replay_guard, prj_project CASCADE
                """).update();
        Files.createDirectories(STORAGE_ROOT);
        seedTenant(BYD_TENANT, BYD_PROJECT, "BYD", "BYD_OCEAN", "byd.multi.linear");
        seedTenant(REF_TENANT, REF_PROJECT, "REFERENCE_OEM", "REFERENCE_BRAND", "ref.multi.linear");
        seedTenant(GEELY_TENANT, GEELY_PROJECT, "GEELY", "GEELY", "geely.multi.linear");
    }

    @Test
    void threeOemInboundCreatesShareGenericPipelineInOneRuntime() throws Exception {
        postByd();
        postReference();
        postGeely();

        assertThat(jdbc.sql("SELECT count(*) FROM wo_work_order").query(Long.class).single())
                .isEqualTo(3L);
        assertThat(jdbc.sql("SELECT client_code FROM wo_work_order ORDER BY client_code")
                .query(String.class).list())
                .containsExactly("BYD", "GEELY", "REFERENCE_OEM");
        assertThat(jdbc.sql("""
                SELECT DISTINCT connector_version_id FROM int_canonical_message ORDER BY 1
                """).query(String.class).list())
                .contains(
                        "byd-cpim-v7.3.1",
                        "geely-haohan-v1.3-local",
                        "reference-oem-sample-v1");
    }

    private void postByd() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", "BYD-MULTI-001");
        payload.put("contactName", "比亚迪用户");
        payload.put("contactMobile", "13800000001");
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
        payload.put("dispatchTime", "2026-07-19T10:00:00");
        payload.put("carOwnerType", "1");
        payload.put("type", "1");
        payload.put("carBrand", "40");
        payload.put("carSeries", "海豹");
        payload.put("carModel", "海豹06 DM-i");
        payload.put("vin", "LGXCE6CD0RA654321");
        payload.put("dealerName", "济南海洋网经销商");
        payload.put("rightCode", "RIGHT-MULTI-001");
        payload.put("orderAmount", 0);
        payload.put("source", "1");
        payload.put("channel", "CPIM");
        String currentTime = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String nonce = "nonce-multi-byd-1";
        String sign = new BydCpimSignatureVerifier(
                BYD_KEY, BYD_SECRET, Clock.systemUTC(), ZoneId.of("Asia/Shanghai"))
                .sign(nonce, LocalDate.parse(currentTime), payload);
        mvc.perform(post("/api/v1/integrations/byd/cpim/v7.3.1/install-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("APP_KEY", BYD_KEY)
                        .header("Nonce", nonce)
                        .header("Cur_Time", currentTime)
                        .header("Sign", sign)
                        .header("X-Correlation-Id", "corr-multi-byd")
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private void postReference() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("externalOrderCode", "REF-MULTI-001");
        body.put("brandCode", "REFERENCE_BRAND");
        body.put("serviceProductCode", "HOME_CHARGING_SURVEY_INSTALL");
        body.put("provinceCode", "370000");
        body.put("cityCode", "370100");
        body.put("districtCode", "370102");
        body.put("customerName", "参考客户");
        body.put("customerMobile", "13900000002");
        body.put("serviceAddress", "参考地址");
        body.put("vehicleVin", "VINREFMULTI0000001");
        body.put("dispatchedAt", LocalDateTime.of(2026, 7, 19, 11, 0).toString());
        byte[] raw = objectMapper.writeValueAsBytes(body);
        String nonce = "nonce-multi-ref-1";
        String signature = ReferenceOemSampleSignature.sign(REF_SECRET, nonce, raw);
        mvc.perform(post("/api/v1/integrations/reference-oem/sample/v1/install-orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Reference-Oem-Key", REF_KEY)
                        .header("X-Reference-Oem-Nonce", nonce)
                        .header("X-Reference-Oem-Signature", signature)
                        .header("X-Correlation-Id", "corr-multi-ref")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private void postGeely() throws Exception {
        Map<String, Object> plain = new LinkedHashMap<>();
        plain.put("installProcessNo", "IN20260719MULTI01");
        plain.put("workNo", "HW20260719MULTI01");
        plain.put("assignProviderTime", "2026-07-19 10:00:00");
        plain.put("applyTime", "2026-07-19 09:00:00");
        plain.put("status", 1);
        plain.put("contactName", "吉利联系人");
        plain.put("contactPhone", "13800000003");
        plain.put("province", "370000");
        plain.put("city", "370100");
        plain.put("district", "370102");
        plain.put("address", "济南多OEM地址");
        plain.put("carBrand", "GEELY");
        plain.put("vin", "VINGEELYMULTI00001");
        plain.put("carryProduct", 1);
        plain.put("licensePhotoSkip", 0);
        plain.put("productList", List.of(Map.of(
                "productName", "7kW", "productPower", 7.0, "packageInfo", "PKG")));
        String cipher = GeelyAesCipher.encryptToBase64(
                objectMapper.writeValueAsString(plain), GEELY_KEY, GEELY_IV);
        byte[] raw = objectMapper.writeValueAsBytes(Map.of(
                "providerNo", "SEA",
                "timestamp", "20260719110000",
                "data", cipher));
        mvc.perform(post("/api/v1/integrations/geely/haohan/v1.3/notify_create_order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-Id", "corr-multi-geely")
                        .content(raw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"));
    }

    private void seedTenant(
            String tenantId, String projectCode, String clientId, String brandCode, String workflowKey
    ) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project (
                    project_id, tenant_id, project_code, client_id, project_name,
                    starts_on, ends_on, project_status, aggregate_version, created_at
                ) VALUES (
                    :projectId, :tenantId, :projectCode, :clientId, :projectName,
                    :startsOn, NULL, 'ACTIVE', 1, :createdAt
                )
                """)
                .param("projectId", projectId)
                .param("tenantId", tenantId)
                .param("projectCode", projectCode)
                .param("clientId", clientId)
                .param("projectName", "Multi OEM " + clientId)
                .param("startsOn", LocalDate.now().minusDays(1))
                .param("createdAt", OffsetDateTime.now())
                .update();
        String workflow = "{\"workflowKey\":\"" + workflowKey
                + "\",\"semanticVersion\":\"1.0.0\",\"startNodeId\":\"START\","
                + "\"nodes\":[{\"nodeId\":\"START\",\"nodeType\":\"START\",\"name\":\"开始\"},"
                + "{\"nodeId\":\"TASK\",\"nodeType\":\"SERVICE_TASK\",\"name\":\"受理\","
                + "\"stageCode\":\"INTAKE\",\"taskType\":\"ASSIGN_COORDINATORS\"},"
                + "{\"nodeId\":\"END\",\"nodeType\":\"END\",\"name\":\"结束\"}],"
                + "\"transitions\":[{\"transitionId\":\"t1\",\"from\":\"START\",\"to\":\"TASK\"},"
                + "{\"transitionId\":\"t2\",\"from\":\"TASK\",\"to\":\"END\"}]}";
        var workflowAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenantId, ConfigurationAssetType.WORKFLOW, workflowKey,
                "1.0.0", "1.0.0", workflow, Sha256.digest(workflow)));
        if ("BYD".equals(clientId)) {
            String integration = "{\"mappingKey\":\"byd-multi\",\"version\":\"1.0.0\","
                    + "\"connectorCode\":\"BYD_CPIM\",\"direction\":\"INBOUND\","
                    + "\"fieldMappings\":[{\"mappingId\":\"order\",\"externalPath\":\"orderCode\","
                    + "\"internalPath\":\"externalOrderCode\",\"required\":true,\"transform\":\"TRIM\"}]}";
            var integrationAsset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                    tenantId, ConfigurationAssetType.INTEGRATION, "byd-multi",
                    "1.0.0", "1.0.0", integration, Sha256.digest(integration)));
            configurations.publishBundle(new PublishConfigurationBundleCommand(
                    tenantId, projectId, projectCode + "-BUNDLE", "1.0.0", brandCode,
                    "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                    null, List.of(workflowAsset.versionId(), integrationAsset.versionId())));
        } else {
            configurations.publishBundle(new PublishConfigurationBundleCommand(
                    tenantId, projectId, projectCode + "-BUNDLE", "1.0.0", brandCode,
                    "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60),
                    null, List.of(workflowAsset.versionId())));
        }
    }
}
