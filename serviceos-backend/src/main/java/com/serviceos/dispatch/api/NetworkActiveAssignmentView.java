package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 某网点当前 ACTIVE NETWORK 责任对应的工单/任务摘要（跨模块可信只读）。
 * 调用方负责 Portal 上下文与 capability 鉴权。
 */
public record NetworkActiveAssignmentView(
        UUID serviceAssignmentId,
        UUID workOrderId,
        UUID taskId,
        String businessType,
        Instant effectiveFrom,
        String technicianId
) {
}
