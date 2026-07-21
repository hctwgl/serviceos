package com.serviceos.workorder.api;

import java.util.UUID;

/**
 * Admin 工单目录页级 SLA 风险摘要（M434）。
 *
 * <p>仅在 PROJECT {@code sla.read} soft-gate 通过后出现；仅包含 openCount&gt;0 的行。</p>
 */
public record WorkOrderDirectorySlaRiskSummary(
        UUID workOrderId,
        int openCount,
        int breachedCount
) {
    public WorkOrderDirectorySlaRiskSummary {
        if (workOrderId == null) {
            throw new IllegalArgumentException("workOrderId must not be null");
        }
        if (openCount < 0 || breachedCount < 0 || breachedCount > openCount) {
            throw new IllegalArgumentException("SLA risk counts are invalid");
        }
    }
}
