package com.serviceos.dispatch.api;

import java.time.Instant;
import java.util.UUID;

/**
 * 某网点当前 ACTIVE NETWORK 整单责任投影到 Task 的摘要（跨模块可信只读）。
 * {@code serviceAssignmentId} 仍指向整单当前责任的权威记录，不表示为每个 Task 重复占用容量。
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
