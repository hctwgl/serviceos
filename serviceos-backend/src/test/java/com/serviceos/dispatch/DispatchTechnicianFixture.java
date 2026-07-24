package com.serviceos.dispatch;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.UUID;

/** 调度集成测试中的师傅档案与登录主体事实。 */
final class DispatchTechnicianFixture {
    private DispatchTechnicianFixture() {
    }

    static void seed(
            JdbcClient jdbc,
            String tenantId,
            UUID technicianProfileId,
            UUID principalId,
            String displayName
    ) {
        jdbc.sql("""
                INSERT INTO idn_security_principal (
                    principal_id, tenant_id, principal_type, principal_status,
                    aggregate_version, created_at, updated_at
                ) VALUES (:principalId, :tenantId, 'USER', 'ACTIVE', 1, now(), now())
                """)
                .param("principalId", principalId)
                .param("tenantId", tenantId)
                .update();
        jdbc.sql("""
                INSERT INTO net_technician_profile (
                    technician_profile_id, tenant_id, principal_id, display_name,
                    profile_status, aggregate_version, created_at, updated_at
                ) VALUES (
                    :profileId, :tenantId, :principalId, :displayName,
                    'ACTIVE', 1, now(), now()
                )
                """)
                .param("profileId", technicianProfileId)
                .param("tenantId", tenantId)
                .param("principalId", principalId)
                .param("displayName", displayName)
                .update();
    }
}
