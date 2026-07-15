package com.serviceos.project.infrastructure;

import com.serviceos.authorization.api.ProjectRegionScopeResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/**
 * 项目目录提供的 REGION 权威映射。查询同时约束 tenant、关系生效区间和精确 region_code，
 * 不使用地址前缀推断，不把空结果扩大为租户全量。
 */
@Component
final class JdbcProjectRegionScopeResolver implements ProjectRegionScopeResolver {
    private final JdbcClient jdbc;

    JdbcProjectRegionScopeResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> resolve(String tenantId, Set<String> regionCodes, Instant effectiveAt) {
        if (regionCodes.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.sql("""
                        SELECT DISTINCT project_id
                          FROM prj_project_region
                         WHERE tenant_id = :tenantId
                           AND region_code IN (:regionCodes)
                           AND valid_from <= :effectiveAt
                           AND (valid_to IS NULL OR valid_to > :effectiveAt)
                         ORDER BY project_id
                        """)
                .param("tenantId", tenantId)
                .param("regionCodes", regionCodes)
                .param("effectiveAt", timestamptz(effectiveAt))
                .query(UUID.class)
                .list());
    }
}
