package com.serviceos.readmodel.application;

import com.serviceos.readmodel.api.SavedView;
import com.serviceos.readmodel.api.SavedViewVisibility;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedViewRepository {
    List<SavedView> listVisible(
            String tenantId,
            String principalId,
            String portal,
            String pageId,
            Collection<String> activeRoleIds
    );

    Optional<SavedViewRecord> findOwned(String tenantId, String principalId, UUID savedViewId);

    void insert(SavedViewRecord record);

    boolean update(SavedViewRecord record, long expectedVersion);

    boolean updateVisibility(
            String tenantId,
            String principalId,
            UUID savedViewId,
            long expectedVersion,
            SavedViewVisibility visibility,
            String sharedScopeRef,
            long nextVersion,
            java.time.Instant updatedAt
    );

    boolean deleteOwned(String tenantId, String principalId, UUID savedViewId);

    void clearDefault(String tenantId, String principalId, String portal, String pageId);

    public record SavedViewRecord(
            UUID id,
            String tenantId,
            String principalId,
            String portal,
            String pageId,
            String name,
            SavedViewVisibility visibility,
            String sharedScopeRef,
            int schemaVersion,
            String filterJson,
            String sortJson,
            String columnJson,
            boolean isDefault,
            long aggregateVersion,
            java.time.Instant createdAt,
            java.time.Instant updatedAt
    ) {
    }
}
