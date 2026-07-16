package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** 跨工单整改案例队列查询边界。 */
public interface CorrectionCaseQueryService {
    CorrectionCaseQueuePage list(
            CurrentPrincipal principal, String correlationId, CorrectionCaseQueueQuery query);
}
