package com.serviceos.readmodel.application;

import com.serviceos.ServiceOsApplication;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.identity.api.AuthenticatedIdentity;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalAuthenticationService;
import com.serviceos.network.api.NetworkCommandService;
import com.serviceos.readmodel.api.ControlledSearchHit;
import com.serviceos.readmodel.api.ControlledSearchQueryService;
import com.serviceos.readmodel.api.ControlledSearchResult;
import com.serviceos.readmodel.api.ControlledSearchType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.workorder.api.ReceiveExternalWorkOrderCommand;
import com.serviceos.workorder.api.WorkOrderCommandService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * M192 Admin 受控搜索：WO/EXTERNAL/NETWORK/TECHNICIAN 命中、未支持 type、跨租户、能力与手机号规则。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ControlledSearchPostgresIT {
    private static final String TENANT_A = "tenant-search-a";
    private static final String TENANT_B = "tenant-search-b";
    private static final String CLIENT = "admin-web";
    private static final String ISSUER = "https://idp.example.com/realms/serviceos";
    private static final String SEARCHER = "019f82a1-1111-7f8c-9505-36fe5c0e8801";
    private static final String NO_SEARCH = "019f82a1-2222-7f8c-9505-36fe5c0e8802";
    private static final String PARTIAL = "019f82a1-3333-7f8c-9505-36fe5c0e8803";
    private static final String OTHER_TENANT = "019f82a1-4444-7f8c-9505-36fe5c0e8804";

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
        registry.add("serviceos.identity.jit-registration.allowed-contexts",
                () -> TENANT_A + "|" + CLIENT + "," + TENANT_B + "|" + CLIENT);
    }

    @Autowired ControlledSearchQueryService searches;
    @Autowired WorkOrderCommandService workOrders;
    @Autowired ConfigurationService configurations;
    @Autowired NetworkCommandService networkCommands;
    @Autowired PrincipalAuthenticationService authentication;
    @Autowired JdbcClient jdbc;
    @Autowired Flyway flyway;

    @BeforeEach
    void clean() {
        jdbc.sql("""
                TRUNCATE TABLE aud_audit_record, rel_outbox_publish_attempt, rel_outbox_event,
                    rel_idempotency_record,
                    wo_work_order, cfg_configuration_bundle_item, cfg_configuration_bundle,
                    cfg_configuration_asset_version, prj_project,
                    net_directory_event, net_clearance_work_item,
                    net_technician_qualification, net_network_technician_membership,
                    net_technician_profile, net_network_membership, net_service_network,
                    net_partner_organization,
                    idn_principal_lifecycle_event, idn_principal_persona,
                    idn_identity_link, idn_person_profile, idn_security_principal,
                    auth_delegation_capability, auth_delegation, auth_role_grant_event,
                    auth_tenant_grant_generation, auth_role_grant, auth_role_capability, auth_role CASCADE
                """).update();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("135");
        assertThat(flyway.info().applied()).hasSize(137);
        assertThat(jdbc.sql(
                        "SELECT risk_level FROM auth_capability WHERE capability_code='search.read'")
                .query(String.class).single()).isEqualTo("HIGH");
    }

    @Test
    void workOrderExternalNetworkTechnicianHits() {
        seedRole(TENANT_A, SEARCHER, List.of(
                "search.read", "workOrder.read", "network.read", "identity.read",
                "network.managePartner", "network.manageNetwork", "network.manageTechnician"));
        UUID workOrderId = seedWorkOrder(TENANT_A, "SEARCH-ORDER-001");
        var partner = networkCommands.createPartnerOrganization(
                actor(SEARCHER, TENANT_A), meta("p1"), "SRCH", "Search Partner");
        var network = networkCommands.createServiceNetwork(
                actor(SEARCHER, TENANT_A), meta("n1"), partner.id(), "SRCH-NET", "Search Network Alpha");
        UUID techPrincipal = seedIdentityPrincipal(TENANT_A, "tech-search", "张三师傅");
        jdbc.sql("UPDATE idn_person_profile SET employee_number='EMP9001' WHERE principal_id=:id")
                .param("id", techPrincipal).update();
        var profile = networkCommands.createTechnicianProfile(
                actor(SEARCHER, TENANT_A), meta("t1"), techPrincipal, "张三师傅", null);

        ControlledSearchResult byCode = searches.search(
                actor(SEARCHER, TENANT_A), "c-code", "SEARCH-ORDER-001",
                "WORK_ORDER,EXTERNAL_ORDER");
        assertThat(byCode.items()).extracting(ControlledSearchHit::type)
                .contains(ControlledSearchType.WORK_ORDER, ControlledSearchType.EXTERNAL_ORDER);
        assertThat(byCode.items()).extracting(ControlledSearchHit::resourceRef)
                .containsOnly(workOrderId.toString());
        assertThat(byCode.meta().qDigest()).isEqualTo(Sha256.digest("SEARCH-ORDER-001"));

        ControlledSearchResult byId = searches.search(
                actor(SEARCHER, TENANT_A), "c-id", workOrderId.toString(), "WORK_ORDER");
        assertThat(byId.items()).hasSize(1);
        assertThat(byId.items().getFirst().deepLink()).isEqualTo("/work-orders/" + workOrderId);

        ControlledSearchResult networks = searches.search(
                actor(SEARCHER, TENANT_A), "c-net", "SRCH", "NETWORK");
        assertThat(networks.items()).extracting(ControlledSearchHit::resourceRef)
                .contains(network.id().toString());

        ControlledSearchResult byName = searches.search(
                actor(SEARCHER, TENANT_A), "c-tech", "张三", "TECHNICIAN");
        assertThat(byName.items()).extracting(ControlledSearchHit::resourceRef)
                .contains(profile.id().toString());

        ControlledSearchResult byEmp = searches.search(
                actor(SEARCHER, TENANT_A), "c-emp", "EMP9001", "TECHNICIAN");
        assertThat(byEmp.items()).extracting(ControlledSearchHit::matchReason)
                .contains("EMPLOYEE_NUMBER_EXACT");
    }

    @Test
    void unsupportedTypeIsRejected() {
        seedRole(TENANT_A, SEARCHER, List.of("search.read", "workOrder.read"));
        assertThatThrownBy(() -> searches.search(
                actor(SEARCHER, TENANT_A), "c-bad", "ab", "VEHICLE"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.SEARCH_TERM_NOT_ALLOWED));
    }

    @Test
    void crossTenantIsEmptyAndMissingSearchCapabilityDenied() {
        seedRole(TENANT_A, SEARCHER, List.of(
                "search.read", "workOrder.read", "network.read",
                "network.managePartner", "network.manageNetwork"));
        seedRole(TENANT_B, OTHER_TENANT, List.of("search.read", "workOrder.read", "network.read"));
        seedRole(TENANT_A, NO_SEARCH, List.of("workOrder.read"));

        UUID workOrderId = seedWorkOrder(TENANT_A, "CROSS-ORDER");
        var partner = networkCommands.createPartnerOrganization(
                actor(SEARCHER, TENANT_A), meta("px"), "X", "X Partner");
        networkCommands.createServiceNetwork(
                actor(SEARCHER, TENANT_A), meta("nx"), partner.id(), "X-NET", "X Network");

        assertThat(searches.search(actor(OTHER_TENANT, TENANT_B), "c-x", "CROSS-ORDER", "WORK_ORDER")
                .items()).isEmpty();
        assertThat(searches.search(actor(OTHER_TENANT, TENANT_B), "c-x2", workOrderId.toString(), "WORK_ORDER")
                .items()).isEmpty();
        assertThat(searches.search(actor(OTHER_TENANT, TENANT_B), "c-x3", "X-NET", "NETWORK")
                .items()).isEmpty();

        assertThatThrownBy(() -> searches.search(actor(NO_SEARCH, TENANT_A), "c-deny", "ab", null))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
    }

    @Test
    void missingTypeCapabilityOmitsType() {
        seedRole(TENANT_A, PARTIAL, List.of("search.read", "workOrder.read"));
        seedWorkOrder(TENANT_A, "PARTIAL-ORDER");

        ControlledSearchResult result = searches.search(
                actor(PARTIAL, TENANT_A), "c-omit", "PARTIAL-ORDER", "WORK_ORDER,NETWORK");
        assertThat(result.meta().searchedTypes()).containsExactly(ControlledSearchType.WORK_ORDER);
        assertThat(result.meta().omittedTypes()).containsExactly(ControlledSearchType.NETWORK);
        assertThat(result.items()).isNotEmpty();
    }

    @Test
    void fullPhoneRejectedLast4Allowed() {
        seedRole(TENANT_A, SEARCHER, List.of("search.read", "workOrder.read"));
        assertThatThrownBy(() -> searches.search(
                actor(SEARCHER, TENANT_A), "c-phone", "13800138000", "WORK_ORDER"))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        p -> assertThat(p.code()).isEqualTo(ProblemCode.SEARCH_TERM_NOT_ALLOWED));

        ControlledSearchResult last4 = searches.search(
                actor(SEARCHER, TENANT_A), "c-last4", "8000", "WORK_ORDER");
        assertThat(last4.meta().qDigest()).isEqualTo(Sha256.digest("8000"));
        assertThat(last4.items()).isEmpty();
    }

    private UUID seedWorkOrder(String tenant, String externalOrderCode) {
        UUID projectId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO prj_project
                (project_id,tenant_id,project_code,client_id,project_name,starts_on,project_status,aggregate_version,created_at)
                VALUES (:id,:tenant,:code,'BYD',:name,current_date,'ACTIVE',1,now())
                """).param("id", projectId).param("tenant", tenant)
                .param("code", "P-" + externalOrderCode).param("name", "项目" + externalOrderCode).update();
        String definition = "{\"workflowCode\":\"WF-" + externalOrderCode + "\"}";
        UUID asset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenant, ConfigurationAssetType.WORKFLOW, "WF-" + externalOrderCode, "1.0.0", "1.0.0",
                definition, Sha256.digest(definition))).versionId();
        var bundle = configurations.publishBundle(new PublishConfigurationBundleCommand(
                tenant, projectId, "B-" + externalOrderCode, "1.0.0", "BYD_OCEAN",
                "HOME_CHARGING_SURVEY_INSTALL", "370000", Instant.now().minusSeconds(60), null, List.of(asset)));
        return workOrders.receive(new ReceiveExternalWorkOrderCommand(
                tenant, projectId, "BYD", "BYD_OCEAN", "HOME_CHARGING_SURVEY_INSTALL",
                externalOrderCode, "d".repeat(64), bundle.bundleId(), bundle.bundleCode(),
                bundle.bundleVersion(), bundle.manifestDigest(), "370000", "370100", "370102",
                "敏感姓名", "13800000000", "敏感地址", "VIN123456789",
                LocalDateTime.of(2026, 7, 15, 10, 0), "corr-" + externalOrderCode, "cause")).workOrderId();
    }

    private UUID seedIdentityPrincipal(String tenant, String subject, String displayName) {
        return UUID.fromString(authentication.resolveOrRegister(
                new AuthenticatedIdentity(tenant, ISSUER, subject, CLIENT,
                        CurrentPrincipal.PrincipalType.USER, displayName),
                "corr-" + subject));
    }

    private void seedRole(String tenantId, String principal, List<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (role_id, tenant_id, role_code, role_name, role_status, created_at)
                VALUES (:roleId, :tenant, :code, :name, 'ACTIVE', now())
                """).param("roleId", roleId).param("tenant", tenantId)
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
                    now() - interval '1 day', 'TEST_FIXTURE', 'm192', now()
                )
                """).param("grantId", UUID.randomUUID()).param("tenant", tenantId)
                .param("principal", principal).param("roleId", roleId).update();
    }

    private static CurrentPrincipal actor(String principalId, String tenantId) {
        return new CurrentPrincipal(principalId, tenantId, CurrentPrincipal.PrincipalType.USER, CLIENT, Set.of());
    }

    private static CommandMetadata meta(String suffix) {
        return new CommandMetadata("corr-" + suffix, "idem-" + suffix);
    }
}
