package com.serviceos.identity;

import com.serviceos.ServiceOsApplication;
import com.serviceos.authorization.api.AuthorizationGovernanceCommandService;
import com.serviceos.identity.api.AuthenticatedIdentity;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalAuthenticationService;
import com.serviceos.identity.api.SecurityPrincipalCommandService;
import com.serviceos.identity.api.SecurityPrincipalQueryService;
import com.serviceos.organization.api.OrganizationCommandService;
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
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M183 统一主体目录的真实 PostgreSQL 验收证据。
 *
 * <p>这里刻意验证 advisory lock、唯一约束、聚合版本和停用后的实时认证路径；这些行为不能用
 * Mock 或 H2 代替，否则无法证明并发首次登录不会创建两个主体，也无法证明旧 JWT 会立即失效。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IdentityDirectoryPostgresIT {
    private static final String TENANT = "tenant-identity-test";
    private static final String CLIENT = "admin-web";
    private static final String ISSUER = "https://idp.example.com/realms/serviceos";
    private static final String ACTOR = "identity-admin";

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

    @Autowired
    PrincipalAuthenticationService authentication;

    @Autowired
    SecurityPrincipalCommandService commands;

    @Autowired
    SecurityPrincipalQueryService queries;

    @Autowired
    OrganizationCommandService organizations;

    @Autowired
    AuthorizationGovernanceCommandService authorizationGovernance;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void cleanAndAuthorizeActor() {
        jdbc.sql("""
                TRUNCATE TABLE org_structure_event, org_reassignment_work_item,
                    org_directory_sync_item, org_directory_sync_batch,
                    org_membership, org_unit_closure, org_unit, org_organization,
                    idn_principal_login_event, idn_principal_lifecycle_event, idn_principal_persona,
                    idn_identity_link, idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record,
                    auth_role_grant_event, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, 'identity-admin', 'Identity Admin', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenant", TENANT).update();
        for (String capability : List.of(
                "identity.read", "identity.readSensitive", "identity.manageLinks",
                "identity.manageLifecycle", "identity.manageProfile", "identity.register",
                "organization.read", "organization.manageStructure", "organization.manageMembership",
                "authorization.read", "authorization.manageRoles", "authorization.requestGrant",
                "authorization.approveGrant")) {
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
                    :grantId, :tenant, :actor, :roleId, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'm183-acceptance', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenant", TENANT)
                .param("actor", ACTOR).param("roleId", roleId).update();
    }

    @Test
    void concurrentFirstLoginCreatesOneStablePrincipal() throws Exception {
        AuthenticatedIdentity identity = identity("subject-concurrent", "并发用户");
        Callable<String> login = () -> authentication.resolveOrRegister(identity, "corr-jit-concurrent");

        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = executor.invokeAll(List.of(login, login, login, login, login, login));
            Set<String> principalIds = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
            }).collect(java.util.stream.Collectors.toSet());

            assertThat(principalIds).hasSize(1);
            assertThat(jdbc.sql("SELECT count(*) FROM idn_security_principal").query(Long.class).single())
                    .isEqualTo(1L);
            assertThat(jdbc.sql("SELECT count(*) FROM idn_identity_link").query(Long.class).single())
                    .isEqualTo(1L);
        }
    }

    @Test
    void secondIdentityResolvesSamePrincipalAndDisableRejectsOldJwtImmediately() {
        UUID principalId = UUID.fromString(authentication.resolveOrRegister(
                identity("subject-primary", "多身份用户"), "corr-primary"));
        CurrentPrincipal actor = actor();

        var linked = commands.linkIdentity(actor, metadata("link-second"), principalId, 1,
                "https://second-idp.example.com", "subject-secondary", "mobile-portal");
        assertThat(linked.version()).isEqualTo(2);
        String resolved = authentication.resolveOrRegister(new AuthenticatedIdentity(
                TENANT, "https://second-idp.example.com", "subject-secondary", "mobile-portal",
                CurrentPrincipal.PrincipalType.USER, "多身份用户"), "corr-secondary");
        assertThat(resolved).isEqualTo(principalId.toString());
        assertThat(queries.identities(actor, "corr-sensitive-read", principalId)).hasSize(2);

        var disabled = commands.disable(actor, metadata("disable"), principalId, 2, "离职停用");
        assertThat(disabled.status()).isEqualTo("DISABLED");
        assertThatThrownBy(() -> authentication.resolveOrRegister(
                identity("subject-primary", "多身份用户"), "corr-old-token"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void profileEmployeeNumberConflictReturnsExplicitConflictAndRollsBackVersion() {
        UUID first = UUID.fromString(authentication.resolveOrRegister(
                identity("subject-profile-a", "甲用户"), "corr-profile-a"));
        UUID second = UUID.fromString(authentication.resolveOrRegister(
                identity("subject-profile-b", "乙用户"), "corr-profile-b"));

        commands.updateProfile(actor(), metadata("profile-a"), first, 1, "甲用户", "EMP-001");
        assertThatThrownBy(() -> commands.updateProfile(
                actor(), metadata("profile-b"), second, 1, "乙用户", "EMP-001"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDENTITY_PROFILE_CONFLICT));

        assertThat(queries.get(actor(), "corr-read-second", second).principal().version()).isEqualTo(1);
    }

    @Test
    void successfulAuthenticationRecordsRecentLoginWithoutSubject() {
        UUID principalId = UUID.fromString(authentication.resolveOrRegister(
                identity("subject-login-a", "登录用户"), "corr-login-1"));
        authentication.resolveOrRegister(identity("subject-login-a", "登录用户"), "corr-login-2");

        var page = queries.recentLogins(actor(), "corr-login-list", principalId, 10);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().getFirst().clientId()).isEqualTo(CLIENT);
        assertThat(page.items().getFirst().authChannel()).isEqualTo("OIDC");
        assertThat(page.items().getFirst().outcome()).isEqualTo("SUCCEEDED");
        assertThat(page.items().getFirst().issuer()).isEqualTo(ISSUER);
        assertThat(jdbc.sql("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_name='idn_principal_login_event' AND column_name='subject'
                """).query(Long.class).single()).isZero();
    }

    @Test
    void changeTimelineMergesLifecycleAndLoginWithoutRedundantAudits() {
        UUID principalId = UUID.fromString(authentication.resolveOrRegister(
                identity("subject-timeline", "时间线用户"), "corr-timeline-1"));
        commands.updateProfile(actor(), metadata("timeline-profile"), principalId, 1, "时间线用户", "EMP-TL-1");
        authentication.resolveOrRegister(identity("subject-timeline", "时间线用户"), "corr-timeline-2");

        var timeline = queries.changeTimeline(actor(), "corr-timeline-list", principalId, 20);
        assertThat(timeline.items()).extracting(item -> item.source())
                .contains("LIFECYCLE", "LOGIN");
        assertThat(timeline.items()).extracting(item -> item.eventCode())
                .contains("REGISTERED", "PROFILE_UPDATED", "LOGIN_SUCCEEDED");
        assertThat(timeline.items()).noneMatch(item ->
                "PRINCIPAL_REGISTERED".equals(item.eventCode())
                        || "PRINCIPAL_LOGIN_SUCCEEDED".equals(item.eventCode()));
        assertThat(timeline.items().getFirst().summary()).isNotBlank();
        assertThat(timeline.omittedSources()).isEmpty();
        assertThat(timeline.items()).filteredOn(item -> "LOGIN_SUCCEEDED".equals(item.eventCode()))
                .isNotEmpty()
                .allSatisfy(item -> assertThat(item.actorDisplayName()).isEqualTo("时间线用户"));
    }

    @Test
    void changeTimelineMergesMembershipAndRoleGrantWithSoftOmit() {
        UUID principalId = UUID.fromString(authentication.resolveOrRegister(
                identity("subject-cross", "跨聚合用户"), "corr-cross-1"));
        commands.updateProfile(actor(), metadata("cross-profile"), principalId, 1, "跨聚合用户", "EMP-CROSS-1");
        seedApprover("grant-approver");
        // 审批者需具备不窄于目标 RoleGrant 的可授予范围。
        seedDirectActiveGrant("grant-approver", "identity.read", "TENANT", TENANT);

        var org = organizations.createOrganization(
                actor(), metadata("cross-org"), "CROSS", "跨聚合组织", "LOCAL", null, null);
        var unit = organizations.createUnit(
                actor(), metadata("cross-unit"), org.id(), org.version(), null, "OPS", "运营部");
        organizations.createMembership(
                actor(), metadata("cross-membership"), org.id(), unit.id(),
                principalId, "PRIMARY", Instant.now().minusSeconds(60));

        var role = authorizationGovernance.createRole(
                actor(), metadata("cross-role"), "CROSS-READER", "跨聚合读者", null,
                List.of("identity.read"));
        var pending = authorizationGovernance.requestRoleGrant(
                actor(), metadata("cross-grant-req"), principalId.toString(), role.roleId(),
                "TENANT", TENANT, "ALLOW", Instant.now().minusSeconds(60), null, "业务需要");
        authorizationGovernance.decideRoleGrant(
                approver(), metadata("cross-grant-approve"), pending.grantId(), pending.version(),
                "APPROVE", "批准");

        var timeline = queries.changeTimeline(actor(), "corr-cross-timeline", principalId, 50);
        assertThat(timeline.omittedSources()).isEmpty();
        assertThat(timeline.items()).extracting(item -> item.source())
                .contains("LIFECYCLE", "MEMBERSHIP", "ROLE_GRANT");
        assertThat(timeline.items()).extracting(item -> item.eventCode())
                .contains("MEMBERSHIP_CREATED", "ROLE_GRANT_REQUESTED", "ROLE_GRANT_APPROVED");
        assertThat(timeline.items()).filteredOn(item -> "MEMBERSHIP_CREATED".equals(item.eventCode()))
                .singleElement()
                .satisfies(item -> assertThat(item.summary()).contains("跨聚合组织").contains("运营部"));

        jdbc.sql("""
                DELETE FROM auth_role_capability
                 WHERE capability_code IN ('organization.read', 'authorization.read')
                   AND role_id IN (
                     SELECT role_id FROM auth_role
                      WHERE tenant_id = :tenant AND role_code = 'identity-admin'
                   )
                """).param("tenant", TENANT).update();
        var omitted = queries.changeTimeline(actor(), "corr-cross-omit", principalId, 50);
        assertThat(omitted.omittedSources()).containsExactly("MEMBERSHIP", "ROLE_GRANT");
        assertThat(omitted.items()).noneMatch(item ->
                "MEMBERSHIP".equals(item.source()) || "ROLE_GRANT".equals(item.source()));
        assertThat(omitted.items()).extracting(item -> item.source())
                .contains("LIFECYCLE");
    }

    private void seedApprover(String principalId) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, 'grant-approver', 'Grant Approver', 'ACTIVE', now())
                """).param("roleId", roleId).param("tenant", TENANT).update();
        for (String capability : List.of("authorization.approveGrant", "authorization.read")) {
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
                    :grantId, :tenant, :actor, :roleId, 'TENANT', :tenant,
                    now() - interval '1 day', 'TEST_FIXTURE', 'm415-approver', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenant", TENANT)
                .param("actor", principalId).param("roleId", roleId).update();
    }

    private void seedDirectActiveGrant(
            String principalId, String capability, String scopeType, String scopeRef
    ) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, :code, :code, 'ACTIVE', now())
                """)
                .param("roleId", roleId)
                .param("tenant", TENANT)
                .param("code", "direct-" + capability + "-" + UUID.randomUUID())
                .update();
        jdbc.sql("""
                INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                VALUES (:roleId, :capability, now())
                """).param("roleId", roleId).param("capability", capability).update();
        jdbc.sql("""
                INSERT INTO auth_role_grant (
                    grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                    valid_from, source_code, grant_status, grant_effect, created_at, updated_at
                ) VALUES (
                    :grantId, :tenant, :principal, :roleId, :scopeType, :scopeRef,
                    now() - interval '1 day', 'TEST_FIXTURE', 'ACTIVE', 'ALLOW', now(), now()
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

    private static CurrentPrincipal approver() {
        return new CurrentPrincipal("grant-approver", TENANT, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
    }

    @Test
    void adminRegisterCreatesPrincipalWithPersonaWithoutPassword() {
        var created = commands.register(
                actor(), metadata("register-user"), "登记用户", "EMP-REG-1", "INTERNAL_EMPLOYEE");
        assertThat(created.displayName()).isEqualTo("登记用户");
        assertThat(created.employeeNumber()).isEqualTo("EMP-REG-1");
        assertThat(created.status()).isEqualTo("ACTIVE");

        var detail = queries.get(actor(), "corr-register-read", created.id());
        assertThat(detail.personas()).extracting(item -> item.personaType())
                .contains("INTERNAL_EMPLOYEE");
        assertThat(jdbc.sql("""
                SELECT count(*) FROM idn_identity_link WHERE principal_id=:id
                """).param("id", created.id()).query(Long.class).single()).isZero();

        var replay = commands.register(
                actor(), metadata("register-user"), "登记用户", "EMP-REG-1", "INTERNAL_EMPLOYEE");
        assertThat(replay.id()).isEqualTo(created.id());

        assertThatThrownBy(() -> commands.register(
                actor(), metadata("register-dup"), "另一个", "EMP-REG-1", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.IDENTITY_PROFILE_CONFLICT));
    }

    @Test
    void unknownContextAndServicePrincipalFailClosed() {
        var unknownClient = new AuthenticatedIdentity(TENANT, ISSUER, "subject-other-client", "unknown-client",
                CurrentPrincipal.PrincipalType.USER, "未知客户端");
        var service = new AuthenticatedIdentity(TENANT, ISSUER, "service-subject", CLIENT,
                CurrentPrincipal.PrincipalType.SERVICE, "服务账号");

        assertThatThrownBy(() -> authentication.resolveOrRegister(unknownClient, "corr-unknown-client"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThatThrownBy(() -> authentication.resolveOrRegister(service, "corr-service"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    private static AuthenticatedIdentity identity(String subject, String displayName) {
        return new AuthenticatedIdentity(TENANT, ISSUER, subject, CLIENT,
                CurrentPrincipal.PrincipalType.USER, displayName);
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }
}
