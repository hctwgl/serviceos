package com.serviceos.project.infrastructure;

import com.serviceos.project.api.ProjectNetworkDirectoryQuery;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/** 项目目录 → 有效 NETWORK 绑定。 */
@Component
final class JdbcProjectNetworkDirectoryQuery implements ProjectNetworkDirectoryQuery {
    private final JdbcClient jdbc;

    JdbcProjectNetworkDirectoryQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<String> listActiveNetworkIds(String tenantId, UUID projectId, Instant asOf) {
        String safeTenant = Objects.requireNonNull(tenantId, "tenantId").trim();
        Objects.requireNonNull(projectId, "projectId");
        Instant evaluatedAt = Objects.requireNonNull(asOf, "asOf");
        return List.copyOf(jdbc.sql("""
                SELECT network_id
                  FROM prj_project_network
                 WHERE tenant_id = :tenantId
                   AND project_id = :projectId
                   AND valid_from <= :asOf
                   AND (valid_to IS NULL OR valid_to > :asOf)
                 ORDER BY network_id ASC
                """)
                .param("tenantId", safeTenant)
                .param("projectId", projectId)
                .param("asOf", timestamptz(evaluatedAt))
                .query(String.class)
                .list());
    }
}
