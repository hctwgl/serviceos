package com.serviceos.project.infrastructure;

import com.serviceos.authorization.api.ProjectNetworkScopeResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 项目目录提供的 NETWORK 权威映射。只有项目创建命令显式登记且当前有效的关系能够授权，
 * 不从 ServiceAssignment、工单地址、网点名称或区域覆盖反推长期项目权限。
 */
@Component
final class JdbcProjectNetworkScopeResolver implements ProjectNetworkScopeResolver {
    private final JdbcClient jdbc;

    JdbcProjectNetworkScopeResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> resolve(String tenantId, Set<String> networkIds, Instant effectiveAt) {
        if (networkIds.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.sql("""
                        SELECT DISTINCT project_id
                          FROM prj_project_network
                         WHERE tenant_id = :tenantId
                           AND network_id IN (:networkIds)
                           AND valid_from <= :effectiveAt
                           AND (valid_to IS NULL OR valid_to > :effectiveAt)
                         ORDER BY project_id
                        """)
                .param("tenantId", tenantId)
                .param("networkIds", networkIds)
                .param("effectiveAt", timestamptz(effectiveAt))
                .query(UUID.class)
                .list());
    }
}
