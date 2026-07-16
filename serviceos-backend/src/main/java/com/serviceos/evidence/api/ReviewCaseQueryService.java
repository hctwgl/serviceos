package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** 跨工单审核案例队列查询边界。 */
public interface ReviewCaseQueryService {
    ReviewCaseQueuePage list(
            CurrentPrincipal principal, String correlationId, ReviewCaseQueueQuery query);
}
