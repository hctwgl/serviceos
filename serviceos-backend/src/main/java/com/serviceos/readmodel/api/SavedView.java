package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin SavedView 读模型。可含本人 PRIVATE 与可见共享视图；只保存受控筛选/排序/列偏好，
 * 不授予页面或数据能力。
 */
public record SavedView(
        UUID id,
        String ownerPrincipalId,
        String portal,
        String pageId,
        String name,
        SavedViewVisibility visibility,
        String sharedScopeRef,
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
