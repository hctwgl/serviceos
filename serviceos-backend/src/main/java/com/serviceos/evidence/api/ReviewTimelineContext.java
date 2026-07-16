package com.serviceos.evidence.api;

import java.util.UUID;

/**
 * 供跨模块时间线投影解析 ReviewCase 所属工单的最小非敏感上下文。
 *
 * <p>不暴露 Snapshot digest、策略版本、决定正文或外部批次引用；若 Case 无法关联到工单，
 * {@code workOrderId} 可为 null，由投影方记为已消费但不写入工单时间线。</p>
 */
public record ReviewTimelineContext(
        UUID reviewCaseId,
        UUID projectId,
        UUID workOrderId
) {
}
