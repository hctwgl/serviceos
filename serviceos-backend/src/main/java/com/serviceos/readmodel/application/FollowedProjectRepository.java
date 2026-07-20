package com.serviceos.readmodel.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowedProjectRepository {
    List<FollowedProjectRecord> listByOwnerOrdered(
            String tenantId, String principalId, String portal, int fetchLimit);

    Optional<FollowedProjectRecord> findOwned(
            String tenantId, String principalId, String portal, UUID projectId);

    void upsert(FollowedProjectRecord record);

    void deleteOwned(String tenantId, String principalId, String portal, UUID projectId);

    record FollowedProjectRecord(
            String tenantId,
            String principalId,
            String portal,
            UUID projectId,
            String displayRef,
            Instant followedAt,
            Instant createdAt
    ) {
    }
}
