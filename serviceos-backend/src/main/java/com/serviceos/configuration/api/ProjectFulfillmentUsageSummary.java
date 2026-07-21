package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 项目履约配置中心「使用中工单」摘要。
 *
 * <p>硬门禁 {@code project.fulfillment.read}；{@code activeWorkOrderCount} 对
 * {@code workOrder.read} soft-gate：缺能力为 null（不得伪装为 0）。
 * 计数上限与关注项目角标一致（100），超出时 {@code activeWorkOrderCountTruncated=true}。</p>
 */
public record ProjectFulfillmentUsageSummary(
        UUID projectId,
        Integer activeWorkOrderCount,
        Boolean activeWorkOrderCountTruncated,
        Instant asOf
) {
    public ProjectFulfillmentUsageSummary {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(asOf, "asOf");
        if (activeWorkOrderCount == null) {
            if (activeWorkOrderCountTruncated != null) {
                throw new IllegalArgumentException(
                        "truncatedated must be null when activeWorkOrderCount is omitted");
            }
        } else {
            if (activeWorkOrderCount < 0) {
                throw new IllegalArgumentException("activeWorkOrderCount must not be negative");
            }
            Objects.requireNonNull(
                    activeWorkOrderCountTruncated, "activeWorkOrderCountTruncated");
        }
    }
}
