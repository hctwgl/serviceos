package com.serviceos.authorization;

import com.serviceos.ServiceOsApplication;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationGovernanceCommandService;
import com.serviceos.authorization.api.AuthorizationGovernanceQueryService;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.RoleGrantView;
import com.serviceos.authorization.api.RoleView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
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

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M186 角色与授权治理真实 PostgreSQL 证据：目录组合、SoD、可授予范围、撤销失效、DENY 优先与委托子集。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RoleGrantGovernancePostgresIT {
    private static final String TENANT = "tenant-auth-gov-test";
    private static final String GOVERNANCE_ADMIN = "gov-admin";
    private static final String REQUESTER = "grant-requester";
    private static final String APPROVER = "grant-approver";
    private static final String SUBJECT = "grant-subject";
    private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

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

    @Autowired AuthorizationGovernanceCommandService commands;
    @Autowired AuthorizationGovernanceQueryService queries;
    @Autowired AuthorizationService authorization;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        seedGovernanceActor(GOVERNANCE_ADMIN, List.of(
                "authorization.read", "authorization.manageRoles", "authorization.requestGrant",
                "authorization.approveGrant", "authorization.revokeGrant", "authorization.delegate",
                "authorization.explain", "project.create"));
        seedGovernanceActor(APPROVER, List.of(
                "authorization.approveGrant", "authorization.read", "project.create"));
        seedGovernanceActor(REQUESTER, List.of(
                "authorization.requestGrant", "authorization.approveGrant", "authorization.read"));
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("125");
        assertThat(flyway.info().applied()).hasSize(127);
    }

    @Test
    void m186_01_tenantRoleCanOnlyComposeCatalogCapabilities() {
        RoleView role = commands.createRole(actor(GOVERNANCE_ADMIN), metadata("role-1"),
                "OPS-READER", "Ops Reader", "目录组合", List.of("authorization.read", "project.create"));
        assertThat(role.roleKind()).isEqualTo("TENANT");
        assertThat(role.capabilityCodes()).containsExactly("authorization.read", "project.create");
        assertThat(queries.getRole(actor(GOVERNANCE_ADMIN), "corr-role", role.roleId()).capabilityCodes())
                .containsExactly("authorization.read", "project.create");

        assertThatThrownBy(() -> commands.createRole(actor(GOVERNANCE_ADMIN), metadata("role-bad"),
                "BAD-ROLE", "Bad", null, List.of("not.a.real.capability")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void m186_02_highRiskGrantRequiresSoDAndGrantableScope() {
        RoleView risky = commands.createRole(actor(GOVERNANCE_ADMIN), metadata("role-risky"),
                "RISKY", "Risky", null, List.of("authorization.manageRoles"));
        RoleGrantView pending = commands.requestRoleGrant(actor(REQUESTER), metadata("req-1"),
                SUBJECT, risky.roleId(), "TENANT", TENANT, "ALLOW",
                NOW.minusSeconds(60), null, "需要角色管理");

        assertThatThrownBy(() -> commands.decideRoleGrant(actor(REQUESTER), metadata("self-approve"),
                pending.grantId(), pending.version(), "APPROVE", "自批"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ROLE_GRANT_DUTY_CONFLICT));

        assertThatThrownBy(() -> commands.decideRoleGrant(actor(APPROVER), metadata("escalate"),
                pending.grantId(), pending.version(), "APPROVE", "越权批准"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.ROLE_GRANT_ESCALATION_FORBIDDEN));

        seedDirectActiveGrant(APPROVER, "authorization.manageRoles", "TENANT", TENANT);
        RoleGrantView approved = commands.decideRoleGrant(actor(APPROVER), metadata("approve-ok"),
                pending.grantId(), pending.version(), "APPROVE", "范围足够");
        assertThat(approved.grantStatus()).isEqualTo("ACTIVE");
        assertThat(approved.approvedBy()).isEqualTo(APPROVER);
    }

    @Test
    void m186_03_revokeInvalidatesAuthorizeAndBumpsGeneration() {
        RoleView role = commands.createRole(actor(GOVERNANCE_ADMIN), metadata("role-revoke"),
                "PROJECT-CREATOR", "Project Creator", null, List.of("project.create"));
        RoleGrantView pending = commands.requestRoleGrant(actor(REQUESTER), metadata("req-revoke"),
                SUBJECT, role.roleId(), "PROJECT", "project-a", "ALLOW",
                NOW.minusSeconds(60), null, "项目创建");
        seedDirectActiveGrant(APPROVER, "project.create", "TENANT", TENANT);
        RoleGrantView active = commands.decideRoleGrant(actor(APPROVER), metadata("approve-revoke"),
                pending.grantId(), pending.version(), "APPROVE", "同意");

        AuthorizationDecision before = authorization.authorize(
                actor(SUBJECT),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-a", "project-a"),
                "corr-before");
        assertThat(before.effect()).isEqualTo(AuthorizationDecision.Effect.ALLOW);
        long generationBefore = jdbc.sql("""
                        SELECT generation FROM auth_tenant_grant_generation WHERE tenant_id=:tenant
                        """)
                .param("tenant", TENANT).query(Long.class).single();

        RoleGrantView revoked = commands.revokeRoleGrant(actor(GOVERNANCE_ADMIN), metadata("revoke"),
                active.grantId(), active.version(), "岗位调整");
        assertThat(revoked.grantStatus()).isEqualTo("REVOKED");

        AuthorizationDecision after = authorization.authorize(
                actor(SUBJECT),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-a", "project-a"),
                "corr-after");
        assertThat(after.effect()).isEqualTo(AuthorizationDecision.Effect.DENY);
        long generationAfter = jdbc.sql("""
                        SELECT generation FROM auth_tenant_grant_generation WHERE tenant_id=:tenant
                        """)
                .param("tenant", TENANT).query(Long.class).single();
        assertThat(generationAfter).isGreaterThan(generationBefore);
        assertThat(after.policyVersion()).isEqualTo("role-grant-v3:g" + generationAfter);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM auth_role_grant_event
                         WHERE tenant_id=:tenant AND event_type='ROLE_GRANT_REVOKED'
                        """)
                .param("tenant", TENANT).query(Long.class).single()).isPositive();
    }

    @Test
    void m186_04_multipleAllowsUnionAndDenyPreferWins() {
        RoleView allowRole = commands.createRole(actor(GOVERNANCE_ADMIN), metadata("role-or"),
                "OR-ALLOW", "OR Allow", null, List.of("project.create"));
        RoleView denyRole = commands.createRole(actor(GOVERNANCE_ADMIN), metadata("role-deny"),
                "OR-DENY", "OR Deny", null, List.of("project.create"));
        seedDirectActiveGrant(SUBJECT, "project.create", "PROJECT", "project-a");
        seedDirectActiveGrant(SUBJECT, "project.create", "PROJECT", "project-b");

        AuthorizationDecision projectA = authorization.authorize(
                actor(SUBJECT),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-a", "project-a"),
                "corr-or-a");
        AuthorizationDecision projectB = authorization.authorize(
                actor(SUBJECT),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-b", "project-b"),
                "corr-or-b");
        assertThat(projectA.effect()).isEqualTo(AuthorizationDecision.Effect.ALLOW);
        assertThat(projectB.effect()).isEqualTo(AuthorizationDecision.Effect.ALLOW);

        UUID denyGrantId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, grant_status, grant_effect, created_at, updated_at
                        ) VALUES (
                            :grantId, :tenant, :principal, :roleId, 'PROJECT', 'project-a',
                            now() - interval '1 day', 'TEST_FIXTURE', 'ACTIVE', 'DENY', now(), now()
                        )
                        """)
                .param("grantId", denyGrantId)
                .param("tenant", TENANT)
                .param("principal", SUBJECT)
                .param("roleId", denyRole.roleId())
                .update();

        AuthorizationDecision denied = authorization.authorize(
                actor(SUBJECT),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-a", "project-a"),
                "corr-deny");
        assertThat(denied.effect()).isEqualTo(AuthorizationDecision.Effect.DENY);
        assertThat(allowRole.roleId()).isNotNull();
    }

    @Test
    void m186_05_delegationMustBeSubsetAndSynthesizesRuntimeGrant() {
        seedDirectActiveGrant(GOVERNANCE_ADMIN, "project.create", "TENANT", TENANT);

        assertThatThrownBy(() -> commands.createDelegation(actor(GOVERNANCE_ADMIN), metadata("del-bad"),
                SUBJECT, List.of("network.managePartner"), "TENANT", TENANT,
                NOW.minusSeconds(60), null, "越权委托"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code())
                                .isEqualTo(ProblemCode.DELEGATION_SCOPE_TOO_BROAD));

        Instant validFrom = Instant.now().minusSeconds(60);
        var delegation = commands.createDelegation(actor(GOVERNANCE_ADMIN), metadata("del-ok"),
                SUBJECT, List.of("project.create"), "PROJECT", "project-del",
                validFrom, null, "临时代管");
        assertThat(delegation.delegationStatus()).isEqualTo("ACTIVE");

        AuthorizationDecision decision = authorization.authorize(
                actor(SUBJECT),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-del", "project-del"),
                "corr-delegation");
        assertThat(decision.effect()).isEqualTo(AuthorizationDecision.Effect.ALLOW);
        assertThat(decision.matchedGrantIds()).anyMatch(id -> id.startsWith("delegation:"));
    }

    private void seedGovernanceActor(String principalId, List<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, role_kind,
                            aggregate_version, created_at, updated_at
                        ) VALUES (
                            :roleId, :tenant, :code, :code, 'ACTIVE', 'TENANT', 1, now(), now()
                        )
                        """)
                .param("roleId", roleId)
                .param("tenant", TENANT)
                .param("code", "seed-" + principalId)
                .update();
        for (String capability : capabilities) {
            jdbc.sql("""
                            INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                            VALUES (:roleId, :capability, now())
                            """)
                    .param("roleId", roleId)
                    .param("capability", capability)
                    .update();
        }
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, grant_status, grant_effect,
                            aggregate_version, created_at, updated_at
                        ) VALUES (
                            :grantId, :tenant, :principal, :roleId, 'TENANT', :tenant,
                            now() - interval '1 day', 'TEST_FIXTURE', 'ACTIVE', 'ALLOW',
                            1, now(), now()
                        )
                        """)
                .param("grantId", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("roleId", roleId)
                .update();
    }

    private void seedDirectActiveGrant(
            String principalId, String capability, String scopeType, String scopeRef
    ) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, role_kind,
                            aggregate_version, created_at, updated_at
                        ) VALUES (
                            :roleId, :tenant, :code, :code, 'ACTIVE', 'TENANT', 1, now(), now()
                        )
                        """)
                .param("roleId", roleId)
                .param("tenant", TENANT)
                .param("code", "direct-" + principalId + "-" + UUID.randomUUID())
                .update();
        jdbc.sql("""
                        INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                        VALUES (:roleId, :capability, now())
                        """)
                .param("roleId", roleId)
                .param("capability", capability)
                .update();
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, grant_status, grant_effect,
                            aggregate_version, created_at, updated_at
                        ) VALUES (
                            :grantId, :tenant, :principal, :roleId, :scopeType, :scopeRef,
                            now() - interval '1 day', 'TEST_FIXTURE', 'ACTIVE', 'ALLOW',
                            1, now(), now()
                        )
                        """)
                .param("grantId", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("roleId", roleId)
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef)
                .update();
    }

    private static CurrentPrincipal actor(String principalId) {
        return new CurrentPrincipal(principalId, TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, "idem-" + key);
    }
}
