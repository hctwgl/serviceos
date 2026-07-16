package com.serviceos.operations.api;

import java.util.Optional;
import java.util.UUID;

/** 时间线投影只读查询：按租户与异常 ID 解析工单范围。 */
public interface ExceptionTimelineContextQuery {
    Optional<ExceptionTimelineContext> find(String tenantId, UUID exceptionId);
}
