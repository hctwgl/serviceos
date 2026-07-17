package com.serviceos.readmodel.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 个人最近访问持久化端口。 */
public interface RecentResourceRepository {
    List<RecentResourceRecord> listByOwnerOrdered(
            String tenantId, String principalId, String portal, int fetchLimit);

    Optional<RecentResourceRecord> findOwned(
            String tenantId,
            String principalId,
            String portal,
            String resourceType,
            String resourceId
    );

    void upsert(RecentResourceRecord record);

    boolean deleteOwned(
            String tenantId,
            String principalId,
            String portal,
            String resourceType,
            String resourceId
    );

    /** 删除超出上限的最旧行；返回删除条数。 */
    int trimExcess(String tenantId, String principalId, String portal, int keepLimit);

    record RecentResourceRecord(
            String tenantId,
            String principalId,
            String portal,
            String resourceType,
            String resourceId,
            String pageId,
            String displayRef,
            Instant lastVisitedAt,
            Instant createdAt
    ) {
    }
}
