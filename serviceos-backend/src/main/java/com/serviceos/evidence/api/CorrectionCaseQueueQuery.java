package com.serviceos.evidence.api;

import java.util.UUID;

/** 整改案例队列的受控筛选；status 为空时服务端默认 OPEN。 */
public record CorrectionCaseQueueQuery(
        UUID projectId,
        String status,
        UUID taskId,
        UUID sourceReviewCaseId,
        String cursor,
        int limit
) {
}
