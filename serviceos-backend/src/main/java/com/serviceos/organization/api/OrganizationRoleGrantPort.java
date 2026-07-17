package com.serviceos.organization.api;

import java.time.Instant;

/**
 * 任职终止时撤销主体有效 RoleGrant 的端口，由 authorization 模块实现。
 */
public interface OrganizationRoleGrantPort {
    int terminateActiveGrants(
            String tenantId, String principalId, Instant effectiveAt,
            String actorId, String reason, String correlationId);
}
