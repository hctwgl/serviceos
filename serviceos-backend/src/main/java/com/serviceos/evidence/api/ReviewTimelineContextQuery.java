package com.serviceos.evidence.api;

import java.util.Optional;
import java.util.UUID;

/** 时间线投影只读查询：按租户与 ReviewCase ID 解析工单范围。 */
public interface ReviewTimelineContextQuery {
    Optional<ReviewTimelineContext> find(String tenantId, UUID reviewCaseId);
}
