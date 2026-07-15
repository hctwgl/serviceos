package com.serviceos.authorization;

import com.serviceos.ServiceOsApplication;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.authorization.api.FieldAuthorizationRequest;
import com.serviceos.authorization.api.FieldAuthorizationService;
import com.serviceos.authorization.api.FieldPermission;
import com.serviceos.authorization.api.ProjectScopeAuthorizationService;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    ProjectScopeAuthorizationService projectScopes;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Flyway flyway;

    @BeforeEach
    void cleanAuthorizationTables() {
        jdbc.sql("""
                TRUNCATE TABLE prj_project_network, prj_project_region, prj_project, aud_audit_record,
                    auth_role_field_policy, auth_field_policy_rule, auth_field_policy,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("068");
    }

    @Test
    void projectCollectionUnionsLiveProjectsAndTenantGrantOverridesTheSet() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        seedRoleGrant("scope-first", PRINCIPAL, "PROJECT", first.toString());
        seedRoleGrant("scope-second", PRINCIPAL, "PROJECT", second.toString());

        var explicit = projectScopes.require(
                principal(PRINCIPAL), "project.create", "Project", "corr-project-scope");
        assertThat(explicit.tenantWide()).isFalse();
        assertThat(explicit.projectIds()).containsExactlyInAnyOrder(first, second);

        seedRoleGrant("scope-tenant", PRINCIPAL, "TENANT", TENANT);
        var tenantWide = projectScopes.require(
                principal(PRINCIPAL), "project.create", "Project", "corr-tenant-scope");
        assertThat(tenantWide.tenantWide()).isTrue();
        assertThat(tenantWide.projectIds()).isEmpty();
        assertThat(tenantWide.scopeDigest()).isNotEqualTo(explicit.scopeDigest());
    }

    @Test
    void regionScopeResolvesOnlyEffectiveTenantProjectsAndMissingGrantFailsClosed() {
        Instant now = Instant.now();
        UUID matching = seedProjectRegion(TENANT, "CN-3702", now.minusSeconds(3600), null);
        seedProjectRegion(TENANT, "CN-3703", now.minusSeconds(3600), null);
        seedProjectRegion("tenant-other", "CN-3702", now.minusSeconds(3600), null);
        seedProjectRegion(TENANT, "CN-3702", now.plusSeconds(3600), null);
        seedRoleGrant("scope-region", "region-only", "REGION", "CN-3702");

        var scope = projectScopes.require(
                principal("region-only"), "project.create", "Project", "corr-region-scope");
        assertThat(scope.tenantWide()).isFalse();
        assertThat(scope.projectIds()).containsExactly(matching);
        assertThatThrownBy(() -> projectScopes.require(
                principal("missing"), "project.create", "Project", "corr-missing-scope"))
                .isInstanceOf(com.serviceos.shared.BusinessProblem.class);
        assertThat(jdbc.sql("""
                SELECT error_code FROM aud_audit_record
                 WHERE action_name='AUTHORIZATION_DENIED' ORDER BY occurred_at
                """).query(String.class).list())
                .containsExactly("PROJECT_SCOPE_MISSING");
    }

    @Test
    void networkScopeResolvesOnlyEffectiveTenantProjectsAndMissingMappingFailsClosed() {
        Instant now = Instant.now();
        UUID matching = seedProjectNetwork(TENANT, "network-qingdao-a", now.minusSeconds(3600), null);
        seedProjectNetwork(TENANT, "network-jinan-a", now.minusSeconds(3600), null);
        seedProjectNetwork("tenant-other", "network-qingdao-a", now.minusSeconds(3600), null);
        seedProjectNetwork(TENANT, "network-qingdao-a", now.plusSeconds(3600), null);
        seedRoleGrant("scope-network", "network-only", "NETWORK", "network-qingdao-a");
        UUID explicitProject = UUID.randomUUID();
        seedRoleGrant("scope-network-project", "network-only", "PROJECT", explicitProject.toString());

        var scope = projectScopes.require(
                principal("network-only"), "project.create", "Project", "corr-network-scope");
        assertThat(scope.tenantWide()).isFalse();
        assertThat(scope.projectIds()).containsExactlyInAnyOrder(matching, explicitProject);

        seedRoleGrant("scope-empty-network", "empty-network", "NETWORK", "network-missing");
        assertThatThrownBy(() -> projectScopes.require(
                principal("empty-network"), "project.create", "Project", "corr-empty-network"))
                .isInstanceOf(com.serviceos.shared.BusinessProblem.class);
        assertThat(jdbc.sql("SELECT error_code FROM aud_audit_record WHERE action_name='AUTHORIZATION_DENIED'")
                .query(String.class).single()).isEqualTo("PROJECT_SCOPE_MISSING");
    }

    @Test
    void regionWithoutMatchingProjectFailsClosedAndCrossTenantBindingIsRejected() {
        seedRoleGrant("scope-empty-region", "empty-region", "REGION", "CN-9999");

        assertThatThrownBy(() -> projectScopes.require(
                principal("empty-region"), "project.create", "Project", "corr-empty-region"))
                .isInstanceOf(com.serviceos.shared.BusinessProblem.class);
        assertThat(jdbc.sql("SELECT error_code FROM aud_audit_record WHERE action_name='AUTHORIZATION_DENIED'")
                .query(String.class).single()).isEqualTo("PROJECT_SCOPE_MISSING");

        UUID otherTenantProject = seedProjectRegion("tenant-other", "CN-3702", Instant.now(), null);
        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO prj_project_region (
                            project_region_id, tenant_id, project_id, region_code,
                            valid_from, created_by, created_at)
                        VALUES (:id, :tenantId, :projectId, 'CN-3702', now(), 'test', now())
                        """)
                .param("id", UUID.randomUUID()).param("tenantId", TENANT)
                .param("projectId", otherTenantProject).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        assertThatThrownBy(() -> jdbc.sql("""
                        INSERT INTO prj_project_network (
                            project_network_id, tenant_id, project_id, network_id,
                            valid_from, created_by, created_at)
                        VALUES (:id, :tenantId, :projectId, 'network-qingdao-a', now(), 'test', now())
                        """)
                .param("id", UUID.randomUUID()).param("tenantId", TENANT)
                .param("projectId", otherTenantProject).update())
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private UUID seedProjectRegion(String tenantId, String regionCode, Instant validFrom, Instant validTo) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO prj_project (
                            project_id, tenant_id, project_code, client_id, project_name,
                            starts_on, project_status, aggregate_version, created_at
                        ) VALUES (
                            :projectId, :tenantId, :projectCode, 'client', 'region project',
                            current_date, 'DRAFT', 1, now()
                        )
                        """)
                .param("projectId", projectId)
                .param("tenantId", tenantId)
                .param("projectCode", "REGION-" + projectId)
                .update();
        jdbc.sql("""
                        INSERT INTO prj_project_region (
                            project_region_id, tenant_id, project_id, region_code,
                            valid_from, valid_to, created_by, created_at
                        ) VALUES (
                            :bindingId, :tenantId, :projectId, :regionCode,
                            :validFrom, :validTo, 'test', now()
                        )
                        """)
                .param("bindingId", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("regionCode", regionCode)
                .param("validFrom", java.time.OffsetDateTime.ofInstant(validFrom, java.time.ZoneOffset.UTC))
                .param("validTo", validTo == null ? null
                        : java.time.OffsetDateTime.ofInstant(validTo, java.time.ZoneOffset.UTC),
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
        return projectId;
    }

    private UUID seedProjectNetwork(String tenantId, String networkId, Instant validFrom, Instant validTo) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO prj_project (
                            project_id, tenant_id, project_code, client_id, project_name,
                            starts_on, project_status, aggregate_version, created_at
                        ) VALUES (
                            :projectId, :tenantId, :projectCode, 'client', 'network project',
                            current_date, 'DRAFT', 1, now()
                        )
                        """)
                .param("projectId", projectId)
                .param("tenantId", tenantId)
                .param("projectCode", "NETWORK-" + projectId)
                .update();
        jdbc.sql("""
                        INSERT INTO prj_project_network (
                            project_network_id, tenant_id, project_id, network_id,
                            valid_from, valid_to, created_by, created_at
                        ) VALUES (
                            :bindingId, :tenantId, :projectId, :networkId,
                            :validFrom, :validTo, 'test', now()
                        )
                        """)
                .param("bindingId", UUID.randomUUID())
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("networkId", networkId)
                .param("validFrom", java.time.OffsetDateTime.ofInstant(validFrom, java.time.ZoneOffset.UTC))
                .param("validTo", validTo == null ? null
                        : java.time.OffsetDateTime.ofInstant(validTo, java.time.ZoneOffset.UTC),
                        java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                .update();
        return projectId;
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
