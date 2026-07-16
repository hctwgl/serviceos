package com.serviceos.operations.api;

import java.util.UUID;

/**
 * 供跨模块时间线投影解析 OperationalException 所属工单的最小非敏感上下文。
 *
 * <p>不暴露错误码、备注、处理 Task 或审计字段；若异常无法关联到工单，{@code workOrderId}
 * 可为 null，由投影方记为已消费但不写入工单时间线。</p>
 */
public record ExceptionTimelineContext(
        UUID exceptionId,
        UUID projectId,
        UUID workOrderId
) {
}
