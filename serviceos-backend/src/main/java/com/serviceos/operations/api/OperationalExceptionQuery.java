package com.serviceos.operations.api;

import java.util.UUID;

/** 异常工作台筛选条件；cursor 是服务端签发的无状态稳定游标。 */
public record OperationalExceptionQuery(
        UUID projectId,
        String status,
        String category,
        String severity,
        UUID workOrderId,
        UUID taskId,
        String cursor,
        int limit
) {
    public OperationalExceptionQuery {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
