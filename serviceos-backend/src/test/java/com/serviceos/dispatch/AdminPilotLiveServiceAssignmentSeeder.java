package com.serviceos.dispatch;

import com.serviceos.ServiceOsApplication;
import com.serviceos.dispatch.api.ActivateServiceAssignmentCommand;
import com.serviceos.dispatch.api.CapacityAuthorityService;
import com.serviceos.dispatch.api.CompleteServiceAssignmentActivationCommand;
import com.serviceos.dispatch.api.ConfigureCapacityCommand;
import com.serviceos.dispatch.api.ConfirmTaskAssignmentPreparedCommand;
import com.serviceos.dispatch.api.PrepareServiceAssignmentCommand;
import com.serviceos.dispatch.api.ResponsibilityLevel;
import com.serviceos.dispatch.api.ServiceAssignmentReceipt;
import com.serviceos.dispatch.api.ServiceAssignmentService;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M143：对已运行的本地 Admin 试点库，经 Dispatch SPI 编排注入 ACTIVE ServiceAssignment。
 * <p>
 * 仅由冒烟脚本通过系统属性显式启用；默认 CI/verify 不会执行。
 * 不暴露 Admin 派单 HTTP，不宣称完整 {@code ADMIN-PILOT-09}。
 * 删除条件：Accepted 的 Manual Assign HTTP 落地且冒烟改为走该表面之后。
 */
@EnabledIfSystemProperty(named = "serviceos.admin.pilot.seed", matches = "true")
@SpringBootTest(classes = ServiceOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminPilotLiveServiceAssignmentSeeder {
    private static final String TENANT = "tenant-local";
    private static final String SEEDER = "admin-pilot-sa-seeder";
    private static final String BUSINESS_TYPE = "INSTALLATION";
    private static final String NETWORK_ID = "admin-pilot-network-1";
    private static final String TECHNICIAN_ID = "06b612f3-a901-4b0e-bd90-86b4259cc087";
    private static final UUID SEEDER_ROLE_ID = UUID.fromString("c3e8a1b2-4d5f-4a6b-8c9d-0e1f2a3b4c5d");
    private static final UUID SEEDER_GRANT_ID = UUID.fromString("d4f9b2c3-5e6a-4b7c-9d0e-1f2a3b4c5d6e");
    private static final int CAPACITY_HEADROOM = 50;

    @DynamicPropertySource
    static void livePilotDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> System.getProperty("serviceos.admin.pilot.dbUrl",
                        System.getenv().getOrDefault("SERVICEOS_DB_URL",
                                "jdbc:postgresql://127.0.0.1:5432/serviceos")));
        registry.add("spring.datasource.username",
                () -> System.getProperty("serviceos.admin.pilot.dbUsername",
                        System.getenv().getOrDefault("SERVICEOS_DB_USERNAME", "serviceos_app")));
        registry.add("spring.datasource.password",
                () -> System.getProperty("serviceos.admin.pilot.dbPassword",
                        System.getenv().getOrDefault("SERVICEOS_DB_PASSWORD", "serviceos_app")));
        // 与已运行 Backend 共享库时禁止第二套调度抢占 Outbox/Task。
        registry.add("serviceos.outbox.scheduling-enabled", () -> "false");
        registry.add("serviceos.task.scheduling-enabled", () -> "false");
        registry.add("serviceos.sla.scheduling-enabled", () -> "false");
        registry.add("serviceos.dispatch.activation-timeout-scheduling-enabled", () -> "false");
    }

    @Autowired CapacityAuthorityService capacities;
    @Autowired ServiceAssignmentService assignments;
    @Autowired JdbcClient jdbc;

    @Test
    void seedNetworkAndTechnicianViaSpi() {
        UUID workOrderId = requiredUuid("serviceos.admin.pilot.workOrderId");
        UUID taskId = requiredUuid("serviceos.admin.pilot.taskId");
        String runKey = System.getProperty("serviceos.admin.pilot.runKey", taskId.toString());

        ensureSeederGrant();
        ensureCapacity(ResponsibilityLevel.NETWORK, NETWORK_ID, runKey + "-network-cap");
        ensureCapacity(ResponsibilityLevel.TECHNICIAN, TECHNICIAN_ID, runKey + "-technician-cap");

        ActivatedAssignment network = activateAndComplete(
                workOrderId, taskId, ResponsibilityLevel.NETWORK, NETWORK_ID, runKey + "-network");
        ActivatedAssignment technician = activateAndComplete(
                workOrderId, taskId, ResponsibilityLevel.TECHNICIAN, TECHNICIAN_ID,
                runKey + "-technician");

        assertThat(jdbc.sql("""
                        SELECT responsibility_level || ':' || assignee_id || ':' || status
                          FROM dsp_service_assignment
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                         ORDER BY responsibility_level
                        """)
                .param("tenantId", TENANT).param("taskId", taskId)
                .query(String.class).list())
                .containsExactly(
                        "NETWORK:" + NETWORK_ID + ":ACTIVE",
                        "TECHNICIAN:" + TECHNICIAN_ID + ":ACTIVE");
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_capacity_reservation
                         WHERE tenant_id = :tenantId
                           AND service_assignment_id IN (:networkId, :technicianId)
                           AND status = 'CONFIRMED'
                        """)
                .param("tenantId", TENANT)
                .param("networkId", network.receipt().serviceAssignmentId())
                .param("technicianId", technician.receipt().serviceAssignmentId())
                .query(Long.class).single())
                .isEqualTo(2L);
        assertThat(jdbc.sql("""
                        SELECT count(*) FROM dsp_service_assignment_activation_saga
                         WHERE tenant_id = :tenantId AND task_id = :taskId
                           AND stage = 'COMPLETED'
                        """)
                .param("tenantId", TENANT).param("taskId", taskId)
                .query(Long.class).single())
                .isEqualTo(2L);
    }

    private ActivatedAssignment activateAndComplete(
            UUID workOrderId,
            UUID taskId,
            ResponsibilityLevel level,
            String assigneeId,
            String key
    ) {
        long capacityVersion = capacityVersion(level, assigneeId);
        ServiceAssignmentReceipt pending = assignments.prepare(
                seeder(), metadata(key + "-prepare"),
                new PrepareServiceAssignmentCommand(
                        UUID.randomUUID(), workOrderId, taskId, level, assigneeId, BUSINESS_TYPE,
                        "decision://admin-pilot/" + key, null, null, capacityVersion));
        UUID preparedId = UUID.randomUUID();
        assignments.confirmTaskPrepared(
                seeder(), metadata(key + "-task-prepared"),
                new ConfirmTaskAssignmentPreparedCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), taskId,
                        UUID.randomUUID(), preparedId, 1));
        ServiceAssignmentReceipt activated = assignments.activate(
                seeder(), metadata(key + "-activate"),
                new ActivateServiceAssignmentCommand(
                        pending.sagaId(), pending.serviceAssignmentId(), 2,
                        "authority://admin-pilot/" + assigneeId, 1,
                        "fence://admin-pilot/" + assigneeId, "admin-pilot-geo-v1"));
        ServiceAssignmentReceipt completed = assignments.complete(
                seeder(), metadata(key + "-complete"),
                new CompleteServiceAssignmentActivationCommand(
                        activated.sagaId(), activated.serviceAssignmentId(), preparedId, 3));
        assertThat(completed.assignmentStatus()).isEqualTo("ACTIVE");
        assertThat(completed.sagaStage()).isEqualTo("COMPLETED");
        return new ActivatedAssignment(completed, preparedId);
    }

    private void ensureCapacity(ResponsibilityLevel level, String assigneeId, String key) {
        var existing = jdbc.sql("""
                        SELECT max_units, occupied_units, version
                          FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND responsibility_level = :level
                           AND assignee_id = :assigneeId AND business_type = :businessType
                        """)
                .param("tenantId", TENANT)
                .param("level", level.name())
                .param("assigneeId", assigneeId)
                .param("businessType", BUSINESS_TYPE)
                .query(CapacityRow.class).optional();
        if (existing.isEmpty()) {
            capacities.configure(seeder(), metadata(key + "-create"),
                    new ConfigureCapacityCommand(level, assigneeId, BUSINESS_TYPE, CAPACITY_HEADROOM, 0));
            return;
        }
        CapacityRow row = existing.get();
        if (row.maxUnits() - row.occupiedUnits() >= 1) {
            return;
        }
        int nextMax = Math.max(row.maxUnits() + CAPACITY_HEADROOM, row.occupiedUnits() + CAPACITY_HEADROOM);
        capacities.configure(seeder(), metadata(key + "-expand-" + UUID.randomUUID()),
                new ConfigureCapacityCommand(level, assigneeId, BUSINESS_TYPE, nextMax, row.version()));
    }

    private long capacityVersion(ResponsibilityLevel level, String assigneeId) {
        return jdbc.sql("""
                        SELECT version FROM dsp_capacity_counter
                         WHERE tenant_id = :tenantId AND responsibility_level = :level
                           AND assignee_id = :assigneeId AND business_type = :businessType
                        """)
                .param("tenantId", TENANT)
                .param("level", level.name())
                .param("assigneeId", assigneeId)
                .param("businessType", BUSINESS_TYPE)
                .query(Long.class).single();
    }

    private void ensureSeederGrant() {
        jdbc.sql("""
                        INSERT INTO auth_role (
                            role_id, tenant_id, role_code, role_name, role_status, created_at
                        ) VALUES (
                            :roleId, :tenantId, 'admin-pilot-sa-seeder', 'Admin 试点 SA SPI 种子',
                            'ACTIVE', now()
                        ) ON CONFLICT (tenant_id, role_code) DO NOTHING
                        """)
                .param("roleId", SEEDER_ROLE_ID).param("tenantId", TENANT).update();
        UUID roleId = jdbc.sql("""
                        SELECT role_id FROM auth_role
                         WHERE tenant_id = :tenantId AND role_code = 'admin-pilot-sa-seeder'
                        """)
                .param("tenantId", TENANT).query(UUID.class).single();
        for (String capability : Set.of(
                "dispatch.capacity.configure", "dispatch.assignment.manage")) {
            jdbc.sql("""
                            INSERT INTO auth_role_capability (role_id, capability_code, granted_at)
                            VALUES (:roleId, :capability, now())
                            ON CONFLICT (role_id, capability_code) DO NOTHING
                            """)
                    .param("roleId", roleId).param("capability", capability).update();
        }
        jdbc.sql("""
                        INSERT INTO auth_role_grant (
                            grant_id, tenant_id, principal_id, role_id, scope_type, scope_ref,
                            valid_from, source_code, approval_ref, created_at
                        ) VALUES (
                            :grantId, :tenantId, :principalId, :roleId, 'TENANT', :tenantId,
                            now() - interval '1 day', 'ADMIN_PILOT_SPI_SEED', 'M143', now()
                        ) ON CONFLICT (grant_id) DO NOTHING
                        """)
                .param("grantId", SEEDER_GRANT_ID).param("tenantId", TENANT)
                .param("principalId", SEEDER).param("roleId", roleId).update();
    }

    private static CurrentPrincipal seeder() {
        return new CurrentPrincipal(
                SEEDER, TENANT, CurrentPrincipal.PrincipalType.SERVICE, "admin-pilot-seed",
                Set.of("dispatch.capacity.configure", "dispatch.assignment.manage"));
    }

    private static CommandMetadata metadata(String key) {
        return new CommandMetadata("corr-" + key, key);
    }

    private static UUID requiredUuid(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when seeding Admin pilot SA");
        }
        return UUID.fromString(value.trim());
    }

    private record CapacityRow(int maxUnits, int occupiedUnits, long version) {
    }

    private record ActivatedAssignment(
            ServiceAssignmentReceipt receipt,
            UUID preparedTaskAssignmentId
    ) {
    }
}
