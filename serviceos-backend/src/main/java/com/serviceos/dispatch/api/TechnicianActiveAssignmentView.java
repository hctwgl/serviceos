package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 师傅视角 ACTIVE/ENDED TECHNICIAN ServiceAssignment 摘要（跨模块可信只读）。
 * 调用方负责 Portal 上下文与 capability 鉴权。
 */
public record TechnicianActiveAssignmentView(
        UUID serviceAssignmentId,
        UUID workOrderId,
        UUID taskId,
        String businessType,
        String status,
        Instant effectiveFrom,
        Instant effectiveTo,
        String endReasonCode,
        String assigneeId
) {
}
