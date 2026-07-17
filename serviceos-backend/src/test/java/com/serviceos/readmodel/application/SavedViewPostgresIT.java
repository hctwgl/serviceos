package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewCommandService;
import com.serviceos.readmodel.api.SavedViewFilterAst;
import com.serviceos.readmodel.api.SavedViewFilterClause;
import com.serviceos.readmodel.api.SavedViewPage;
import com.serviceos.readmodel.api.SavedViewQueryService;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M189 Admin 个人 SavedView：CRUD、租户隔离、未知筛选拒绝、schema 过期与主体所有权。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SavedViewPostgresIT {
    private static final String TENANT_A = "tenant-saved-a";
    private static final String TENANT_B = "tenant-saved-b";
    private static final String PRINCIPAL_A = "019f81a0-1111-7f8c-9505-36fe5c0e8801";
    private static final String PRINCIPAL_B = "019f81a0-2222-7f8c-9505-36fe5c0e8802";

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

    @Autowired SavedViewQueryService queries;
    @Autowired SavedViewCommandService commands;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("TRUNCATE TABLE rdm_saved_view CASCADE").update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("094");
        assertThat(flyway.info().applied()).hasSize(96);
    }

    @Test
    void crudListAndDefaultWithinPrincipal() {
        SavedView created = commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-create",
                "ADMIN.TASK.QUEUE",
                "我的 READY",
                1,
                filter("status", "READY"),
                null,
                null,
                true);
        assertThat(created.isDefault()).isTrue();
        assertThat(created.aggregateVersion()).isEqualTo(1L);

        SavedView second = commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-create-2",
                "ADMIN.TASK.QUEUE",
                "HUMAN 任务",
                1,
                filter("taskKind", "HUMAN"),
                null,
                List.of("id", "status"),
                true);
        assertThat(second.isDefault()).isTrue();

        SavedViewPage page = queries.list(actor(PRINCIPAL_A, TENANT_A), "corr-list", "ADMIN.TASK.QUEUE");
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().getFirst().name()).isEqualTo("HUMAN 任务");
        assertThat(page.items().stream().filter(SavedView::isDefault)).hasSize(1);

        SavedView updated = commands.update(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-update",
                second.id(),
                second.aggregateVersion(),
                "HUMAN 任务-改",
                1,
                filter("taskKind", "AUTOMATED"),
                null,
                List.of("id"),
                false);
        assertThat(updated.name()).isEqualTo("HUMAN 任务-改");
        assertThat(updated.aggregateVersion()).isEqualTo(2L);

        commands.delete(actor(PRINCIPAL_A, TENANT_A), "corr-del", updated.id());
        assertThat(queries.list(actor(PRINCIPAL_A, TENANT_A), "corr-list-2", "ADMIN.TASK.QUEUE").items())
                .extracting(SavedView::name)
                .containsExactly("我的 READY");
    }

    @Test
    void tenantAndPrincipalIsolation() {
        SavedView owned = commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-own",
                "ADMIN.WORKORDER.LIST",
                "ACTIVE 工单",
                1,
                filter("status", "ACTIVE"),
                null,
                null,
                false);

        assertThat(queries.list(actor(PRINCIPAL_B, TENANT_A), "corr-other", "ADMIN.WORKORDER.LIST").items())
                .isEmpty();
        assertThat(queries.list(actor(PRINCIPAL_A, TENANT_B), "corr-tenant", "ADMIN.WORKORDER.LIST").items())
                .isEmpty();

        assertThatThrownBy(() -> commands.update(
                actor(PRINCIPAL_B, TENANT_A),
                "corr-x",
                owned.id(),
                1L,
                "偷改",
                1,
                filter("status", "RECEIVED"),
                null,
                null,
                false))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));

        assertThatThrownBy(() -> commands.delete(actor(PRINCIPAL_A, TENANT_B), "corr-x2", owned.id()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.RESOURCE_NOT_FOUND));
    }

    @Test
    void unknownFilterRejected() {
        assertThatThrownBy(() -> commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-bad-filter",
                "ADMIN.CORRECTION.QUEUE",
                "坏筛选",
                1,
                new SavedViewFilterAst(List.of(new SavedViewFilterClause("secretField", "EQ", "x"))),
                null,
                null,
                false))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.QUERY_FILTER_NOT_ALLOWED));

        assertThatThrownBy(() -> commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-bad-op",
                "ADMIN.TASK.QUEUE",
                "坏操作符",
                1,
                new SavedViewFilterAst(List.of(new SavedViewFilterClause("status", "LIKE", "READY"))),
                null,
                null,
                false))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.QUERY_FILTER_NOT_ALLOWED));
    }

    @Test
    void schemaOutdatedOnUpdate() {
        SavedView created = commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-schema",
                "ADMIN.TASK.QUEUE",
                "旧视图",
                1,
                filter("status", "READY"),
                null,
                null,
                false);

        jdbc.sql("UPDATE rdm_saved_view SET schema_version = 99 WHERE saved_view_id = :id")
                .param("id", created.id())
                .update();

        assertThatThrownBy(() -> commands.update(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-outdated",
                created.id(),
                1L,
                "旧视图",
                1,
                filter("status", "CLAIMED"),
                null,
                null,
                false))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.SAVED_VIEW_SCHEMA_OUTDATED));
    }

    @Test
    void correctionQueueAndWorkOrderFiltersAccepted() {
        SavedView correction = commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-correction",
                "ADMIN.CORRECTION.QUEUE",
                "整改进行中",
                1,
                filter("status", "IN_PROGRESS"),
                null,
                null,
                false);
        assertThat(correction.pageId()).isEqualTo("ADMIN.CORRECTION.QUEUE");

        UUID projectId = UUID.fromString("019f81a0-3333-7f8c-9505-36fe5c0e8803");
        SavedView workOrders = commands.create(
                actor(PRINCIPAL_A, TENANT_A),
                "corr-wo",
                "ADMIN.WORKORDER.LIST",
                "项目工单",
                1,
                new SavedViewFilterAst(List.of(
                        new SavedViewFilterClause("status", "EQ", "ACTIVE"),
                        new SavedViewFilterClause("projectId", "EQ", projectId.toString()))),
                null,
                null,
                false);
        assertThat(workOrders.filter().clauses()).hasSize(2);
    }

    private static SavedViewFilterAst filter(String field, String value) {
        return new SavedViewFilterAst(List.of(new SavedViewFilterClause(field, "EQ", value)));
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
