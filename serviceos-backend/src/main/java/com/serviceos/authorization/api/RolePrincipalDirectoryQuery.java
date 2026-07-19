package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 按角色代码查询当前有效 USER 主体目录。
 *
 * <p>供 ASSIGNEE_POLICY 等配置运行时组装 {@code principalsByRoleCode} 快照；
 * 只读 ACTIVE ALLOW RoleGrant（TENANT 或指定 PROJECT 范围），DENY 优先排除。
 * 不得把结果当作长期缓存；调用方应在单次解析内使用。</p>
 */
public interface RolePrincipalDirectoryQuery {
    /**
     * @return roleCode → 去重保序的 principalId 列表；无命中角色不出现在 map 中
     */
    Map<String, List<String>> listActivePrincipalsGroupedByRoleCode(
            String tenantId,
            UUID projectId,
            Instant asOf);
}
