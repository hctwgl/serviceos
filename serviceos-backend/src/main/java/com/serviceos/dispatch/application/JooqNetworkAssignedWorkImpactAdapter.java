package com.serviceos.dispatch.application;

import com.serviceos.network.api.NetworkAssignedWorkImpactPort;
import com.serviceos.network.api.NetworkWorkImpact;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import static com.serviceos.jooq.generated.tables.DspServiceAssignment.DSP_SERVICE_ASSIGNMENT;

/**
 * 清退影响统计：当前仅统计 dispatch 模块拥有的 ACTIVE ServiceAssignment。
 * openTasks/openAppointments/openVisits/offlinePackages 尚未建模，暂返回 0。
 */
@Primary
@Component
final class JooqNetworkAssignedWorkImpactAdapter implements NetworkAssignedWorkImpactPort {
    private final DSLContext dsl;

    JooqNetworkAssignedWorkImpactAdapter(DSLContext dsl) {
        this.dsl = dsl;
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
        return dsl.fetchCount(DSP_SERVICE_ASSIGNMENT,
                DSP_SERVICE_ASSIGNMENT.TENANT_ID.eq(tenantId)
                        .and(DSP_SERVICE_ASSIGNMENT.RESPONSIBILITY_LEVEL.eq(level))
                        .and(DSP_SERVICE_ASSIGNMENT.ASSIGNEE_ID.eq(assigneeId))
                        .and(DSP_SERVICE_ASSIGNMENT.STATUS.eq("ACTIVE")));
    }
}
