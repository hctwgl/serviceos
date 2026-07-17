package com.serviceos.dispatch.application;

import com.serviceos.network.api.NetworkAssignedWorkImpactPort;
import com.serviceos.network.api.NetworkWorkImpact;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 清退影响统计：当前仅统计 dispatch 模块拥有的 ACTIVE ServiceAssignment。
 * openTasks/openAppointments/openVisits/offlinePackages 尚未建模，暂返回 0。
 */
@Primary
@Component
final class DefaultNetworkAssignedWorkImpactAdapter implements NetworkAssignedWorkImpactPort {
    private final JdbcClient jdbc;

    DefaultNetworkAssignedWorkImpactAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public NetworkWorkImpact summarizeForNetwork(String tenantId, String networkId) {
        int activeAssignments = countAssignments(tenantId, "NETWORK", networkId);
        // 离线工作包与其他模块未完成工作尚未接入统一影响端口。
        return new NetworkWorkImpact(0, 0, 0, activeAssignments, 0);
    }

    @Override
    public NetworkWorkImpact summarizeForTechnician(String tenantId, String technicianPrincipalId) {
        int activeAssignments = countAssignments(tenantId, "TECHNICIAN", technicianPrincipalId);
        return new NetworkWorkImpact(0, 0, 0, activeAssignments, 0);
    }

    private int countAssignments(String tenantId, String level, String assigneeId) {
        Integer count = jdbc.sql("""
                        SELECT count(*)::int
                          FROM dsp_service_assignment
                         WHERE tenant_id=:tenant AND responsibility_level=:level
                           AND assignee_id=:assigneeId AND status='ACTIVE'
                        """)
                .param("tenant", tenantId).param("level", level).param("assigneeId", assigneeId)
                .query(Integer.class).single();
        return count == null ? 0 : count;
    }
}
