package com.serviceos.configuration.application;

import com.serviceos.configuration.api.NotificationResolution;

import java.time.Instant;
import java.util.UUID;

/**
 * NOTIFICATION Intent/Delivery/Attempt 持久化端口。
 *
 * <p>与 Inbox 同事务写入；以 (tenant, sourceEventId, policyKey) 去重，
 * Delivery 以幂等键唯一，防止事件重放重复落库。</p>
 */
public interface NotificationDispatchStore {
    boolean intentExists(String tenantId, UUID sourceEventId, String policyKey);

    void saveResolution(
            String tenantId,
            UUID projectId,
            UUID sourceEventId,
            String sourceEventType,
            String sourceAggregateType,
            String sourceAggregateId,
            UUID workOrderId,
            UUID taskId,
            UUID bundleId,
            String bundleDigest,
            String correlationId,
            Instant now,
            NotificationResolution resolution
    );
}
