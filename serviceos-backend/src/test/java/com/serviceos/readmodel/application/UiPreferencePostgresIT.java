package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.UiPreferenceCommandService;
import com.serviceos.readmodel.api.UiPreferenceEntry;
import com.serviceos.readmodel.api.UiPreferenceQueryService;
import com.serviceos.readmodel.api.UiPreferenceWrite;
import com.serviceos.readmodel.api.UiPreferencesDocument;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.StringNode;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M190 Admin UI Preference：CRUD、白名单拒绝、租户/主体隔离、禁止键与版本冲突。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UiPreferencePostgresIT {
    private static final String TENANT_A = "tenant-pref-a";
    private static final String TENANT_B = "tenant-pref-b";
    private static final String PRINCIPAL_A = "019f81a0-aaaa-7f8c-9505-36fe5c0e8801";
    private static final String PRINCIPAL_B = "019f81a0-bbbb-7f8c-9505-36fe5c0e8802";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

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

    @Autowired UiPreferenceQueryService queries;
    @Autowired UiPreferenceCommandService commands;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE TABLE rdm_ui_preference CASCADE").update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("149");
        assertThat(flyway.info().applied()).hasSize(151);
    }

    @Test
    void crudPutGetAndDelete() {
        UiPreferencesDocument put = commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-put",
                "ADMIN",
                Map.of(
                        "theme", write(StringNode.valueOf("DARK"), null),
                        "density", write(StringNode.valueOf("COMPACT"), null),
                        "reduceMotion", write(BooleanNode.TRUE, null)
                ));
        assertThat(put.preferences()).containsKeys("theme", "density", "reduceMotion");
        assertThat(put.preferences().get("theme").value().asString()).isEqualTo("DARK");
        assertThat(put.preferences().get("theme").aggregateVersion()).isEqualTo(1L);

        UiPreferencesDocument got = queries.get(actor(PRINCIPAL_A, TENANT_A), "corr-get", "ADMIN");
        assertThat(got.preferences()).hasSize(3);

        UiPreferenceEntry theme = got.preferences().get("theme");
        UiPreferencesDocument updated = commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-upd",
                "ADMIN",
                Map.of("theme", write(StringNode.valueOf("LIGHT"), theme.aggregateVersion())));
        assertThat(updated.preferences().get("theme").value().asString()).isEqualTo("LIGHT");
        assertThat(updated.preferences().get("theme").aggregateVersion()).isEqualTo(2L);

        commands.delete(actor(PRINCIPAL_A, TENANT_A), "corr-del", "ADMIN", "density");
        assertThat(queries.get(actor(PRINCIPAL_A, TENANT_A), "corr-get-2", "ADMIN").preferences())
                .doesNotContainKey("density")
                .containsKeys("theme", "reduceMotion");
    }

    @Test
    void tenantAndPrincipalIsolation() {
        commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-own",
                "ADMIN",
                Map.of("locale", write(StringNode.valueOf("zh-CN"), null)));

        assertThat(queries.get(actor(PRINCIPAL_B, TENANT_A), "corr-other", "ADMIN").preferences())
                .isEmpty();
        assertThat(queries.get(actor(PRINCIPAL_A, TENANT_B), "corr-tenant", "ADMIN").preferences())
                .isEmpty();

        commands.put(
                actor(PRINCIPAL_B, TENANT_A),
                "corr-b",
                "ADMIN",
                Map.of("theme", write(StringNode.valueOf("SYSTEM"), null)));
        assertThat(queries.get(actor(PRINCIPAL_A, TENANT_A), "corr-a", "ADMIN").preferences())
                .containsOnlyKeys("locale");
    }

    @Test
    void whitelistAndForbiddenKeysRejected() {
        assertThatThrownBy(() -> commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-bad-key",
                "ADMIN",
                Map.of("unknownKey", write(StringNode.valueOf("x"), null))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.UI_PREFERENCE_KEY_NOT_ALLOWED));

        assertThatThrownBy(() -> commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-forbid",
                "ADMIN",
                Map.of("disableSecurityConfirmations", write(BooleanNode.TRUE, null))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.UI_PREFERENCE_KEY_NOT_ALLOWED));

        assertThatThrownBy(() -> commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-bypass",
                "ADMIN",
                Map.of("bypassRedaction", write(BooleanNode.TRUE, null))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.UI_PREFERENCE_KEY_NOT_ALLOWED));
    }

    @Test
    void nonAdminPortalRejected() {
        assertThatThrownBy(() -> queries.get(actor(PRINCIPAL_A, TENANT_A), "corr-portal", "NETWORK"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));

        assertThatThrownBy(() -> commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-portal-put",
                "TECHNICIAN",
                Map.of("theme", write(StringNode.valueOf("DARK"), null))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void versionConflictAndStructuredKeys() {
        commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-v1",
                "ADMIN",
                Map.of("theme", write(StringNode.valueOf("DARK"), null)));

        assertThatThrownBy(() -> commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-conflict",
                "ADMIN",
                Map.of("theme", write(StringNode.valueOf("LIGHT"), 99L))))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VERSION_CONFLICT));

        String savedViewId = UUID.randomUUID().toString();
        UiPreferencesDocument structured = commands.put(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-struct",
                "ADMIN",
                Map.of(
                        "defaultSavedViews", write(JSON.readTree(
                                "{\"ADMIN.TASK.QUEUE\":\"" + savedViewId + "\",\"ADMIN.WORKORDER.LIST\":null}"), null),
                        "columnWidths", write(JSON.readTree(
                                "{\"schemaVersion\":1,\"pages\":{\"ADMIN.TASK.QUEUE\":{\"status\":160}}}"), null)
                ));
        assertThat(structured.preferences()).containsKeys("defaultSavedViews", "columnWidths");
    }

    private static UiPreferenceWrite write(tools.jackson.databind.JsonNode value, Long expectedVersion) {
        return new UiPreferenceWrite(value, 1, expectedVersion);
    }

    private static CurrentPrincipal actor(String principalId, String tenantId) {
        return new CurrentPrincipal(
                principalId,
                tenantId,
                CurrentPrincipal.PrincipalType.USER,
                "admin-web",
                Set.of());
    }
}
