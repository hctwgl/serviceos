package com.serviceos.project.infrastructure;

import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.RegionCatalogItem;
import com.serviceos.project.application.ProjectCatalogRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
final class JdbcProjectCatalogRepository implements ProjectCatalogRepository {
    private final JdbcClient jdbc;

    JdbcProjectCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProjectClientDirectoryItem> listClients(String tenantId, boolean activeOnly) {
        String sql = """
                SELECT client_code, display_name, client_status
                  FROM prj_client_directory
                 WHERE tenant_id = :tenantId
                """ + (activeOnly ? " AND client_status = 'ACTIVE'" : "") + """
                 ORDER BY display_name, client_code
                """;
        return jdbc.sql(sql)
                .param("tenantId", tenantId)
                .query((rs, rowNum) -> new ProjectClientDirectoryItem(
                        rs.getString("client_code"),
                        rs.getString("display_name"),
                        rs.getString("client_status")))
                .list();
    }

    @Override
    public Optional<ProjectClientDirectoryItem> findClient(String tenantId, String clientCode) {
        return jdbc.sql("""
                SELECT client_code, display_name, client_status
                  FROM prj_client_directory
                 WHERE tenant_id = :tenantId
                   AND client_code = :clientCode
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
                .query((rs, rowNum) -> new ProjectClientDirectoryItem(
                        rs.getString("client_code"),
                        rs.getString("display_name"),
                        rs.getString("client_status")))
                .optional();
    }

    @Override
    public void upsertClient(
            String tenantId, String clientCode, String displayName, String status, Instant now
    ) {
        jdbc.sql("""
                INSERT INTO prj_client_directory (
                    tenant_id, client_code, display_name, client_status, created_at, updated_at
                ) VALUES (
                    :tenantId, :clientCode, :displayName, :status, :now, :now
                )
                ON CONFLICT (tenant_id, client_code) DO UPDATE
                   SET display_name = EXCLUDED.display_name,
                       client_status = EXCLUDED.client_status,
                       updated_at = EXCLUDED.updated_at
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
                .param("displayName", displayName)
                .param("status", status)
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    public void ensureClient(String tenantId, String clientCode, String displayName, Instant now) {
        jdbc.sql("""
                INSERT INTO prj_client_directory (
                    tenant_id, client_code, display_name, client_status, created_at, updated_at
                ) VALUES (
                    :tenantId, :clientCode, :displayName, 'ACTIVE', :now, :now
                )
                ON CONFLICT (tenant_id, client_code) DO NOTHING
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
                .param("displayName", displayName)
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    public Map<String, String> findClientDisplayNames(String tenantId, Collection<String> clientCodes) {
        if (clientCodes == null || clientCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        jdbc.sql("""
                SELECT client_code, display_name
                  FROM prj_client_directory
                 WHERE tenant_id = :tenantId
                   AND client_code IN (:codes)
                """)
                .param("tenantId", tenantId)
                .param("codes", clientCodes)
                .query((rs, rowNum) -> {
                    names.put(rs.getString("client_code"), rs.getString("display_name"));
                    return null;
                })
                .list();
        return names;
    }

    @Override
    public List<RegionCatalogItem> listRegions(String parentCode, String query, String level, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT region_code, parent_code, region_name, region_level, sort_order
                  FROM prj_region_catalog
                 WHERE region_status = 'ACTIVE'
                """);
        if (parentCode == null) {
            sql.append(" AND parent_code IS NULL");
        } else if (!"*".equals(parentCode)) {
            sql.append(" AND parent_code = :parentCode");
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (region_code ILIKE :query OR region_name ILIKE :query)");
        }
        if (level != null && !level.isBlank()) {
            sql.append(" AND region_level = :level");
        }
        sql.append(" ORDER BY sort_order, region_code LIMIT :limit");

        var spec = jdbc.sql(sql.toString()).param("limit", limit);
        if (parentCode != null && !"*".equals(parentCode)) {
            spec = spec.param("parentCode", parentCode);
        }
        if (query != null && !query.isBlank()) {
            spec = spec.param("query", "%" + query.trim() + "%");
        }
        if (level != null && !level.isBlank()) {
            spec = spec.param("level", level.trim());
        }
        return spec.query((rs, rowNum) -> new RegionCatalogItem(
                        rs.getString("region_code"),
                        rs.getString("parent_code"),
                        rs.getString("region_name"),
                        rs.getString("region_level"),
                        rs.getInt("sort_order")))
                .list();
    }

    @Override
    public Map<String, String> findRegionNames(Collection<String> regionCodes) {
        if (regionCodes == null || regionCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        jdbc.sql("""
                SELECT region_code, region_name
                  FROM prj_region_catalog
                 WHERE region_code IN (:codes)
                """)
                .param("codes", regionCodes)
                .query((rs, rowNum) -> {
                    names.put(rs.getString("region_code"), rs.getString("region_name"));
                    return null;
                })
                .list();
        return names;
    }
}
