package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Task 当前已激活服务责任的安全只读摘要。
 *
 * <p>网点与师傅可能在不同时间激活，因此分别保留生效时间和稳定原因码，不合并为歧义时间。</p>
 */
public record ServiceAssignmentSummary(
        UUID taskId,
        String networkId,
        Instant networkEffectiveFrom,
        String networkReassignmentReasonCode,
        String technicianId,
        Instant technicianEffectiveFrom,
        String technicianReassignmentReasonCode
) {
}
