package com.serviceos.project.application;

import com.serviceos.project.api.ProjectClientBrandItem;
import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.RegionCatalogItem;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProjectCatalogRepository {
    /**
     * @param statusFilter ACTIVE / DISABLED；null 或 ALL 表示全部
     */
    List<ProjectClientDirectoryItem> listClients(String tenantId, String statusFilter);

    Optional<ProjectClientDirectoryItem> findClient(String tenantId, String clientCode);

    void upsertClient(
            String tenantId, String clientCode, String displayName, String status, Instant now);

    void updateClientStatus(String tenantId, String clientCode, String status, Instant now);

    /** 若不存在则登记；已存在时不覆盖运营维护的显示名。 */
    void ensureClient(String tenantId, String clientCode, String displayName, Instant now);

    Map<String, String> findClientDisplayNames(String tenantId, Collection<String> clientCodes);

    List<ProjectClientBrandItem> listBrands(String tenantId, String clientCode, String statusFilter);

    Optional<ProjectClientBrandItem> findBrand(String tenantId, String clientCode, String brandCode);

    void upsertBrand(
            String tenantId,
            String clientCode,
            String brandCode,
            String displayName,
            String status,
            int sortOrder,
            Instant now
    );

    void updateBrandStatus(
            String tenantId, String clientCode, String brandCode, String status, Instant now);

    List<RegionCatalogItem> listRegions(String parentCode, String query, String level, int limit);

    Map<String, String> findRegionNames(Collection<String> regionCodes);
}
