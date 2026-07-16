package com.serviceos.evidence.api;

import java.util.UUID;

/** 审核案例队列的受控筛选；status 为空时服务端默认 OPEN。 */
public record ReviewCaseQueueQuery(
        UUID projectId,
        String status,
        String origin,
        UUID taskId,
        String cursor,
        int limit
) {
}
