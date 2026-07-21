package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/**
 * 项目履约方案聚合计数：供项目目录列表展示已发布/草稿方案数，避免前端 N+1 拉取。
 */
public record ProjectFulfillmentSchemeCount(
        UUID projectId,
        int publishedSchemeCount,
        int draftSchemeCount
) {
    public ProjectFulfillmentSchemeCount {
        Objects.requireNonNull(projectId, "projectId");
        if (publishedSchemeCount < 0 || draftSchemeCount < 0) {
            throw new IllegalArgumentException("scheme counts must not be negative");
        }
    }
}
