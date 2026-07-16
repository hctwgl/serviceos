package com.serviceos.evidence.application;

import java.util.UUID;

/** ReviewCase 时间线身份：仅含解析工单所需的三列，不加载决定。 */
public record ReviewCaseTimelineIdentity(
        UUID reviewCaseId,
        UUID projectId,
        UUID taskId
) {
}
