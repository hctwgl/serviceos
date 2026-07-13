package com.serviceos.integration.byd.web;

import com.serviceos.ServiceOsApplication;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
    private static final String ENDPOINT = "/api/v1/integrations/byd/cpim/v7.3.1/install-orders";

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
        registry.add("serviceos.integration.byd.cpim.allowed-clock-skew", () -> "PT10M");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE TABLE int_inbound_replay_guard").update();
    }

    @Test
    void acceptsValidRequestAndSafelyReplaysSamePayload() throws Exception {
        Map<String, Object> payload = validPayload();
        long currentTime = Instant.now().getEpochSecond();
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
    }

    @Test
    void rejectsPayloadMutationForPreviouslyUsedNonce() throws Exception {
        Map<String, Object> original = validPayload();
        long currentTime = Instant.now().getEpochSecond();
        String nonce = "nonce-http-002";
        perform(original, nonce, currentTime, sign(nonce, currentTime, original))
                .andExpect(jsonPath("$.code").value("ACCEPTED"));

        Map<String, Object> mutated = new LinkedHashMap<>(original);
        mutated.put("contactAddress", "山东省济南市历下区另一地址");
        perform(mutated, nonce, currentTime, sign(nonce, currentTime, mutated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("REPLAY_CONFLICT"));
    }

    @Test
    void invalidBusinessPayloadDoesNotReserveNonce() throws Exception {
        Map<String, Object> invalid = validPayload();
        invalid.put("carBrand", "10");
        long currentTime = Instant.now().getEpochSecond();
        String nonce = "nonce-http-003";

        perform(invalid, nonce, currentTime, sign(nonce, currentTime, invalid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("INVALID_ORDER"));
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                .query(Long.class).single()).isZero();

        Map<String, Object> corrected = validPayload();
        perform(corrected, nonce, currentTime, sign(nonce, currentTime, corrected))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACCEPTED"));
    }

    @Test
    void rejectsInvalidSignatureWithoutWritingReplayState() throws Exception {
        Map<String, Object> payload = validPayload();
        long currentTime = Instant.now().getEpochSecond();

        perform(payload, "nonce-http-004", currentTime, "0".repeat(64))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SIGNATURE_MISMATCH"));
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                .query(Long.class).single()).isZero();
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            Map<String, Object> payload, String nonce, long currentTime, String sign) throws Exception {
        return mvc.perform(post(ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .header("APP_KEY", APP_KEY)
                .header("Nonce", nonce)
                .header("Cur_Time", currentTime)
                .header("Sign", sign)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private String sign(String nonce, long currentTime, Map<String, Object> payload) {
        return new BydCpimSignatureVerifier(APP_KEY, APP_SECRET, Clock.systemUTC(), Duration.ofMinutes(10))
                .sign(APP_KEY, nonce, currentTime, payload);
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
}
