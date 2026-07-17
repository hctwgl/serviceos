package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.readmodel.api.NetworkPortalPage;
import com.serviceos.readmodel.api.NetworkPortalQualificationItem;
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
 * M205 Network Portal 资质只读列表：本网点隔离、过滤、get 允许/拒绝、伪造上下文、缺能力。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkPortalQualificationListPostgresIT {
    private static final String TENANT = "tenant-network-portal-m205";
    private static final UUID PRINCIPAL = UUID.fromString("019f85d0-1111-7f8c-9505-36fe5c0e8801");
    private static final UUID OTHER_PRINCIPAL = UUID.fromString("019f85d0-1112-7f8c-9505-36fe5c0e8802");
    private static final UUID NETWORK_A = UUID.fromString("019f85d0-2222-7f8c-9505-36fe5c0e8803");
    private static final UUID NETWORK_B = UUID.fromString("019f85d0-3333-7f8c-9505-36fe5c0e8804");
    private static final UUID PARTNER = UUID.fromString("019f85d0-4444-7f8c-9505-36fe5c0e8805");
    private static final UUID TECH_PROFILE_A = UUID.fromString("019f85d0-5555-7f8c-9505-36fe5c0e8806");
    private static final UUID TECH_PRINCIPAL_A = UUID.fromString("019f85d0-6666-7f8c-9505-36fe5c0e8807");
    private static final UUID TECH_PROFILE_B = UUID.fromString("019f85d0-5556-7f8c-9505-36fe5c0e8808");
    private static final UUID TECH_PRINCIPAL_B = UUID.fromString("019f85d0-6667-7f8c-9505-36fe5c0e8809");
    private static final UUID QUAL_A_PENDING = UUID.fromString("019f85d0-aaaa-7f8c-9505-36fe5c0e8810");
    private static final UUID QUAL_A_APPROVED = UUID.fromString("019f85d0-aaab-7f8c-9505-36fe5c0e8811");
    private static final UUID QUAL_B = UUID.fromString("019f85d0-bbbb-7f8c-9505-36fe5c0e8812");
    private static final Instant SUBMITTED_A_PENDING = Instant.parse("2026-07-17T10:00:00Z");
    private static final Instant SUBMITTED_A_APPROVED = Instant.parse("2026-07-17T11:00:00Z");
    private static final Instant SUBMITTED_B = Instant.parse("2026-07-17T09:00:00Z");

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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("100");
        assertThat(flyway.info().applied()).hasSize(102);

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
        seedTechnicianMembership(UUID.fromString("019f85d0-7777-7f8c-9505-36fe5c0e8801"),
                TECH_PROFILE_A, NETWORK_A);
        seedTechnicianMembership(UUID.fromString("019f85d0-7778-7f8c-9505-36fe5c0e8802"),
                TECH_PROFILE_B, NETWORK_B);
        seedQualification(QUAL_A_PENDING, TECH_PROFILE_A, "EV-INSTALL", "PENDING",
                SUBMITTED_A_PENDING, null, null, null);
        seedQualification(QUAL_A_APPROVED, TECH_PROFILE_A, "EV-COMMISSION", "APPROVED",
                SUBMITTED_A_APPROVED, "decider", SUBMITTED_A_APPROVED.plusSeconds(60), "通过");
        seedQualification(QUAL_B, TECH_PROFILE_B, "EV-INSTALL", "PENDING",
                SUBMITTED_B, null, null, null);
        seedGrant(PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_A.toString());
        seedGrant(OTHER_PRINCIPAL, "technician.readOwnNetwork", "NETWORK", NETWORK_B.toString());
        jdbc.sql("""
                INSERT INTO auth_tenant_grant_generation (tenant_id, generation, updated_at)
                VALUES (:tenant, 1, now())
                ON CONFLICT (tenant_id) DO UPDATE SET generation = 1, updated_at = now()
                """).param("tenant", TENANT).update();
    }

    @Test
    void m205_01_02_listReturnsOnlyOwnNetworkQualifications() {
        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalPage<NetworkPortalQualificationItem> page =
                portal.listQualifications(actor(PRINCIPAL), "qual-list", contextA, null, null, 50);
        assertThat(page.networkId()).isEqualTo(NETWORK_A);
        assertThat(page.items()).extracting(NetworkPortalQualificationItem::id)
                .containsExactly(QUAL_A_PENDING, QUAL_A_APPROVED);
        assertThat(page.items()).noneMatch(item -> QUAL_B.equals(item.id()));
    }

    @Test
    void m205_03_04_getOwnOkCrossNetworkDenied() {
        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalQualificationItem own = portal.getQualification(
                actor(PRINCIPAL), "qual-get-ok", contextA, QUAL_A_PENDING);
        assertThat(own.id()).isEqualTo(QUAL_A_PENDING);
        assertThat(own.technicianProfileId()).isEqualTo(TECH_PROFILE_A);
        assertThat(own.status()).isEqualTo("PENDING");

        assertThatThrownBy(() -> portal.getQualification(
                actor(PRINCIPAL), "qual-get-cross", contextA, QUAL_B))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void m205_05_statusAndProfileFilters() {
        String contextA = "NETWORK|NETWORK|" + NETWORK_A;
        NetworkPortalPage<NetworkPortalQualificationItem> byStatus =
                portal.listQualifications(actor(PRINCIPAL), "qual-status", contextA,
                        "PENDING", null, 50);
        assertThat(byStatus.items()).extracting(NetworkPortalQualificationItem::id)
                .containsExactly(QUAL_A_PENDING);

        NetworkPortalPage<NetworkPortalQualificationItem> byProfile =
                portal.listQualifications(actor(PRINCIPAL), "qual-profile", contextA,
                        null, TECH_PROFILE_A, 50);
        assertThat(byProfile.items()).extracting(NetworkPortalQualificationItem::id)
                .containsExactly(QUAL_A_PENDING, QUAL_A_APPROVED);

        NetworkPortalPage<NetworkPortalQualificationItem> foreignProfile =
                portal.listQualifications(actor(PRINCIPAL), "qual-foreign-profile", contextA,
                        null, TECH_PROFILE_B, 50);
        assertThat(foreignProfile.items()).isEmpty();
    }

    @Test
    void m205_06_forgedContextIsPortalContextInvalid() {
        assertThatThrownBy(() -> portal.listQualifications(
                actor(PRINCIPAL), "qual-forged", "NETWORK|NETWORK|" + UUID.randomUUID(),
                null, null, 50))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.PORTAL_CONTEXT_INVALID));
    }

    @Test
    void m205_08_missingTechnicianReadOwnIsAccessDenied() {
        jdbc.sql("""
                UPDATE auth_role_grant SET grant_status='REVOKED', revoked_at=now(),
                       revoked_by='test', revoke_reason='m205',
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

        assertThatThrownBy(() -> portal.listQualifications(
                actor(PRINCIPAL), "qual-cap-missing", "NETWORK|NETWORK|" + NETWORK_A,
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
                ) VALUES (:id, :tenant, 'P-205', 'Partner 205', 'ACTIVE', 1, now(), now())
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

    private void seedQualification(
            UUID qualificationId,
            UUID profileId,
            String code,
            String status,
            Instant submittedAt,
            String decidedBy,
            Instant decidedAt,
            String decisionReason
    ) {
        // JDBC 对 Instant/可空时间戳推断不稳；PENDING 与已裁决分路径，时间用 Timestamp。
        if ("PENDING".equals(status)) {
            jdbc.sql("""
                    INSERT INTO net_technician_qualification (
                        qualification_id, tenant_id, technician_profile_id, qualification_code,
                        qualification_status, valid_from, valid_to, submitted_by, submitted_at,
                        decided_by, decided_at, decision_reason, aggregate_version
                    ) VALUES (
                        :id, :tenant, :profile, :code, 'PENDING',
                        :validFrom, :validTo, 'submitter', :submittedAt,
                        NULL, NULL, NULL, 1
                    )
                    """)
                    .param("id", qualificationId)
                    .param("tenant", TENANT)
                    .param("profile", profileId)
                    .param("code", code)
                    .param("validFrom", Timestamp.from(submittedAt))
                    .param("validTo", Timestamp.from(submittedAt.plusSeconds(86400L * 365)))
                    .param("submittedAt", Timestamp.from(submittedAt))
                    .update();
            return;
        }
        jdbc.sql("""
                INSERT INTO net_technician_qualification (
                    qualification_id, tenant_id, technician_profile_id, qualification_code,
                    qualification_status, valid_from, valid_to, submitted_by, submitted_at,
                    decided_by, decided_at, decision_reason, aggregate_version
                ) VALUES (
                    :id, :tenant, :profile, :code, :status,
                    :validFrom, :validTo, 'submitter', :submittedAt,
                    :decidedBy, :decidedAt, :decisionReason, 1
                )
                """)
                .param("id", qualificationId)
                .param("tenant", TENANT)
                .param("profile", profileId)
                .param("code", code)
                .param("status", status)
                .param("validFrom", Timestamp.from(submittedAt))
                .param("validTo", Timestamp.from(submittedAt.plusSeconds(86400L * 365)))
                .param("submittedAt", Timestamp.from(submittedAt))
                .param("decidedBy", decidedBy)
                .param("decidedAt", Timestamp.from(decidedAt))
                .param("decisionReason", decisionReason)
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
                .param("code", "m205-" + capability + "-" + UUID.randomUUID())
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
                "network-portal-m205",
                Set.of());
    }
}
