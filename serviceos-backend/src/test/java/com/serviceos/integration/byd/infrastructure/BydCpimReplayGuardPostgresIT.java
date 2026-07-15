package com.serviceos.integration.byd.infrastructure;

import com.serviceos.ServiceOsApplication;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class BydCpimReplayGuardPostgresIT {
    private static final long REQUEST_DATE = LocalDate.parse("2026-07-15").toEpochDay();
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("serviceos")
            .withUsername("serviceos_test")
            .withPassword("serviceos_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    JdbcBydCpimReplayGuard guard;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE TABLE int_inbound_replay_guard").update();
    }

    @Test
    void firstRequestIsNewAndSamePayloadIsReplayable() {
        var first = guard.register("app-key", "nonce-001", REQUEST_DATE, "a".repeat(64), null);
        guard.complete("app-key", "nonce-001", REQUEST_DATE, "b".repeat(64));
        var replay = guard.register("app-key", "nonce-001", REQUEST_DATE, "a".repeat(64), null);

        assertThat(first.kind()).isEqualTo(BydCpimReplayDecision.Kind.NEW);
        assertThat(replay.kind()).isEqualTo(BydCpimReplayDecision.Kind.REPLAY);
        assertThat(replay.resultDigest()).isEqualTo("b".repeat(64));
        assertThat(jdbc.sql("SELECT count(*) FROM int_inbound_replay_guard")
                .query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void sameNonceWithDifferentPayloadIsRejected() {
        guard.register("app-key", "nonce-002", REQUEST_DATE, "a".repeat(64), null);

        assertThatThrownBy(() -> guard.register(
                "app-key", "nonce-002", REQUEST_DATE, "c".repeat(64), null))
                .isInstanceOf(BydCpimReplayConflictException.class);
    }

    @Test
    void migrationIsRepeatableAtCurrentVersion() {
        assertThat(flyway.info().applied()).hasSize(70);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }
}
