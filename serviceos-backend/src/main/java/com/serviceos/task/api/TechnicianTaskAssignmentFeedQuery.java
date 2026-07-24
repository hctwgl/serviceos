package com.serviceos.task.api;

import java.util.List;

/**
 * 按师傅 principal 列出 ACTIVE/REVOKED 候选或责任 TaskAssignment（供 Technician Portal feed）。
 * 调用方负责 Portal 上下文与 capability，并自行按网点收敛。
 */
public interface TechnicianTaskAssignmentFeedQuery {
    List<TechnicianTaskAssignmentFeedView> listActiveForPrincipal(String tenantId, String principalId);

    List<TechnicianTaskAssignmentFeedView> listRevokedForPrincipal(String tenantId, String principalId);
}
