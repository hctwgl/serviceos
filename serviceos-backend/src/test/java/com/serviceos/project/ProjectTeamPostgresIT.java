package com.serviceos.project;

import com.serviceos.ServiceOsApplication;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.project.api.AddProjectTeamMemberCommand;
import com.serviceos.project.api.AssignProjectRegionPersonnelCommand;
import com.serviceos.project.api.CreateProjectCommand;
import com.serviceos.project.api.ProjectCommandService;
import com.serviceos.project.api.ProjectPositionCode;
import com.serviceos.workorder.api.WorkOrderProjectPersonnelResolver;
import com.serviceos.project.api.ProjectTeamService;
import com.serviceos.project.api.ProjectView;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 项目团队与区域分工的真实 PostgreSQL 证据。
 *
 * <p>重点证明当前负责人唯一约束、替换事务、精确行政区优先和受控向上继承。匹配失败必须
 * 返回明确缺失岗位，禁止选择任意人员兜底。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ProjectTeamPostgresIT {
    private static final String TENANT = "tenant-project-team";
    private static final UUID CUSTOMER_MANAGER = UUID.fromString("84000000-0000-4000-8000-000000000001");
    private static final UUID PROJECT_MANAGER = UUID.fromString("84000000-0000-4000-8000-000000000002");
    private static final UUID PROJECT_ASSISTANT = UUID.fromString("84000000-0000-4000-8000-000000000003");
    private static final UUID REPLACEMENT_MANAGER = UUID.fromString("84000000-0000-4000-8000-000000000004");

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
    ProjectCommandService projectCommands;

    @Autowired
    ProjectTeamService teams;

    @Autowired
    WorkOrderProjectPersonnelResolver personnelResolver;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void resetAndSeed() {
        jdbc.sql("""
                TRUNCATE TABLE prj_project, idn_security_principal,
                    aud_audit_record, rel_idempotency_record,
                    auth_role_field_policy, auth_role_grant,
                    auth_role_capability, auth_role CASCADE
                """).update();
        seedRole("operator", List.of(
                "project.create", "project.read", "project.team.manage", "identity.read"));
        seedPerson(CUSTOMER_MANAGER, "周雨桐", "BYD-CS-018");
        seedPerson(PROJECT_MANAGER, "陈昊", "BYD-PM-006");
        seedPerson(PROJECT_ASSISTANT, "林晓雯", "BYD-PA-012");
        seedPerson(REPLACEMENT_MANAGER, "许文博", "BYD-PM-009");
    }

    @Test
    void workspaceAssignMatchAndReplaceFollowProjectRegionRules() {
        ProjectView project = createProject();
        addMember(project.id(), CUSTOMER_MANAGER, "member-customer");
        addMember(project.id(), PROJECT_MANAGER, "member-manager");
        addMember(project.id(), PROJECT_ASSISTANT, "member-assistant");
        addMember(project.id(), REPLACEMENT_MANAGER, "member-replacement");

        var customer = teams.assign(operator(), metadata("assign-customer"),
                assignment(project.id(), "440305", ProjectPositionCode.CUSTOMER_SERVICE_MANAGER,
                        CUSTOMER_MANAGER, null, false, "南山区客户沟通由本区客服经理负责"));
        var manager = teams.assign(operator(), metadata("assign-manager"),
                assignment(project.id(), "440300", ProjectPositionCode.PROJECT_MANAGER,
                        PROJECT_MANAGER, null, true, "深圳市项目经理统一负责下辖行政区"));
        teams.assign(operator(), metadata("assign-assistant"),
                assignment(project.id(), "440300", ProjectPositionCode.PROJECT_ASSISTANT,
                        PROJECT_ASSISTANT, null, true, "深圳市项目助理统一支持下辖行政区"));

        var workspace = teams.workspace(operator(), "corr-workspace", project.id());
        assertThat(workspace.projectName()).isEqualTo("比亚迪华南家充安装服务项目");
        assertThat(workspace.members()).extracting(item -> item.displayName())
                .containsExactly("周雨桐", "陈昊", "林晓雯", "许文博");
        assertThat(workspace.assignments()).hasSize(3);
        assertThat(workspace.allowedActions()).containsExactly("ADD_MEMBER", "ASSIGN_REGION_PERSONNEL");
        assertThat(workspace.regions()).extracting(item -> item.code())
                .contains("440300", "440304", "440305");

        var match = teams.match(operator(), "corr-match", project.id(), "440305");
        assertThat(match.missingPositions()).isEmpty();
        assertThat(match.matches())
                .filteredOn(item -> item.position() == ProjectPositionCode.CUSTOMER_SERVICE_MANAGER)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.displayName()).isEqualTo("周雨桐");
                    assertThat(item.matchedRegionCode()).isEqualTo("440305");
                    assertThat(item.inherited()).isFalse();
                });

        var snapshotSource = personnelResolver.resolve(TENANT, project.id(), "440305", java.time.Instant.now());
        assertThat(snapshotSource.items())
                .filteredOn(item -> item.positionCode().equals(ProjectPositionCode.CUSTOMER_SERVICE_MANAGER.name()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.displayName()).isEqualTo("周雨桐");
                    assertThat(item.matchedRegionCode()).isEqualTo("440305");
                    assertThat(item.status()).isEqualTo("ASSIGNED");
                    assertThat(item.inherited()).isFalse();
                });
        assertThat(snapshotSource.items())
                .filteredOn(item -> item.positionCode().equals(ProjectPositionCode.PROJECT_MANAGER.name()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.displayName()).isEqualTo("陈昊");
                    assertThat(item.matchedRegionCode()).isEqualTo("440300");
                    assertThat(item.inherited()).isTrue();
                });
        assertThat(match.matches())
                .filteredOn(item -> item.position() == ProjectPositionCode.PROJECT_MANAGER)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.displayName()).isEqualTo("陈昊");
                    assertThat(item.matchedRegionCode()).isEqualTo("440300");
                    assertThat(item.inherited()).isTrue();
                });

        var replacement = teams.assign(operator(), metadata("replace-manager"),
                assignment(project.id(), "440300", ProjectPositionCode.PROJECT_MANAGER,
                        REPLACEMENT_MANAGER, manager.assignmentId(), true, "项目经理职责调整"));
        assertThat(replacement.displayName()).isEqualTo("许文博");
        assertThat(jdbc.sql("""
                SELECT assignment_status FROM prj_project_region_personnel_assignment
                 WHERE assignment_id = :assignmentId
                """).param("assignmentId", manager.assignmentId()).query(String.class).single())
                .isEqualTo("ENDED");
        assertThat(teams.match(operator(), "corr-match-replaced", project.id(), "440305").matches())
                .filteredOn(item -> item.position() == ProjectPositionCode.PROJECT_MANAGER)
                .singleElement()
                .extracting(item -> item.displayName())
                .isEqualTo("许文博");

        assertThatThrownBy(() -> teams.assign(operator(), metadata("stale-replace"),
                assignment(project.id(), "440300", ProjectPositionCode.PROJECT_MANAGER,
                        PROJECT_MANAGER, manager.assignmentId(), true, "使用陈旧分工覆盖")))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.VERSION_CONFLICT));
        assertThat(customer.dataComplete()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM aud_audit_record")
                .query(Long.class).single()).isEqualTo(9);
    }

    @Test
    void missingPositionAndNoInheritanceRemainExplicit() {
        ProjectView project = createProject();
        addMember(project.id(), CUSTOMER_MANAGER, "member-only");
        teams.assign(operator(), metadata("assign-no-inheritance"),
                assignment(project.id(), "440300", ProjectPositionCode.CUSTOMER_SERVICE_MANAGER,
                        CUSTOMER_MANAGER, null, false, "只负责深圳市级工单，不向下继承"));

        var match = teams.match(operator(), "corr-missing", project.id(), "440305");
        assertThat(match.matches()).isEmpty();
        assertThat(match.missingPositions()).containsExactlyInAnyOrder(
                ProjectPositionCode.CUSTOMER_SERVICE_MANAGER,
                ProjectPositionCode.PROJECT_MANAGER,
                ProjectPositionCode.PROJECT_ASSISTANT);
    }

    @Test
    void manageCommandsRequireDedicatedCapability() {
        ProjectView project = createProject();
        seedRole("reader", List.of("project.read"));

        assertThatThrownBy(() -> teams.addMember(
                reader(), metadata("denied-member"),
                new AddProjectTeamMemberCommand(project.id(), CUSTOMER_MANAGER)))
                .isInstanceOfSatisfying(BusinessProblem.class,
                        problem -> assertThat(problem.code()).isEqualTo(ProblemCode.ACCESS_DENIED));
        assertThat(jdbc.sql("SELECT decision_code FROM aud_audit_record ORDER BY occurred_at DESC LIMIT 1")
                .query(String.class).single()).isEqualTo("DENY");
    }

    private ProjectView createProject() {
        return projectCommands.create(operator(), metadata("create-project"), new CreateProjectCommand(
                "BYD-SOUTH-CHARGING", "client-byd", "比亚迪华南家充安装服务项目",
                LocalDate.of(2026, 7, 1), null, List.of("440300"), List.of()));
    }

    private void addMember(UUID projectId, UUID principalId, String key) {
        var first = teams.addMember(
                operator(), metadata(key), new AddProjectTeamMemberCommand(projectId, principalId));
        var replay = teams.addMember(
                operator(), metadata(key), new AddProjectTeamMemberCommand(projectId, principalId));
        assertThat(replay.memberId()).isEqualTo(first.memberId());
    }

    private static AssignProjectRegionPersonnelCommand assignment(
            UUID projectId,
            String regionCode,
            ProjectPositionCode position,
            UUID principalId,
            UUID expected,
            boolean inheritance,
            String reason
    ) {
        return new AssignProjectRegionPersonnelCommand(
                projectId, regionCode, position, principalId, expected, inheritance, reason);
    }

    private void seedPerson(UUID principalId, String name, String employeeNumber) {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:principalId, :tenantId, 'USER', 'ACTIVE', 1, now(), now())
                """).param("principalId", principalId).param("tenantId", TENANT).update();
        jdbc.sql("""
                INSERT INTO idn_person_profile (
                    principal_id, tenant_id, display_name, employee_number,
                    profile_version, created_at, updated_at, updated_by
                ) VALUES (:principalId, :tenantId, :name, :employeeNumber, 1, now(), now(), 'test')
                """)
                .param("principalId", principalId).param("tenantId", TENANT)
                .param("name", name).param("employeeNumber", employeeNumber).update();
    }

    private void seedRole(String principalId, List<String> capabilities) {
        UUID roleId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO auth_role (
                    role_id, tenant_id, role_code, role_name, role_status, created_at
                ) VALUES (:roleId, :tenantId, :roleCode, :roleCode, 'ACTIVE', now())
                """)
                .param("roleId", roleId).param("tenantId", TENANT)
                .param("roleCode", "role-" + principalId).update();
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
                    :grantId, :tenantId, :principalId, :roleId, 'TENANT', :tenantId,
                    now() - interval '1 day', 'TEST_FIXTURE', 'project-team-test', now()
                )
                """)
                .param("grantId", UUID.randomUUID()).param("tenantId", TENANT)
                .param("principalId", principalId).param("roleId", roleId).update();
    }

    private static CurrentPrincipal operator() {
        return principal("operator");
    }

    private static CurrentPrincipal reader() {
        return principal("reader");
    }

    private static CurrentPrincipal principal(String principalId) {
        return new CurrentPrincipal(
                principalId, TENANT, CurrentPrincipal.PrincipalType.USER, "project-team-it", Set.of());
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-project-team-" + key, "idem-project-team-" + key);
    }
}
