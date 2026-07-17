package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/**
 * 个人 SavedView 写入。任意已认证主体可 CRUD 自己的视图；
 * 跨主体更新/删除按不存在处理，避免泄露他人偏好。
 */
public interface SavedViewCommandService {
    SavedView create(
            CurrentPrincipal actor,
            String correlationId,
            String pageId,
            String name,
            int schemaVersion,
            SavedViewFilterAst filter,
            SavedViewSortSpec sort,
            List<String> columns,
            boolean isDefault
    );

    SavedView update(
            CurrentPrincipal actor,
            String correlationId,
            UUID savedViewId,
            long expectedVersion,
            String name,
            int schemaVersion,
            SavedViewFilterAst filter,
            SavedViewSortSpec sort,
            List<String> columns,
            boolean isDefault
    );

    void delete(CurrentPrincipal actor, String correlationId, UUID savedViewId);
}
