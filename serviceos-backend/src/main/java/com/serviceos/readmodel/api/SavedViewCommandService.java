package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.List;
import java.util.UUID;

/**
 * SavedView 写入。任意已认证主体可 CRUD 自己的视图；
 * 共享到 ROLE/TENANT 需要 preference.shareSavedView；owner 可始终收回 PRIVATE。
 * 跨主体更新/删除/分享按不存在处理，避免泄露他人偏好。
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

    /**
     * 设置可见性。visibility=PRIVATE 取消共享；ROLE/TENANT 需 HIGH capability。
     * 共享只改变查询定义可见性，不授予数据访问权。
     */
    SavedView share(
            CurrentPrincipal actor,
            String correlationId,
            UUID savedViewId,
            long expectedVersion,
            SavedViewVisibility visibility,
            String sharedScopeRef
    );
}
