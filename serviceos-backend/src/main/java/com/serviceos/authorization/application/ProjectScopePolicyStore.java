package com.serviceos.authorization.application;

import java.time.Instant;

/** authorization 模块内部的项目集合 RoleGrant 查询端口。 */
public interface ProjectScopePolicyStore {
    ProjectScopeGrantMatch findProjectScopeGrants(
            String tenantId, String principalId, String capability, Instant evaluatedAt);
}
