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
            String clientKind,
            String sinceCursor);

    TechnicianPortalSchedulePage schedule(
            CurrentPrincipal actor, String correlationId, String technicianContextHeader);

    TechnicianPortalSyncSummary syncSummary(
            CurrentPrincipal actor, String correlationId, String technicianContextHeader);

    /**
     * 返回当前师傅在指定网点上下文中的 ACTIVE 责任任务详情；非本人或已撤权任务按不存在处理。
     *
     * <p>M359：对已知 TECHNICIAN_WEB/IOS，若冻结 FORM/EVIDENCE 与当前客户端不兼容，
     * 抛出 {@code CLIENT_CAPABILITY_UNSUPPORTED}，禁止进入现场中途执行。</p>
     */
    TechnicianPortalTaskDetail taskDetail(
            CurrentPrincipal actor,
            String correlationId,
            String technicianContextHeader,
            String clientKind,
            UUID taskId);
}
