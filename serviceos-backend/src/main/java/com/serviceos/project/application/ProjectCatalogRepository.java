package com.serviceos.project.application;

import com.serviceos.project.api.ProjectClientDirectoryItem;
import com.serviceos.project.api.RegionCatalogItem;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProjectCatalogRepository {
    List<ProjectClientDirectoryItem> listClients(String tenantId, boolean activeOnly);

    Optional<ProjectClientDirectoryItem> findClient(String tenantId, String clientCode);

    void upsertClient(
            String tenantId, String clientCode, String displayName, String status, Instant now);

    /** 若不存在则登记；已存在时不覆盖运营维护的显示名。 */
    void ensureClient(String tenantId, String clientCode, String displayName, Instant now);

    Map<String, String> findClientDisplayNames(String tenantId, Collection<String> clientCodes);

    List<RegionCatalogItem> listRegions(String parentCode, String query, String level, int limit);

    Map<String, String> findRegionNames(Collection<String> regionCodes);
}
