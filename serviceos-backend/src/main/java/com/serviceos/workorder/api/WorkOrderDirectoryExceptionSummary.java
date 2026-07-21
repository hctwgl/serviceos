package com.serviceos.workorder.api;

import java.util.UUID;

/**
 * Admin 工单目录页级运营异常摘要（M450）。
 *
 * <p>仅在 PROJECT {@code operations.exception.read} soft-gate 通过后出现；
 * 仅包含 OPEN 异常 openCount&gt;0 的行。</p>
 */
public record WorkOrderDirectoryExceptionSummary(
        UUID workOrderId,
        int openCount
) {
    public WorkOrderDirectoryExceptionSummary {
        if (workOrderId == null) {
            throw new IllegalArgumentException("workOrderId must not be null");
        }
        if (openCount < 1) {
            throw new IllegalArgumentException("openCount must be positive");
        }
    }
}
