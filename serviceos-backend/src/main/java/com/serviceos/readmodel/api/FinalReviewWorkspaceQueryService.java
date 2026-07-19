package com.serviceos.readmodel.api;

import com.serviceos.identity.api.CurrentPrincipal;

import java.util.UUID;

/** 工单平台终审工作区只读组合查询（M351）。 */
public interface FinalReviewWorkspaceQueryService {
    FinalReviewWorkspaceSectionResponse get(
            CurrentPrincipal principal,
            String correlationId,
            UUID workOrderId
    );
}
