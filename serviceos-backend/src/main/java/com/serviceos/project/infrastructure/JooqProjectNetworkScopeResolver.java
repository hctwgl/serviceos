package com.serviceos.project.infrastructure;

import com.serviceos.authorization.api.ProjectNetworkScopeResolver;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.PrjProjectNetwork.PRJ_PROJECT_NETWORK;

/**
 * 项目目录提供的 NETWORK 权威映射。只有项目创建命令显式登记且当前有效的关系能够授权，
 * 不从 ServiceAssignment、工单地址、网点名称或区域覆盖反推长期项目权限。
 */
@Component
final class JooqProjectNetworkScopeResolver implements ProjectNetworkScopeResolver {
    private final DSLContext dsl;

    JooqProjectNetworkScopeResolver(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Set<UUID> resolve(String tenantId, Set<String> networkIds, Instant effectiveAt) {
        if (networkIds.isEmpty()) {
            return Set.of();
        }
        var n = PRJ_PROJECT_NETWORK;
        return new LinkedHashSet<>(dsl.selectDistinct(n.PROJECT_ID)
                .from(n)
                .where(n.TENANT_ID.eq(tenantId))
                .and(n.NETWORK_ID.in(networkIds))
                .and(n.VALID_FROM.le(effectiveAt))
                .and(n.VALID_TO.isNull().or(n.VALID_TO.gt(effectiveAt)))
                .orderBy(n.PROJECT_ID)
                .fetch(n.PROJECT_ID));
    }
}
