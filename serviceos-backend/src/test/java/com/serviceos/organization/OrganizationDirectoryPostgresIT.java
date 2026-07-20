package com.serviceos.organization;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.AuthenticatedIdentity;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalAuthenticationService;
import com.serviceos.identity.api.PrincipalEmploymentLifecyclePort;
import com.serviceos.organization.api.DirectorySyncBatchView;
import com.serviceos.organization.api.OrganizationCommandService;
import com.serviceos.organization.api.OrganizationCommandService.SyncItemInput;
import com.serviceos.organization.api.OrganizationQueryService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M184 企业组织目录的真实 PostgreSQL 验收证据：closure、主职冲突、离职联动、外部权威与同步批次。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OrganizationDirectoryPostgresIT {
    private static final String TENANT = "tenant-org-test";
    private static final String CLIENT = "admin-web";
    private static final String ISSUER = "https://idp.example.com/realms/serviceos";
    private static final String ACTOR = "org-admin";
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

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
        registry.add("serviceos.identity.jit-registration.allowed-contexts", () -> TENANT + "|" + CLIENT);
    }

    @Autowired OrganizationCommandService commands;
    @Autowired OrganizationQueryService queries;
    @Autowired PrincipalAuthenticationService authentication;
    @Autowired PrincipalEmploymentLifecyclePort principalLifecycle;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void cleanAndAuthorizeActor() {
        jdbc.sql("""
                TRUNCATE TABLE org_structure_event, org_reassignment_work_item,
                    org_directory_sync_item, org_directory_sync_batch,
                    org_membership, org_unit_closure, org_unit, org_organization,
                    idn_principal_login_event, idn_principal_lifecycle_event, idn_principal_persona,
                    idn_identity_link, idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedRole(ACTOR, List.of(
                "organization.read", "organization.manageStructure", "organization.manageMembership",
                "organization.sync", "organization.overrideExternal",
                "identity.read", "identity.manageLifecycle"));
    }

    @Test
    void m184_01_multilevelOrgUnitClosureIsCorrectAndRejectsCycles() {
        var org = commands.createOrganization(actor(), metadata("create-org"), "ACME", "Acme Corp",
                "LOCAL", null, null);
        var root = commands.createUnit(actor(), metadata("root"), org.id(), org.version(),
                null, "ROOT", "Root");
        var child = commands.createUnit(actor(), metadata("child"), org.id(), org.version() + 1,
                root.id(), "ENG", "Engineering");
        var grandchild = commands.createUnit(actor(), metadata("grandchild"), org.id(), org.version() + 2,
                child.id(), "BE", "Backend");

        assertThat(jdbc.sql("""
                SELECT count(*) FROM org_unit_closure
                 WHERE tenant_id=:tenant AND ancestor_id=:root AND descendant_id=:grandchild AND depth=2
                """).param("tenant", TENANT).param("root", root.id()).param("grandchild", grandchild.id())
                .query(Long.class).single()).isEqualTo(1L);

        assertThatThrownBy(() -> commands.moveUnit(actor(), metadata("cycle"), org.id(), root.id(),
                root.version(), child.id()))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ORGANIZATION_UNIT_CYCLE));
    }

    @Test
    void m184_02_membershipTransferAndPrimaryConflict() {
        var org = commands.createOrganization(actor(), metadata("org"), "ACME", "Acme", "LOCAL", null, null);
        var unitA = commands.createUnit(actor(), metadata("unit-a"), org.id(), org.version(), null, "A", "Dept A");
        var unitB = commands.createUnit(actor(), metadata("unit-b"), org.id(), org.version() + 1,
                null, "B", "Dept B");
        UUID principal = seedPrincipal("employee-1", "员工一");

        var primary = commands.createMembership(actor(), metadata("primary"), org.id(), unitA.id(),
                principal, "PRIMARY", NOW);
        var secondary = commands.createMembership(actor(), metadata("secondary"), org.id(), unitA.id(),
                principal, "SECONDARY", NOW);
        var summaries = queries.listMembershipSummariesForPrincipal(
                actor(), "corr-summary", principal, "ACTIVE");
        assertThat(summaries.items()).extracting(item -> item.membershipType())
                .containsExactlyInAnyOrder("PRIMARY", "SECONDARY");
        assertThat(summaries.items()).allSatisfy(item -> {
            assertThat(item.organizationName()).isEqualTo("Acme");
            assertThat(item.unitName()).isEqualTo("Dept A");
        });

        assertThatThrownBy(() -> commands.createMembership(actor(), metadata("dup-primary"), org.id(),
                unitB.id(), principal, "PRIMARY", NOW))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ORGANIZATION_MEMBERSHIP_CONFLICT));

        var transferred = commands.transferMembership(actor(), metadata("transfer"), secondary.id(),
                secondary.version(), unitB.id(), "SECONDARY", NOW.plusSeconds(3600));
        assertThat(transferred.orgUnitId()).isEqualTo(unitB.id());
        assertThat(transferred.id()).isNotEqualTo(secondary.id());
        assertThat(queries.listMemberships(actor(), "read", org.id(), null, principal).items())
                .hasSize(3);
        assertThat(jdbc.sql("SELECT membership_status FROM org_membership WHERE membership_id=:id")
                .param("id", secondary.id()).query(String.class).single()).isEqualTo("TERMINATED");
    }

    @Test
    void m184_03_terminateMembershipDisablesPrincipalRevokesGrantsAndOpensWorkItem() {
        var org = commands.createOrganization(actor(), metadata("org"), "ACME", "Acme", "LOCAL", null, null);
        var unit = commands.createUnit(actor(), metadata("unit"), org.id(), org.version(), null, "HR", "HR");
        UUID principal = seedPrincipal("employee-leave", "离职员工");
        seedRoleGrant(principal.toString(), "tenant-worker");

        var membership = commands.createMembership(actor(), metadata("membership"), org.id(), unit.id(),
                principal, "PRIMARY", NOW);

        var terminated = commands.terminateMembership(actor(), metadata("terminate"), membership.id(),
                membership.version(), "离职", true);
        assertThat(terminated.status()).isEqualTo("TERMINATED");

        assertThat(jdbc.sql("SELECT principal_status FROM idn_security_principal WHERE principal_id=:id")
                .param("id", principal).query(String.class).single()).isEqualTo("DISABLED");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM auth_role_grant
                 WHERE tenant_id=:tenant AND principal_id=:principal AND revoked_at IS NOT NULL
                """).param("tenant", TENANT).param("principal", principal.toString()).query(Long.class).single())
                .isEqualTo(1L);
        assertThat(queries.listOpenReassignmentWorkItems(actor(), "work-items").items()).hasSize(1);
    }

    @Test
    void m184_04_externalAuthoritativeRejectsOrdinaryWriteUnlessOverride() {
        var org = commands.createOrganization(actor(), metadata("ext-org"), "EXT", "External Org",
                "EXTERNAL_AUTHORITATIVE", "HRIS", "ext-org-1");

        // 仅有结构管理能力、没有 override 的操作者不得改外部权威组织。
        seedRole("structure-only", List.of("organization.read", "organization.manageStructure"));
        CurrentPrincipal structureOnly = new CurrentPrincipal(
                "structure-only", TENANT, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
        assertThatThrownBy(() -> commands.createUnit(structureOnly, metadata("denied"), org.id(), org.version(),
                null, "DENIED", "Denied"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        // ACTOR 在 BeforeEach 已具备 organization.overrideExternal，可显式覆盖。
        var unit = commands.createUnit(actor(), metadata("override"), org.id(), org.version(),
                null, "OK", "Override OK");
        assertThat(unit.unitCode()).isEqualTo("OK");
    }

    @Test
    void m184_05_syncBatchIsIdempotentAndSkipsOutOfOrderVersions() {
        var org = commands.createOrganization(actor(), metadata("sync-org"), "SYNC", "Sync Org",
                "EXTERNAL_AUTHORITATIVE", "HRIS", "sync-org");
        UUID principal = seedPrincipal("sync-user", "同步用户");
        List<SyncItemInput> firstBatch = List.of(
                new SyncItemInput("UPSERT_UNIT", "unit-root", 2L, "ROOT", "Root", null, null, null, null),
                new SyncItemInput("UPSERT_UNIT", "unit-child", 3L, "CHILD", "Child", "unit-root", null, null, null),
                new SyncItemInput("UPSERT_MEMBERSHIP", "mem-1", 5L, null, null, "unit-child",
                        principal, "PRIMARY", NOW));

        DirectorySyncBatchView batch1 = commands.submitSyncBatch(actor(), metadata("batch-1"), org.id(),
                "HRIS", "batch-20260717", firstBatch);
        assertThat(batch1.batchStatus()).isEqualTo("COMPLETED");
        assertThat(batch1.successCount()).isEqualTo(3);

        DirectorySyncBatchView replay = commands.submitSyncBatch(actor(), metadata("batch-1"), org.id(),
                "HRIS", "batch-20260717", firstBatch);
        assertThat(replay.id()).isEqualTo(batch1.id());

        List<SyncItemInput> staleBatch = List.of(
                new SyncItemInput("UPSERT_UNIT", "unit-root", 1L, "ROOT", "Root Old", null, null, null, null));
        DirectorySyncBatchView batch2 = commands.submitSyncBatch(actor(), metadata("batch-2"), org.id(),
                "HRIS", "batch-stale", staleBatch);
        assertThat(batch2.skippedCount()).isEqualTo(1);
    }

    @Test
    void m184_06_moveUnitRebuildsClosureAtomically() {
        var org = commands.createOrganization(actor(), metadata("move-org"), "MOVE", "Move Org", "LOCAL", null, null);
        var root = commands.createUnit(actor(), metadata("root"), org.id(), org.version(), null, "R", "Root");
        var a = commands.createUnit(actor(), metadata("a"), org.id(), org.version() + 1, root.id(), "A", "A");
        var b = commands.createUnit(actor(), metadata("b"), org.id(), org.version() + 2, a.id(), "B", "B");

        commands.moveUnit(actor(), metadata("move-b"), org.id(), b.id(), b.version(), root.id());

        assertThat(jdbc.sql("""
                SELECT count(*) FROM org_unit_closure
                 WHERE tenant_id=:tenant AND ancestor_id=:root AND descendant_id=:b AND depth=1
                """).param("tenant", TENANT).param("root", root.id()).param("b", b.id())
                .query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM org_unit_closure
                 WHERE tenant_id=:tenant AND ancestor_id=:a AND descendant_id=:b
                """).param("tenant", TENANT).param("a", a.id()).param("b", b.id())
                .query(Long.class).single()).isEqualTo(0L);
    }

    private UUID seedPrincipal(String subject, String displayName) {
        return UUID.fromString(authentication.resolveOrRegister(
                new AuthenticatedIdentity(TENANT, ISSUER, subject, CLIENT,
                        CurrentPrincipal.PrincipalType.USER, displayName),
                "corr-" + subject));
    }

    private void seedRole(String principal, List<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, :code, :name, 'ACTIVE', now())
                """).param("roleId", roleId).param("tenant", TENANT)
                .param("code", principal + "-role").param("name", principal + "-role").update();
        for (String capability : capabilities) {
            jdbc.sql("""
                    INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                    VALUES (:roleId, :capability, now())
                    """).param("roleId", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grantId, :tenant, :principal, :roleId, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'm184', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principal).param("roleId", roleId).update();
    }

    private void seedRoleGrant(String principalId, String roleCode) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, :code, :name, 'ACTIVE', now())
                """).param("roleId", roleId).param("tenant", TENANT)
                .param("code", roleCode).param("name", roleCode).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, approval_ref, created_at
                ) VALUES (
                    :grantId, :tenant, :principal, :roleId, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST', 'worker', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principalId).param("roleId", roleId).update();
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }
}
