package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 查询主体当前有效 RoleGrant 的角色 ID。供共享偏好可见性等跨模块读端口使用；
 * 不暴露授权细节或 capability 判定结果。
 */
public interface PrincipalActiveRoleQuery {
    /** 返回 tenant 内 ACTIVE 且未过期/未撤销的 roleId 集合。 */
    Set<UUID> listActiveRoleIds(String tenantId, String principalId, Instant evaluatedAt);

    /** 同租户 ACTIVE 角色是否存在（用于 share ROLE 目标校验）。 */
    boolean roleExists(String tenantId, UUID roleId);
}
