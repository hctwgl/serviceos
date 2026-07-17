package com.serviceos.readmodel.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 个人 UI Preference 持久化端口。 */
public interface UiPreferenceRepository {
    List<UiPreferenceRecord> listByOwner(String tenantId, String principalId, String portal);

    Optional<UiPreferenceRecord> findOwned(
            String tenantId, String principalId, String portal, String preferenceKey);

    void insert(UiPreferenceRecord record);

    boolean update(UiPreferenceRecord record, long expectedVersion);

    boolean deleteOwned(String tenantId, String principalId, String portal, String preferenceKey);

    record UiPreferenceRecord(
            String tenantId,
            String principalId,
            String portal,
            String preferenceKey,
            String valueJson,
            int schemaVersion,
            long aggregateVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
