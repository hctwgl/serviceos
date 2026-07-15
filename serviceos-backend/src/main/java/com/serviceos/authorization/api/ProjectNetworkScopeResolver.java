package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * NETWORK RoleGrant 到项目集合的权威解析端口。
 *
 * <p>授权模块只计算策略，不读取项目模块的关系表。项目目录按租户、精确网点引用和关系有效期
 * 返回项目集合；空集合必须由调用方失败关闭，不能扩大为租户全量。</p>
 */
public interface ProjectNetworkScopeResolver {
    Set<UUID> resolve(String tenantId, Set<String> networkIds, Instant effectiveAt);
}
