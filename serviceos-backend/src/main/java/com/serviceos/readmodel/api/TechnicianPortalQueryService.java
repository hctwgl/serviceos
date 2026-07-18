package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/**
 * Technician Portal Feed 只读编排（API-06 §11 子集）。
 */
public interface TechnicianPortalQueryService {
    TechnicianPortalFeedPage taskFeed(
            CurrentPrincipal actor,
            String correlationId,
            String technicianContextHeader,
            String sinceCursor);

    TechnicianPortalSchedulePage schedule(
            CurrentPrincipal actor, String correlationId, String technicianContextHeader);

    TechnicianPortalSyncSummary syncSummary(
            CurrentPrincipal actor, String correlationId, String technicianContextHeader);

    /**
     * 返回当前师傅在指定网点上下文中的 ACTIVE 责任任务详情；非本人或已撤权任务按不存在处理。
     */
    TechnicianPortalTaskDetail taskDetail(
            CurrentPrincipal actor,
            String correlationId,
            String technicianContextHeader,
            UUID taskId);
}
