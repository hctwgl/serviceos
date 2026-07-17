package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin 个人 SavedView 读模型。只保存受控筛选/排序/列偏好，不授予页面或数据能力。
 */
public record SavedView(
        UUID id,
        String portal,
        String pageId,
        String name,
        int schemaVersion,
        SavedViewFilterAst filter,
        SavedViewSortSpec sort,
        List<String> columns,
        boolean isDefault,
        long aggregateVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
