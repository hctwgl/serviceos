package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

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
}
