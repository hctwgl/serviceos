package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * REGION RoleGrant 到项目集合的权威解析端口。
 *
 * <p>授权模块只拥有策略计算，不得跨模块读取项目表；项目目录负责实现本端口，并按租户与
 * 关系有效期返回精确集合。空集合表示当前没有匹配项目，调用方必须失败关闭。</p>
 */
public interface ProjectRegionScopeResolver {
    Set<UUID> resolve(String tenantId, Set<String> regionCodes, Instant effectiveAt);
}
