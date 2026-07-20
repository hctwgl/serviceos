package com.serviceos.project.infrastructure;

import com.serviceos.project.api.ProjectClientOption;
import com.serviceos.project.api.ProjectRegionOption;
import com.serviceos.project.application.ProjectReferenceOptionsRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 项目选择器选项聚合。SQL 内收敛 tenant 与授权 project 范围。
 */
@Repository
final class JdbcProjectReferenceOptionsRepository implements ProjectReferenceOptionsRepository {
    private final JdbcClient jdbc;

    JdbcProjectReferenceOptionsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProjectClientOption> listClients(String tenantId, boolean tenantWide, Collection<UUID> projectIds) {
        if (!tenantWide && (projectIds == null || projectIds.isEmpty())) {
            return List.of();
        }
        if (tenantWide) {
            return jdbc.sql("""
                    SELECT client_id, COUNT(*)::int AS project_count
                      FROM prj_project
                     WHERE tenant_id = :tenantId
                     GROUP BY client_id
                     ORDER BY client_id
                    """)
                    .param("tenantId", tenantId)
                    .query((rs, rowNum) -> new ProjectClientOption(
                            rs.getString("client_id"), rs.getString("client_id"), rs.getInt("project_count")))
                    .list();
        }
        return jdbc.sql("""
                SELECT client_id, COUNT(*)::int AS project_count
                  FROM prj_project
                 WHERE tenant_id = :tenantId
                   AND project_id IN (:projectIds)
                 GROUP BY client_id
                 ORDER BY client_id
                """)
                .param("tenantId", tenantId)
                .param("projectIds", projectIds)
                .query((rs, rowNum) -> new ProjectClientOption(
                        rs.getString("client_id"), rs.getString("client_id"), rs.getInt("project_count")))
                .list();
    }

    @Override
    public List<ProjectRegionOption> listRegions(String tenantId, boolean tenantWide, Collection<UUID> projectIds) {
        if (!tenantWide && (projectIds == null || projectIds.isEmpty())) {
            return List.of();
        }
        if (tenantWide) {
            return jdbc.sql("""
                    SELECT region_code, COUNT(DISTINCT project_id)::int AS project_count
                      FROM prj_project_region
                     WHERE tenant_id = :tenantId
                       AND valid_to IS NULL
                     GROUP BY region_code
                     ORDER BY region_code
                    """)
                    .param("tenantId", tenantId)
                    .query((rs, rowNum) -> new ProjectRegionOption(
                            rs.getString("region_code"), rs.getString("region_code"), rs.getInt("project_count")))
                    .list();
        }
        return jdbc.sql("""
                SELECT region_code, COUNT(DISTINCT project_id)::int AS project_count
                  FROM prj_project_region
                 WHERE tenant_id = :tenantId
                   AND valid_to IS NULL
                   AND project_id IN (:projectIds)
                 GROUP BY region_code
                 ORDER BY region_code
                """)
                .param("tenantId", tenantId)
                .param("projectIds", projectIds)
                .query((rs, rowNum) -> new ProjectRegionOption(
                        rs.getString("region_code"), rs.getString("region_code"), rs.getInt("project_count")))
                .list();
    }
}
