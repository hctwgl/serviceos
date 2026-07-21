package com.serviceos.project.infrastructure;

import com.serviceos.project.api.ProjectClientBrandItem;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Repository
final class JdbcProjectCatalogRepository implements ProjectCatalogRepository {
    private final JdbcClient jdbc;

    JdbcProjectCatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProjectClientDirectoryItem> listClients(String tenantId, String statusFilter) {
        String normalized = normalizeStatusFilter(statusFilter);
        String sql = """
                SELECT client_code, display_name, client_status
                  FROM prj_client_directory
                 WHERE tenant_id = :tenantId
                """ + (normalized == null ? "" : " AND client_status = :status") + """
                 ORDER BY display_name, client_code
                """;
        var spec = jdbc.sql(sql).param("tenantId", tenantId);
        if (normalized != null) {
            spec = spec.param("status", normalized);
        }
        return spec.query((rs, rowNum) -> new ProjectClientDirectoryItem(
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
    public void updateClientStatus(String tenantId, String clientCode, String status, Instant now) {
        jdbc.sql("""
                UPDATE prj_client_directory
                   SET client_status = :status,
                       updated_at = :now
                 WHERE tenant_id = :tenantId
                   AND client_code = :clientCode
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
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
    public List<ProjectClientBrandItem> listBrands(
            String tenantId, String clientCode, String statusFilter
    ) {
        String normalized = normalizeStatusFilter(statusFilter);
        String sql = """
                SELECT client_code, brand_code, display_name, brand_status, sort_order
                  FROM prj_client_brand
                 WHERE tenant_id = :tenantId
                   AND client_code = :clientCode
                """ + (normalized == null ? "" : " AND brand_status = :status") + """
                 ORDER BY sort_order, brand_code
                """;
        var spec = jdbc.sql(sql)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode);
        if (normalized != null) {
            spec = spec.param("status", normalized);
        }
        return spec.query((rs, rowNum) -> new ProjectClientBrandItem(
                        rs.getString("client_code"),
                        rs.getString("brand_code"),
                        rs.getString("display_name"),
                        rs.getString("brand_status"),
                        rs.getInt("sort_order")))
                .list();
    }

    @Override
    public Optional<ProjectClientBrandItem> findBrand(
            String tenantId, String clientCode, String brandCode
    ) {
        return jdbc.sql("""
                SELECT client_code, brand_code, display_name, brand_status, sort_order
                  FROM prj_client_brand
                 WHERE tenant_id = :tenantId
                   AND client_code = :clientCode
                   AND brand_code = :brandCode
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
                .param("brandCode", brandCode)
                .query((rs, rowNum) -> new ProjectClientBrandItem(
                        rs.getString("client_code"),
                        rs.getString("brand_code"),
                        rs.getString("display_name"),
                        rs.getString("brand_status"),
                        rs.getInt("sort_order")))
                .optional();
    }

    @Override
    public void upsertBrand(
            String tenantId,
            String clientCode,
            String brandCode,
            String displayName,
            String status,
            int sortOrder,
            Instant now
    ) {
        jdbc.sql("""
                INSERT INTO prj_client_brand (
                    tenant_id, client_code, brand_code, display_name, brand_status,
                    sort_order, created_at, updated_at
                ) VALUES (
                    :tenantId, :clientCode, :brandCode, :displayName, :status,
                    :sortOrder, :now, :now
                )
                ON CONFLICT (tenant_id, client_code, brand_code) DO UPDATE
                   SET display_name = EXCLUDED.display_name,
                       brand_status = EXCLUDED.brand_status,
                       sort_order = EXCLUDED.sort_order,
                       updated_at = EXCLUDED.updated_at
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
                .param("brandCode", brandCode)
                .param("displayName", displayName)
                .param("status", status)
                .param("sortOrder", sortOrder)
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    public void updateBrandStatus(
            String tenantId, String clientCode, String brandCode, String status, Instant now
    ) {
        jdbc.sql("""
                UPDATE prj_client_brand
                   SET brand_status = :status,
                       updated_at = :now
                 WHERE tenant_id = :tenantId
                   AND client_code = :clientCode
                   AND brand_code = :brandCode
                """)
                .param("tenantId", tenantId)
                .param("clientCode", clientCode)
                .param("brandCode", brandCode)
                .param("status", status)
                .param("now", Timestamp.from(now))
                .update();
    }

    @Override
    public List<RegionCatalogItem> listRegions(String parentCode, String query, String level, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT r.region_code,
                       r.parent_code,
                       r.region_name,
                       r.region_level,
                       r.sort_order,
                       (
                         SELECT COUNT(*)::int
                           FROM prj_region_catalog c
                          WHERE c.parent_code = r.region_code
                            AND c.region_status = 'ACTIVE'
                       ) AS child_count
                  FROM prj_region_catalog r
                 WHERE r.region_status = 'ACTIVE'
                """);
        if (parentCode == null) {
            sql.append(" AND r.parent_code IS NULL");
        } else if (!"*".equals(parentCode)) {
            sql.append(" AND r.parent_code = :parentCode");
        }
        if (query != null && !query.isBlank()) {
            sql.append(" AND (r.region_code ILIKE :query OR r.region_name ILIKE :query)");
        }
        if (level != null && !level.isBlank()) {
            sql.append(" AND r.region_level = :level");
        }
        sql.append(" ORDER BY r.sort_order, r.region_code LIMIT :limit");

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
                        rs.getInt("sort_order"),
                        rs.getInt("child_count")))
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

    private static String normalizeStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank() || "ALL".equalsIgnoreCase(statusFilter)) {
            return null;
        }
        String normalized = statusFilter.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(normalized) && !"DISABLED".equals(normalized)) {
            throw new IllegalArgumentException("status is invalid");
        }
        return normalized;
    }
}
