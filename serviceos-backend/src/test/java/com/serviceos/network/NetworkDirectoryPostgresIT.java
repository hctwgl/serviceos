package com.serviceos.network;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.AuthenticatedIdentity;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalAuthenticationService;
import com.serviceos.network.api.NetworkCommandService;
import com.serviceos.network.api.NetworkQueryService;
import com.serviceos.network.api.TechnicianEligibilityQuery;
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
 * M185 网点与师傅目录的真实 PostgreSQL 验收证据：独立网点、成员邀请、多网点师傅、
 * 可接单判定、清退待办与资质审核。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NetworkDirectoryPostgresIT {
    private static final String TENANT = "tenant-network-test";
    private static final String CLIENT = "admin-web";
    private static final String ISSUER = "https://idp.example.com/realms/serviceos";
    private static final String ACTOR = "network-admin";
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

    @Autowired NetworkCommandService commands;
    @Autowired NetworkQueryService queries;
    @Autowired TechnicianEligibilityQuery eligibility;
    @Autowired PrincipalAuthenticationService authentication;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void cleanAndAuthorizeActor() {
        jdbc.sql("""
                TRUNCATE TABLE net_directory_event, net_clearance_work_item,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization,
                    idn_principal_lifecycle_event, idn_principal_persona,
                    idn_identity_link, idn_person_profile, idn_security_principal,
                    rel_idempotency_record, aud_audit_record,
                    auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        seedRole(ACTOR, List.of(
                "network.read", "network.managePartner", "network.manageNetwork",
                "network.manageMembership", "network.manageTechnician", "network.reviewQualification",
                "identity.read"));
    }

    @Test
    void m185_01_partnerOwnsMultipleNetworksWithoutOrgUnitClosure() {
        var partner = commands.createPartnerOrganization(actor(), metadata("partner"), "ACME", "Acme Partner");
        var networkA = commands.createServiceNetwork(actor(), metadata("net-a"), partner.id(), "NET-A", "Network A");
        var networkB = commands.createServiceNetwork(actor(), metadata("net-b"), partner.id(), "NET-B", "Network B");

        assertThat(networkA.partnerOrganizationId()).isEqualTo(partner.id());
        assertThat(networkB.partnerOrganizationId()).isEqualTo(partner.id());
        assertThat(queries.listServiceNetworks(actor(), "list", partner.id()).items()).hasSize(2);
        assertThat(jdbc.sql("SELECT count(*) FROM org_unit_closure WHERE tenant_id=:tenant")
                .param("tenant", TENANT).query(Long.class).single()).isZero();
    }

    @Test
    void m185_02_networkManagerInviteCreatesMembershipWithoutNewPrincipal() {
        var partner = commands.createPartnerOrganization(actor(), metadata("partner"), "INV", "Invite Partner");
        var network = commands.createServiceNetwork(actor(), metadata("network"), partner.id(), "INV-NET", "Invite Net");
        UUID memberPrincipal = seedPrincipal("member-1", "网点成员");
        long principalCountBefore = jdbc.sql("SELECT count(*) FROM idn_security_principal WHERE tenant_id=:tenant")
                .param("tenant", TENANT).query(Long.class).single();

        var membership = commands.inviteNetworkMember(actor(), metadata("invite"), network.id(), null,
                memberPrincipal, "STAFF", NOW);

        assertThat(membership.principalId()).isEqualTo(memberPrincipal);
        assertThat(membership.status()).isEqualTo("ACTIVE");
        assertThat(jdbc.sql("SELECT count(*) FROM idn_security_principal WHERE tenant_id=:tenant")
                .param("tenant", TENANT).query(Long.class).single()).isEqualTo(principalCountBefore);
    }

    @Test
    void m185_03_technicianCanServeMultipleNetworksIndependently() {
        var partner = commands.createPartnerOrganization(actor(), metadata("partner"), "MULTI", "Multi Partner");
        var networkA = commands.createServiceNetwork(actor(), metadata("net-a"), partner.id(), "MA", "Net A");
        var networkB = commands.createServiceNetwork(actor(), metadata("net-b"), partner.id(), "MB", "Net B");
        UUID techPrincipal = seedPrincipal("tech-multi", "多网点师傅");
        var profile = commands.createTechnicianProfile(actor(), metadata("profile"), techPrincipal, "多网点师傅", null);
        approveQualification(profile.id());

        var membershipA = commands.createNetworkTechnicianMembership(actor(), metadata("mem-a"),
                networkA.id(), profile.id(), NOW);
        var membershipB = commands.createNetworkTechnicianMembership(actor(), metadata("mem-b"),
                networkB.id(), profile.id(), NOW.plusSeconds(3600));

        assertThat(membershipA.id()).isNotEqualTo(membershipB.id());
        assertThat(queries.listNetworkTechnicianMemberships(actor(), "list", null, profile.id()).items())
                .hasSize(2);
        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, networkA.id(), NOW)).isTrue();
        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, networkB.id(), NOW.plusSeconds(7200)))
                .isTrue();
    }

    @Test
    void m185_04_eligibilityFailsWhenQualificationMissingOrProfileDisabled() {
        var partner = commands.createPartnerOrganization(actor(), metadata("partner"), "ELIG", "Elig Partner");
        var network = commands.createServiceNetwork(actor(), metadata("network"), partner.id(), "ELIG-NET", "Elig Net");
        UUID techPrincipal = seedPrincipal("tech-elig", "资质师傅");
        var profile = commands.createTechnicianProfile(actor(), metadata("profile"), techPrincipal, "资质师傅", null);
        commands.createNetworkTechnicianMembership(actor(), metadata("membership"), network.id(), profile.id(), NOW);

        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, network.id(), NOW)).isFalse();

        var qualification = commands.submitQualification(actor(), metadata("submit"), profile.id(),
                "EV-INSTALL", NOW, NOW.plusSeconds(86400 * 365));
        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, network.id(), NOW)).isFalse();

        commands.decideQualification(actor(), metadata("approve"), qualification.id(), qualification.version(),
                "APPROVED", "总部批准");
        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, network.id(), NOW)).isTrue();

        commands.disableTechnicianProfile(actor(), metadata("disable"), profile.id(), profile.version(),
                "停用测试");
        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, network.id(), NOW)).isFalse();
    }

    @Test
    void m185_05_deactivateNetworkCreatesClearanceWorkItemAndBlocksEligibility() {
        var partner = commands.createPartnerOrganization(actor(), metadata("partner"), "DEACT", "Deact Partner");
        var network = commands.createServiceNetwork(actor(), metadata("network"), partner.id(), "DEACT-NET", "Deact Net");
        UUID techPrincipal = seedPrincipal("tech-deact", "清退师傅");
        var profile = commands.createTechnicianProfile(actor(), metadata("profile"), techPrincipal, "清退师傅", null);
        approveQualification(profile.id());
        commands.createNetworkTechnicianMembership(actor(), metadata("membership"), network.id(), profile.id(), NOW);
        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, network.id(), NOW)).isTrue();

        var deactivated = commands.deactivateServiceNetwork(actor(), metadata("deactivate"),
                network.id(), network.version(), "合同到期清退");
        assertThat(deactivated.status()).isEqualTo("DEACTIVATED");
        assertThat(queries.listOpenClearanceWorkItems(actor(), "clearance").items()).hasSize(1);
        assertThat(eligibility.canAcceptAssignment(TENANT, techPrincipal, network.id(), NOW)).isFalse();
    }

    @Test
    void m185_06_qualificationDecisionRequiresReviewCapability() {
        UUID techPrincipal = seedPrincipal("tech-qual", "资质审核师傅");
        var profile = commands.createTechnicianProfile(actor(), metadata("profile"), techPrincipal, "资质审核师傅", null);
        var qualification = commands.submitQualification(actor(), metadata("submit"), profile.id(),
                "HIGH_VOLTAGE", NOW, null);

        seedRole("network-manager-only", List.of("network.read", "network.manageTechnician"));
        CurrentPrincipal managerOnly = new CurrentPrincipal(
                "network-manager-only", TENANT, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
        assertThatThrownBy(() -> commands.decideQualification(managerOnly, metadata("denied"),
                qualification.id(), qualification.version(), "APPROVED", "网点自批"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));

        var decided = commands.decideQualification(actor(), metadata("approve"), qualification.id(),
                qualification.version(), "APPROVED", "总部审核通过");
        assertThat(decided.status()).isEqualTo("APPROVED");
    }

    @Test
    void m185_duplicateActiveMembershipIsRejected() {
        var partner = commands.createPartnerOrganization(actor(), metadata("partner"), "DUP", "Dup Partner");
        var network = commands.createServiceNetwork(actor(), metadata("network"), partner.id(), "DUP-NET", "Dup Net");
        UUID memberPrincipal = seedPrincipal("dup-member", "重复成员");
        commands.inviteNetworkMember(actor(), metadata("first"), network.id(), null,
                memberPrincipal, "STAFF", NOW);

        assertThatThrownBy(() -> commands.inviteNetworkMember(actor(), metadata("dup"), network.id(), null,
                memberPrincipal, "STAFF", NOW))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.NETWORK_MEMBERSHIP_CONFLICT));
    }

    private void approveQualification(UUID profileId) {
        var qualification = commands.submitQualification(actor(), metadata("qual-" + profileId), profileId,
                "DEFAULT", NOW, NOW.plusSeconds(86400 * 365));
        commands.decideQualification(actor(), metadata("decide-" + profileId), qualification.id(),
                qualification.version(), "APPROVED", "测试批准");
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'm185', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenant", TENANT)
                .param("principal", principal).param("roleId", roleId).update();
    }

    private static CurrentPrincipal actor() {
        return new CurrentPrincipal(ACTOR, TENANT, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
    }

    private static CommandMetadata metadata(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }
}
