package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.NetworkPortalMembershipItem;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQueryService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M206 Network Portal 师傅关系只读列表：本网点隔离、过滤、version、get 允许/拒绝、伪造上下文、缺能力。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalTechnicianMembershipPostgresIT {
    private static final String TENANT = "tenant-network-portal-m206";
    private static final UUID PRINCIPAL = UUID.fromString("019f86d0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_PRINCIPAL = UUID.fromString("019f86d0-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NETWORK_A = UUID.fromString("019f86d0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f86d0-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f86d0-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID TECH_PROFILE_A = UUID.fromString("019f86d0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PRINCIPAL_A = UUID.fromString("019f86d0-6666-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_PROFILE_B = UUID.fromString("019f86d0-5556-7f8c-9505-36fe5c0e8808");
    private static final UUID TECH_PRINCIPAL_B = UUID.fromString("019f86d0-6667-7f8c-9505-36fe5c0e8809");
    private static final UUID MEM_A_ACTIVE = UUID.fromString("019f86d0-7777-7f8c-9505-36fe5c0e8801");
    private static final UUID MEM_A_TERMINATED = UUID.fromString("019f86d0-7778-7f8c-9505-36fe5c0e8802");
    private static final UUID MEM_B = UUID.fromString("019f86d0-7779-7f8c-9505-36fe5c0e8803");
    private static final Instant CREATED_A_ACTIVE = Instant.parse("2026-07-17T10:00:00Z");
    private static final Instant CREATED_A_TERMINATED = Instant.parse("2026-07-17T09:00:00Z");
    private static final Instant CREATED_B = Instant.parse("2026-07-17T08:00:00Z");

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
        registry.add("serviceos.outbox.scheduling-enabled", () -> "false");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
    }

    @Autowired NetworkPortalQueryService portal;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void cleanAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization, net_directory_event, net_clearance_work_item,
                    idn_principal_lifecycle_event, idn_principal_persona, idn_identity_link,
                    idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("151");
        assertThat(flyway.info().applied()).hasSize(153);

        seedPrincipal(PRINCIPAL, "Portal Member A");
        seedPrincipal(OTHER_PRINCIPAL, "Portal Member B");
        seedPrincipal(TECH_PRINCIPAL_A, "Technician A");
        seedPrincipal(TECH_PRINCIPAL_B, "Technician B");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPersona(OTHER_PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedNetworkMembership(OTHER_PRINCIPAL, NETWORK_B);
        seedTechnicianProfile(TECH_PROFILE_A, TECH_PRINCIPAL_A);
        seedTechnicianProfile(TECH_PROFILE_B, TECH_PRINCIPAL_B);
        seedTechnicianMembership(MEM_A_ACTIVE, TECH_PROFILE_A, NETWORK_A, "ACTIVE",
                CREATED_A_ACTIVE, 3L, null, null);
        seedTechnicianMembership(MEM_A_TERMINATED, TECH_PROFILE_A, NETWORK_A, "TERMINATED",
                CREATED_A_TERMINATED, 2L, CREATED_A_TERMINATED.plusSeconds(3600), "网点调整");
        seedTechnicianMembership(MEM_B, TECH_PROFILE_B, NETWORK_B, "ACTIVE",
                CREATED_B, 1L, null, null);
        seedGrant(PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_A.toString());
        seedGrant(OTHER_PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_B.toString());
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void m206_01_02_listReturnsOnlyOwnNetworkActiveMembershipsWithVersion() {
        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalPage<NetworkPortalMembershipItem> page =
                portal.listMemberships(actor(PRINCIPAL), "mem-list", contextA, null, null, 50);
        assertThat(page.networkId()).isEqualTo(NETWORK_A);
        assertThat(page.items()).extracting(NetworkPortalMembershipItem::id)
                .containsExactly(MEM_A_ACTIVE);
        assertThat(page.items().getFirst().version()).isEqualTo(3L);
        assertThat(page.items()).noneMatch(item -> MEM_B.equals(item.id()));
        assertThat(page.items()).noneMatch(item -> MEM_A_TERMINATED.equals(item.id()));
    }

    @Test
    void m206_03_04_getOwnOkCrossNetworkDenied() {
        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalMembershipItem own = portal.getMembership(
                actor(PRINCIPAL), "mem-get-ok", contextA, MEM_A_ACTIVE);
        assertThat(own.id()).isEqualTo(MEM_A_ACTIVE);
        assertThat(own.serviceNetworkId()).isEqualTo(NETWORK_A);
        assertThat(own.version()).isEqualTo(3L);
        assertThat(own.status()).isEqualTo("ACTIVE");

        assertThatThrownBy(() -> portal.getMembership(
                actor(PRINCIPAL), "mem-get-cross", contextA, MEM_B))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void m206_05_statusAndProfileFilters() {
        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalPage<NetworkPortalMembershipItem> terminated =
                portal.listMemberships(actor(PRINCIPAL), "mem-status", contextA,
                        "TERMINATED", null, 50);
        assertThat(terminated.items()).extracting(NetworkPortalMembershipItem::id)
                .containsExactly(MEM_A_TERMINATED);
        assertThat(terminated.items().getFirst().version()).isEqualTo(2L);
        assertThat(terminated.items().getFirst().terminateReason()).isEqualTo("网点调整");

        NetworkPortalPage<NetworkPortalMembershipItem> byProfile =
                portal.listMemberships(actor(PRINCIPAL), "mem-profile", contextA,
                        "ACTIVE", TECH_PROFILE_A, 50);
        assertThat(byProfile.items()).extracting(NetworkPortalMembershipItem::id)
                .containsExactly(MEM_A_ACTIVE);

        NetworkPortalPage<NetworkPortalMembershipItem> foreignProfile =
                portal.listMemberships(actor(PRINCIPAL), "mem-foreign-profile", contextA,
                        null, TECH_PROFILE_B, 50);
        assertThat(foreignProfile.items()).isEmpty();
    }

    @Test
    void m206_06_forgedContextIsPortalContextInvalid() {
        assertThatThrownBy(() -> portal.listMemberships(
                actor(PRINCIPAL), "mem-forged", "NETWORK|NETWORK|" + UUID.randomUUID(),
                null, null, 50))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void m206_08_missingTechnicianReadOwnIsAccessDenied() {
        jdbc.sql("""
                UPDATE auth_role_grant SET grant_status='REVOKED', revoked_at=now(),
                       revoked_by='test', revoke_reason='m206',
                       aggregate_version = aggregate_version + 1, updated_at=now()
                 WHERE tenant_id=:tenant AND principal_id=:principal
                   AND scope_type='NETWORK' AND scope_ref=:network
                   AND role_id IN (
                     SELECT role_id FROM auth_role_capability
                      WHERE capability_code='technician.readOwnNetwork'
                   )
                """)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL.toString())
                .param("network", NETWORK_A.toString())
                .update();

        assertThatThrownBy(() -> portal.listMemberships(
                actor(PRINCIPAL), "mem-cap-missing", "NETWORK|NETWORK|" + NETWORK_A,
                null, null, 50))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    private void seedPrincipal(UUID principalId, String displayName) {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'USER', 'ACTIVE', 1, now(), now())
                """).param("id", principalId).param("tenant", TENANT).update();
        jdbc.sql("""
                INSERT INTO idn_person_profile (
                    principal_id, tenant_id, display_name, employee_number,
                    profile_version, created_at, updated_at, updated_by
                ) VALUES (:id, :tenant, :name, :emp, 1, now(), now(), 'test')
                """)
                .param("id", principalId)
                .param("tenant", TENANT)
                .param("name", displayName)
                .param("emp", "E-" + principalId.toString().substring(24))
                .update();
    }

    private void seedPersona(UUID principalId, String type) {
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
                .param("principal", principalId)
                .param("type", type)
                .update();
    }

    private void seedPartnerAndNetworks() {
        jdbc.sql("""
                INSERT INTO net_partner_organization (
                    partner_organization_id, tenant_id, partner_code, partner_name,
                    partner_status, aggregate_version, created_at, updated_at
                ) VALUES (:id, :tenant, 'P-206', 'Partner 206', 'ACTIVE', 1, now(), now())
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

    private void seedNetworkMembership(UUID principalId, UUID networkId) {
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
                .param("principal", principalId)
                .update();
    }

    private void seedTechnicianProfile(UUID profileId, UUID principalId) {
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name, profile_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (
                    :id, :tenant, :principal, :name, 'ACTIVE', 1, now(), now()
                )
                """)
                .param("id", profileId)
                .param("tenant", TENANT)
                .param("principal", principalId)
                .param("name", "师傅 " + profileId.toString().substring(24))
                .update();
    }

    private void seedTechnicianMembership(
            UUID membershipId,
            UUID profileId,
            UUID networkId,
            String status,
            Instant createdAt,
            long version,
            Instant terminatedAt,
            String terminateReason
    ) {
        if ("TERMINATED".equals(status)) {
            // CHECK：TERMINATED 必须同时写 valid_to / terminated_* / terminate_reason
            jdbc.sql("""
                    INSERT INTO net_network_technician_membership (
                        membership_id, tenant_id, service_network_id, technician_profile_id,
                        membership_status, valid_from, valid_to, created_by, created_at,
                        terminated_by, terminated_at, terminate_reason, aggregate_version
                    ) VALUES (
                        :id, :tenant, :network, :profile,
                        'TERMINATED', :createdAt, :terminatedAt, 'test', :createdAt,
                        'test', :terminatedAt, :reason, :version
                    )
                    """)
                    .param("id", membershipId)
                    .param("tenant", TENANT)
                    .param("network", networkId)
                    .param("profile", profileId)
                    .param("createdAt", Timestamp.from(createdAt))
                    .param("terminatedAt", Timestamp.from(terminatedAt))
                    .param("reason", terminateReason)
                    .param("version", version)
                    .update();
            return;
        }
        jdbc.sql("""
                INSERT INTO net_network_technician_membership (
                    membership_id, tenant_id, service_network_id, technician_profile_id,
                    membership_status, valid_from, created_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :profile,
                    'ACTIVE', :createdAt, 'test', :createdAt, :version
                )
                """)
                .param("id", membershipId)
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("profile", profileId)
                .param("createdAt", Timestamp.from(createdAt))
                .param("version", version)
                .update();
    }

    private void seedGrant(UUID principalId, String capability, String scopeType, String scopeRef) {
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
                .param("code", "m206-" + capability + "-" + UUID.randomUUID())
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
                    now() - interval '1 day', 'TEST', 'ACTIVE', 'ALLOW',
                    1, now(), now()
                )
                """)
                .param("grantId", UUID.randomUUID())
                .param("tenant", TENANT)
                .param("principal", principalId.toString())
                .param("roleId", roleId)
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef)
                .update();
    }

    private static CurrentPrincipal actor(UUID principalId) {
        return new CurrentPrincipal(
                principalId.toString(),
                TENANT,
                CurrentPrincipal.PrincipalType.USER,
                "network-portal-m206",
                Set.of());
    }
}
