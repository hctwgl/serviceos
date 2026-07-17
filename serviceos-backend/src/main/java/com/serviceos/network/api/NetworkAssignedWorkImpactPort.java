package com.serviceos.network.api;

/**
 * 清退/停用影响统计端口，由 dispatch 等模块实现；network 不得直接读取其他模块内部表。
 */
public interface NetworkAssignedWorkImpactPort {
    NetworkWorkImpact summarizeForNetwork(String tenantId, String networkId);

    NetworkWorkImpact summarizeForTechnician(String tenantId, String technicianPrincipalId);
}
