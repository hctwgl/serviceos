package com.serviceos.authorization;

import com.serviceos.ServiceOsApplication;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.FieldAuthorizationRequest;
import com.serviceos.authorization.api.FieldAuthorizationService;
import com.serviceos.authorization.api.FieldPermission;
import com.serviceos.identity.api.CurrentPrincipal;
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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1-05、M6-SEC-001/002 的真实 PostgreSQL 权威授权证据。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuthorizationPolicyPostgresIT {
    private static final String TENANT = "tenant-auth-test";
    private static final String PRINCIPAL = "scope-user";

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
    AuthorizationService authorization;

    @Autowired
    FieldAuthorizationService fields;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @BeforeEach
    void cleanAuthorizationTables() {
        jdbc.sql("""
                TRUNCATE TABLE auth_role_field_policy, auth_field_policy_rule, auth_field_policy,
                    auth_role_grant, auth_role_capability, auth_role
                """).update();
    }

    @Test
    void projectGrantMatchesOnlyTheRequestedProjectAndNeverBecomesTenantWide() {
        seedRoleGrant("project-reader", PRINCIPAL, "PROJECT", "project-a");

        AuthorizationDecision matching = authorization.authorize(
                principal(PRINCIPAL),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-a", "project-a"),
                "corr-project-match");
        AuthorizationDecision otherProject = authorization.authorize(
                principal(PRINCIPAL),
                AuthorizationRequest.projectCapability(
                        "project.create", TENANT, "Project", "project-b", "project-b"),
                "corr-project-other");
        AuthorizationDecision tenantWide = authorization.authorize(
                principal(PRINCIPAL),
                AuthorizationRequest.tenantCapability(
                        "project.create", TENANT, "Project", "project-a"),
                "corr-project-tenant");

        assertThat(matching.effect()).isEqualTo(AuthorizationDecision.Effect.ALLOW);
        assertThat(matching.dataScopeExplanations()).containsExactly("PROJECT:project-a");
        assertThat(otherProject.effect()).isEqualTo(AuthorizationDecision.Effect.DENY);
        assertThat(tenantWide.effect()).isEqualTo(AuthorizationDecision.Effect.DENY);
    }

    @Test
    void regionAndNetworkGrantsRequireExactStableScopeReferences() {
        seedRoleGrant("region-reader", "region-user", "REGION", "CN-3702");
        seedRoleGrant("network-reader", "network-user", "NETWORK", "network-qingdao-a");

        assertThat(authorization.authorize(
                principal("region-user"),
                AuthorizationRequest.regionCapability(
                        "project.create", TENANT, "WorkOrder", "wo-1", "CN-3702"),
                "corr-region-match").effect()).isEqualTo(AuthorizationDecision.Effect.ALLOW);
        assertThat(authorization.authorize(
                principal("region-user"),
                AuthorizationRequest.regionCapability(
                        "project.create", TENANT, "WorkOrder", "wo-2", "CN-3703"),
                "corr-region-deny").effect()).isEqualTo(AuthorizationDecision.Effect.DENY);
        assertThat(authorization.authorize(
                principal("network-user"),
                AuthorizationRequest.networkCapability(
                        "project.create", TENANT, "WorkOrder", "wo-3", "network-qingdao-a"),
                "corr-network-match").dataScopeExplanations())
                .containsExactly("NETWORK:network-qingdao-a");
    }

    @Test
    void fieldPolicyMasksReadsAndDefaultsUnknownFieldsToHidden() {
        UUID roleId = seedRoleGrant("field-reader", PRINCIPAL, "PROJECT", "project-a");
        UUID policyId = seedPublishedFieldPolicy("project-field-read", "Project");
        assignPolicy(roleId, policyId);
        seedRule(policyId, "project.create", "name", "READ", null);
        seedRule(policyId, "project.create", "customerMobile", "MASKED", "CN_MOBILE");

        var decision = fields.evaluate(
                principal(PRINCIPAL),
                new FieldAuthorizationRequest(
                        AuthorizationRequest.projectCapability(
                                "project.create", TENANT, "Project", "project-a", "project-a"),
                        Set.of("name", "customerMobile", "settlementAmount")),
                "corr-field-policy");

        assertThat(decision.fields().get("name").permission()).isEqualTo(FieldPermission.READ);
        assertThat(decision.fields().get("customerMobile").permission()).isEqualTo(FieldPermission.MASKED);
        assertThat(decision.fields().get("customerMobile").maskCode()).isEqualTo("CN_MOBILE");
        assertThat(decision.fields().get("settlementAmount").permission()).isEqualTo(FieldPermission.HIDDEN);
        assertThat(decision.matchedGrantIds()).hasSize(1);
    }

    @Test
    void explicitHiddenRuleWinsAcrossMultipleMatchingRoles() {
        UUID readerRole = seedRoleGrant("field-reader", PRINCIPAL, "PROJECT", "project-a");
        UUID denyRole = seedRoleGrant("field-restriction", PRINCIPAL, "PROJECT", "project-a");
        UUID readerPolicy = seedPublishedFieldPolicy("project-reader-policy", "Project");
        UUID denyPolicy = seedPublishedFieldPolicy("project-deny-policy", "Project");
        assignPolicy(readerRole, readerPolicy);
        assignPolicy(denyRole, denyPolicy);
        seedRule(readerPolicy, "project.create", "settlementAmount", "EXPORT", null);
        seedRule(denyPolicy, "project.create", "settlementAmount", "HIDDEN", null);

        var decision = fields.evaluate(
                principal(PRINCIPAL),
                new FieldAuthorizationRequest(
                        AuthorizationRequest.projectCapability(
                                "project.create", TENANT, "Project", "project-a", "project-a"),
                        Set.of("settlementAmount")),
                "corr-explicit-hidden");

        assertThat(decision.fields().get("settlementAmount").permission())
                .isEqualTo(FieldPermission.HIDDEN);
        assertThat(decision.matchedGrantIds()).hasSize(2);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("054");
    }

    private UUID seedRoleGrant(String roleCode, String principalId, String scopeType, String scopeRef) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, created_at
                        ) VALUES (:roleId, :tenantId, :roleCode, :roleCode, 'ACTIVE', now())
                        """)
                .param("roleId", roleId)
                .param("tenantId", TENANT)
                .param("roleCode", roleCode)
                .update();
        jdbc.sql("""
                        INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                        VALUES (:roleId, 'project.create', now())
                        """)
                .param("roleId", roleId)
                .update();
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, approval_ref, created_at
                        ) VALUES (
                            :grantId, :tenantId, :principalId, :roleId, :scopeType, :scopeRef,
                            now() - interval '1 day', 'TEST_FIXTURE', 'test-approval', now()
                        )
                        """)
                .param("grantId", UUID.randomUUID())
                .param("tenantId", TENANT)
                .param("principalId", principalId)
                .param("roleId", roleId)
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef)
                .update();
        return roleId;
    }

    private UUID seedPublishedFieldPolicy(String policyCode, String resourceType) {
        UUID policyId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO auth_field_policy (
                            policy_id, tenant_id, policy_code, resource_type, policy_version,
                            policy_status, content_digest, published_at, created_at
                        ) VALUES (
                            :policyId, :tenantId, :policyCode, :resourceType, 1,
                            'PUBLISHED', :digest, now(), now()
                        )
                        """)
                .param("policyId", policyId)
                .param("tenantId", TENANT)
                .param("policyCode", policyCode)
                .param("resourceType", resourceType)
                .param("digest", "a".repeat(64))
                .update();
        return policyId;
    }

    private void assignPolicy(UUID roleId, UUID policyId) {
        jdbc.sql("""
                        INSERT INTO auth_role_field_policy (role_id, policy_id, assigned_at)
                        VALUES (:roleId, :policyId, now())
                        """)
                .param("roleId", roleId)
                .param("policyId", policyId)
                .update();
    }

    private void seedRule(
            UUID policyId,
            String capability,
            String fieldCode,
            String accessLevel,
            String maskCode
    ) {
        jdbc.sql("""
                        INSERT INTO auth_field_policy_rule (
                            policy_id, capability_code, field_code, access_level, mask_code
                        ) VALUES (:policyId, :capability, :fieldCode, :accessLevel, :maskCode)
                        """)
                .param("policyId", policyId)
                .param("capability", capability)
                .param("fieldCode", fieldCode)
                .param("accessLevel", accessLevel)
                .param("maskCode", maskCode, java.sql.Types.VARCHAR)
                .update();
    }

    private static CurrentPrincipal principal(String principalId) {
        return new CurrentPrincipal(
                principalId, TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of("project.create"));
    }
}
