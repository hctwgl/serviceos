package com.serviceos.bootstrap;

import com.serviceos.ServiceOsApplication;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1-10 运行态证据：真实 PostgreSQL 下验证探针、关联头和 Prometheus 自定义指标。
 */
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMetrics
@AutoConfigureTracing
@SpringBootTest(classes = ServiceOsApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ObservabilityPostgresIT {
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
        registry.add("management.tracing.sampling.probability", () -> "1.0");
        registry.add("management.tracing.export.otlp.enabled", () -> "false");
    }

    @LocalServerPort
    int port;

    @Autowired
    PrometheusMeterRegistry prometheus;

    @Test
    void exposesIndependentLivenessAndDatabaseAwareReadiness() throws Exception {
        HttpResponse<String> liveness = get("/livez", "probe-live-1");
        HttpResponse<String> readiness = get("/readyz", "probe-ready-1");

        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).contains("\"status\":\"UP\"");
        assertThat(liveness.headers().firstValue("X-Correlation-Id")).contains("probe-live-1");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"");
        assertThat(readiness.headers().firstValue("X-Correlation-Id")).contains("probe-ready-1");
    }

    @Test
    void publishesBoundedOutboxGaugesAndProtectsTheScrapeEndpoint() throws Exception {
        String scrape = prometheus.scrape();
        HttpResponse<String> anonymous = get("/actuator/prometheus", "metrics-anonymous-1");

        assertThat(scrape).contains("serviceos_outbox_backlog", "serviceos_outbox_oldest_age_seconds");
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.headers().firstValue("X-Correlation-Id")).contains("metrics-anonymous-1");
    }

    private HttpResponse<String> get(String path, String correlationId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("X-Correlation-Id", correlationId)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
