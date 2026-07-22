package com.serviceos.authorization;

import com.serviceos.ServiceOsApplication;
import com.serviceos.authorization.api.MeCapabilitiesView;
import com.serviceos.authorization.api.MeContextsView;
import com.serviceos.authorization.api.MeNavigationView;
import com.serviceos.authorization.api.MeProfileView;
import com.serviceos.authorization.api.PortalContextQueryService;
import com.serviceos.identity.api.CurrentPrincipal;
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

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M188 Portal 上下文真实 PostgreSQL 证据：多 Persona、网点切换、撤权版本失效、隐藏 page、伪造 context。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PortalContextPostgresIT {
    private static final String TENANT = "tenant-portal-me";
    private static final UUID PRINCIPAL = UUID.fromString("019f80a0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID NETWORK_A = UUID.fromString("019f80a0-2222-7f8c-9505-36fe5c0e8802");
    private static final UUID NETWORK_B = UUID.fromString("019f80a0-3333-7f8c-9505-36fe5c0e8803");
    private static final UUID PARTNER = UUID.fromString("019f80a0-4444-7f8c-9505-36fe5c0e8804");
    private static final UUID TECH_PROFILE = UUID.fromString("019f80a0-5555-7f8c-9505-36fe5c0e8805");

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

    @Autowired PortalContextQueryService portal;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE auth_page_registry_override, auth_feature_gate,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("149");
        assertThat(flyway.info().applied()).hasSize(151);

        seedPrincipal();
        seedPersona("INTERNAL_EMPLOYEE");
        seedPersona("NETWORK_MEMBER");
        seedPersona("TECHNICIAN");
        seedPersona("CONSUMER");
        seedPartnerAndNetworks();
        seedNetworkMembership(NETWORK_A);
        seedNetworkMembership(NETWORK_B);
        seedTechnician(NETWORK_A);
        seedGrant(PRINCIPAL.toString(), "identity.read", "TENANT", TENANT);
        seedGrant(PRINCIPAL.toString(), "authorization.read", "TENANT", TENANT);
        seedGrant(PRINCIPAL.toString(), "project.read", "TENANT", TENANT);
        seedGrant(PRINCIPAL.toString(), "networkTask.read", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL.toString(), "technician.readOwnNetwork", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL.toString(), "task.readAssigned", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL.toString(), "networkTask.read", "NETWORK", NETWORK_B.toString());
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void m188_01_multiPersonaContextsExcludeConsumer() {
        MeProfileView me = portal.me(actor(), "corr-me");
        assertThat(me.personas()).extracting(p -> p.personaType())
                .contains("INTERNAL_EMPLOYEE", "NETWORK_MEMBER", "TECHNICIAN", "CONSUMER");

        MeContextsView contexts = portal.contexts(actor(), "corr-contexts");
        assertThat(contexts.contexts()).extracting(c -> c.portal())
                .contains("ADMIN", "NETWORK", "TECHNICIAN");
        assertThat(contexts.contexts()).noneMatch(c -> "CONSUMER".equals(c.personaType()));
        assertThat(contexts.contexts()).noneMatch(c -> c.portal().equals("EXTERNAL"));
        assertThat(contexts.contextVersion()).startsWith("role-grant-v3:g");
    }

    @Test
    void m188_02_forgedContextAndNetworkSwitchFailClosed() {
        MeContextsView contexts = portal.contexts(actor(), "corr-switch");
        String networkA = "NETWORK|NETWORK|" + NETWORK_A;
        String networkB = "NETWORK|NETWORK|" + NETWORK_B;
        assertThat(contexts.contexts()).extracting(c -> c.contextId()).contains(networkA, networkB);

        MeNavigationView navA = portal.navigation(actor(), "corr-nav-a", networkA, null);
        assertThat(navA.navigationCatalogVersion()).isEqualTo("page-registry-v22");
        assertThat(navA.items()).extracting(i -> i.pageId())
                .contains("NETWORK.TASK.QUEUE", "NETWORK.CAPACITY", "NETWORK.WORKORDER.WORKSPACE");

        assertThatThrownBy(() -> portal.navigation(actor(), "corr-forged",
                "NETWORK|NETWORK|" + UUID.randomUUID(), null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        assertThatThrownBy(() -> portal.capabilities(actor(), "corr-forged-cap",
                "ADMIN|TENANT|other-tenant", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void m188_03_navigationUsesPageRegistryAndCapabilityGate() {
        String adminContext = "ADMIN|TENANT|" + TENANT;
        MeNavigationView nav = portal.navigation(actor(), "corr-nav", adminContext, null);
        assertThat(nav.navigationCatalogVersion()).isEqualTo("page-registry-v22");
        assertThat(nav.items()).extracting(i -> i.pageId())
                .contains("ADMIN.USER.DIRECTORY", "ADMIN.GRANT.DIRECTORY", "ADMIN.MASTERDATA.CATALOG");
        assertThat(nav.items()).noneMatch(i -> i.pageId().startsWith("NETWORK."));
        assertThat(nav.items()).noneMatch(i -> i.pageId().startsWith("TECHNICIAN."));

        jdbc.sql("""
                INSERT INTO auth_page_registry_override (
                    tenant_id, page_id, enabled, title_override, sort_order, feature_gate, updated_at
                ) VALUES (:tenant, 'ADMIN.USER.DIRECTORY', FALSE, NULL, NULL, NULL, now())
                """).param("tenant", TENANT).update();
        MeNavigationView hidden = portal.navigation(actor(), "corr-hidden", adminContext, null);
        assertThat(hidden.items()).noneMatch(i -> "ADMIN.USER.DIRECTORY".equals(i.pageId()));
    }

    @Test
    void m188_05_grantRevokeBumpsContextVersionAndStaleFails() {
        String adminContext = "ADMIN|TENANT|" + TENANT;
        MeCapabilitiesView before = portal.capabilities(actor(), "corr-before", adminContext, null);
        assertThat(before.capabilityCodes()).contains("identity.read");
        String oldVersion = before.contextVersion();

        jdbc.sql("""
                UPDATE auth_role_grant
                   SET grant_status='REVOKED', revoked_at=now(), revoked_by='test',
                       revoke_reason='m188 revoke',
                       aggregate_version = aggregate_version + 1, updated_at=now()
                 WHERE tenant_id=:tenant AND principal_id=:principal
                   AND scope_type='TENANT'
                """)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL.toString())
                .update();
        jdbc.sql("""
                UPDATE auth_tenant_grant_generation
                   SET generation = generation + 1, updated_at = now()
                 WHERE tenant_id=:tenant
                """).param("tenant", TENANT).update();

        assertThatThrownBy(() -> portal.navigation(actor(), "corr-stale", adminContext, oldVersion))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VERSION_CONFLICT));

        MeContextsView after = portal.contexts(actor(), "corr-after");
        assertThat(after.contextVersion()).isNotEqualTo(oldVersion);
        assertThat(after.contexts()).noneMatch(c -> "ADMIN".equals(c.portal()));
    }

    private void seedPrincipal() {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'USER', 'ACTIVE', 1, now(), now())
                """).param("id", PRINCIPAL).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO idn_person_profile (
                    principal_id, tenant_id, display_name, employee_number,
                    profile_version, created_at, updated_at, updated_by
                ) VALUES (:id, :tenant, 'Portal User', 'P-188', 1, now(), now(), 'test')
                """).param("id", PRINCIPAL).param("tenant", TENANT).update();
    }

    private void seedPersona(String type) {
        jdbc.sql("""
                INSERT INTO idn_principal_persona (
                    persona_id, tenant_id, principal_id, persona_type, persona_status,
                    valid_from, valid_to, persona_version, created_by, created_at
                ) VALUES (
                    :id, :tenant, :principal, :type, 'ACTIVE',
                    now() - interval '1 day', NULL, 1, 'test', now()
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL)
                .param("type", type)
                .update();
    }

    private void seedPartnerAndNetworks() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, 'P-188', 'Partner 188', 'ACTIVE', 1, now(), now()
                )
                """).param("id", PARTNER).param("tenant", TENANT).update();
        for (UUID networkId : new UUID[]{NETWORK_A, NETWORK_B}) {
            jdbc.sql("""
                    INSERT INTO net_service_network (
                        service_network_id, tenant_id, partner_organization_id, network_code,
                        network_name, network_status, aggregate_version, created_at, updated_at
                    ) VALUES (
                        :id, :tenant, :partner, :code, :name, 'ACTIVE', 1, now(), now()
                    )
                    """)
                    .param("id", networkId)
                    .param("tenant", TENANT)
                    .param("partner", PARTNER)
                    .param("code", "N-" + networkId.toString().substring(24))
                    .param("name", "Network " + networkId)
                    .update();
        }
    }

    private void seedNetworkMembership(UUID networkId) {
        jdbc.sql("""
                INSERT INTO net_network_membership (
                    membership_id, tenant_id, service_network_id, principal_id, membership_role,
                    membership_status, valid_from, invited_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :principal, 'STAFF',
                    'ACTIVE', now() - interval '1 day', 'test', now(), 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("principal", PRINCIPAL)
                .update();
    }

    private void seedTechnician(UUID networkId) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :principal, 'Tech User', 'ACTIVE', 1, now(), now()
                )
                """)
                .param("id", TECH_PROFILE)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL)
                .update();
        jdbc.sql("""
                INSERT INTO net_network_technician_membership (
                    membership_id, tenant_id, service_network_id, technician_profile_id,
                    membership_status, valid_from, created_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :profile,
                    'ACTIVE', now() - interval '1 day', 'test', now(), 1
                )
                """)
                .param("id", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("profile", TECH_PROFILE)
                .update();
    }

    private void seedGrant(String principalId, String capability, String scopeType, String scopeRef) {
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
                .param("code", "me-" + capability + "-" + UUID.randomUUID())
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

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(PRINCIPAL.toString(), TENANT, CurrentPrincipal.PrincipalType.USER,
                "admin-web", Set.of());
    }
}
