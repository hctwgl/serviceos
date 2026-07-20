package com.serviceos.network;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.network.api.NetworkPortalManageTechnicianService;
import com.serviceos.network.api.NetworkTechnicianMembershipView;
import com.serviceos.network.api.TechnicianQualificationView;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M204 Network Portal 师傅关系与资质提交：成功绑定/终止/提交、跨网点拒绝、伪造上下文、缺能力。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalManageTechnicianPostgresIT {
    private static final String TENANT = "tenant-network-portal-m204";
    private static final UUID PRINCIPAL = UUID.fromString("019f84c0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_PRINCIPAL = UUID.fromString("019f84c0-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NETWORK_A = UUID.fromString("019f84c0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f84c0-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f84c0-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID TECH_PROFILE = UUID.fromString("019f84c0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PRINCIPAL = UUID.fromString("019f84c0-6666-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_PROFILE_B = UUID.fromString("019f84c0-5556-7f8c-9505-36fe5c0e8808");
    private static final UUID TECH_PRINCIPAL_B = UUID.fromString("019f84c0-6667-7f8c-9505-36fe5c0e8809");
    private static final UUID TECH_PROFILE_UNBOUND = UUID.fromString("019f84c0-5557-7f8c-9505-36fe5c0e880a");
    private static final UUID TECH_PRINCIPAL_UNBOUND = UUID.fromString("019f84c0-6668-7f8c-9505-36fe5c0e880b");
    private static final Instant VALID_FROM = Instant.parse("2026-07-17T00:00:00Z");

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

    @Autowired NetworkPortalManageTechnicianService portalManage;
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("135");
        assertThat(flyway.info().applied()).hasSize(137);
        assertThat(jdbc.sql("""
                        SELECT risk_level FROM auth_capability
                         WHERE capability_code='networkPortal.manageTechnician'
                        """).query(String.class).single()).isEqualTo("HIGH");

        seedPrincipal(PRINCIPAL, "Portal Member");
        seedPrincipal(OTHER_PRINCIPAL, "Other Member");
        seedPrincipal(TECH_PRINCIPAL, "Technician A");
        seedPrincipal(TECH_PRINCIPAL_B, "Technician B");
        seedPrincipal(TECH_PRINCIPAL_UNBOUND, "Technician Unbound");
        seedPersona(PRINCIPAL, "NETWORK_MEMBER");
        seedPartnerAndNetworks();
        seedNetworkMembership(PRINCIPAL, NETWORK_A);
        seedNetworkMembership(OTHER_PRINCIPAL, NETWORK_B);
        seedTechnicianProfile(TECH_PROFILE, TECH_PRINCIPAL);
        seedTechnicianProfile(TECH_PROFILE_B, TECH_PRINCIPAL_B);
        seedTechnicianProfile(TECH_PROFILE_UNBOUND, TECH_PRINCIPAL_UNBOUND);
        seedTechnicianMembership(UUID.fromString("019f84c0-7777-7f8c-9505-36fe5c0e8801"),
                TECH_PROFILE, NETWORK_A);
        seedTechnicianMembership(UUID.fromString("019f84c0-7778-7f8c-9505-36fe5c0e8802"),
                TECH_PROFILE_B, NETWORK_B);
        seedGrant(PRINCIPAL, "networkPortal.manageTechnician", "NETWORK", NETWORK_A.toString());
        seedGrant(PRINCIPAL, "network.manageTechnician", "NETWORK", NETWORK_A.toString());
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void m204_01_createMembershipUsesTrustedContextNetwork() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkTechnicianMembershipView created = portalManage.createMembership(
                actor(PRINCIPAL), metadata("m204-create"), context,
                TECH_PROFILE_UNBOUND, VALID_FROM);

        assertThat(created.serviceNetworkId()).isEqualTo(NETWORK_A);
        assertThat(created.technicianProfileId()).isEqualTo(TECH_PROFILE_UNBOUND);
        assertThat(created.status()).isEqualTo("ACTIVE");

        NetworkTechnicianMembershipView replay = portalManage.createMembership(
                actor(PRINCIPAL), metadata("m204-create"), context,
                TECH_PROFILE_UNBOUND, VALID_FROM);
        assertThat(replay.id()).isEqualTo(created.id());
    }

    @Test
    void m204_02_terminateMembershipOnContextNetwork() {
        UUID membershipId = UUID.fromString("019f84c0-7777-7f8c-9505-36fe5c0e8801");
        String context = "NETWORK|NETWORK|" + NETWORK_A;

        NetworkTechnicianMembershipView terminated = portalManage.terminateMembership(
                actor(PRINCIPAL), metadata("m204-term"), context,
                membershipId, 1L, "网点调整");

        assertThat(terminated.status()).isEqualTo("TERMINATED");
        assertThat(terminated.serviceNetworkId()).isEqualTo(NETWORK_A);
    }

    @Test
    void m204_03_submitQualificationForActiveNetworkTechnician() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        TechnicianQualificationView qualification = portalManage.submitQualification(
                actor(PRINCIPAL), metadata("m204-qual"), context,
                TECH_PROFILE, "EV-INSTALL", VALID_FROM, VALID_FROM.plusSeconds(86400 * 365));

        assertThat(qualification.status()).isEqualTo("PENDING");
        assertThat(qualification.technicianProfileId()).isEqualTo(TECH_PROFILE);
        assertThat(qualification.qualificationCode()).isEqualTo("EV-INSTALL");
    }

    @Test
    void m204_04_submitQualificationForNonNetworkTechnicianFails() {
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        assertThatThrownBy(() -> portalManage.submitQualification(
                actor(PRINCIPAL), metadata("m204-qual-cross"), context,
                TECH_PROFILE_B, "EV-INSTALL", VALID_FROM, null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.VALIDATION_FAILED));
    }

    @Test
    void m204_05_terminateOtherNetworkMembershipIsAccessDenied() {
        UUID foreignMembership = UUID.fromString("019f84c0-7778-7f8c-9505-36fe5c0e8802");
        String context = "NETWORK|NETWORK|" + NETWORK_A;
        assertThatThrownBy(() -> portalManage.terminateMembership(
                actor(PRINCIPAL), metadata("m204-term-cross"), context,
                foreignMembership, 1L, "越权终止"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void m204_06_forgedContextIsPortalContextInvalid() {
        assertThatThrownBy(() -> portalManage.createMembership(
                actor(PRINCIPAL), metadata("m204-forged"), "NETWORK|NETWORK|" + UUID.randomUUID(),
                TECH_PROFILE_UNBOUND, VALID_FROM))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void m204_08_missingPortalCapabilityIsAccessDenied() {
        jdbc.sql("""
                UPDATE auth_role_grant SET grant_status='REVOKED', revoked_at=now(),
                       revoked_by='test', revoke_reason='m204',
                       aggregate_version = aggregate_version + 1, updated_at=now()
                 WHERE tenant_id=:tenant AND principal_id=:principal
                   AND scope_type='NETWORK' AND scope_ref=:network
                   AND role_id IN (
                     SELECT role_id FROM auth_role_capability
                      WHERE capability_code='networkPortal.manageTechnician'
                   )
                """)
                .param("tenant", TENANT)
                .param("principal", PRINCIPAL.toString())
                .param("network", NETWORK_A.toString())
                .update();

        assertThatThrownBy(() -> portalManage.createMembership(
                actor(PRINCIPAL), metadata("m204-cap"), "NETWORK|NETWORK|" + NETWORK_A,
                TECH_PROFILE_UNBOUND, VALID_FROM))
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
                ) VALUES (:id, :tenant, 'P-204', 'Partner 204', 'ACTIVE', 1, now(), now())
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

    private void seedTechnicianMembership(UUID membershipId, UUID profileId, UUID networkId) {
        jdbc.sql("""
                INSERT INTO net_network_technician_membership (
                    membership_id, tenant_id, service_network_id, technician_profile_id,
                    membership_status, valid_from, created_by, created_at, aggregate_version
                ) VALUES (
                    :id, :tenant, :network, :profile,
                    'ACTIVE', now() - interval '1 day', 'test', now(), 1
                )
                """)
                .param("id", membershipId)
                .param("tenant", TENANT)
                .param("network", networkId)
                .param("profile", profileId)
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
                .param("code", "m204-" + capability + "-" + UUID.randomUUID())
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
                .param("principal", principalId.toString())
                .param("roleId", roleId)
                .param("scopeType", scopeType)
                .param("scopeRef", scopeRef)
                .update();
    }

    private static CurrentPrincipal actor(UUID principalId) {
        return new CurrentPrincipal(principalId.toString(), TENANT, CurrentPrincipal.PrincipalType.USER,
                "network-portal", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }
}
