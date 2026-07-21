package com.serviceos.evidence.api;

import com.serviceos.identity.api.CurrentPrincipal;

/** 跨工单审核案例队列查询边界。 */
public interface ReviewCaseQueryService {
    ReviewCaseQueuePage list(
            CurrentPrincipal principal, String correlationId, ReviewCaseQueueQuery query);

    /**
     * M452：与 {@link #list} 同授权与筛选口径的精确 COUNT（无 cursor）。
     * {@code query.limit}/{@code query.cursor} 忽略。
     */
    int count(CurrentPrincipal principal, String correlationId, ReviewCaseQueueQuery query);
}
