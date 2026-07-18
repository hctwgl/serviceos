package com.serviceos.readmodel.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;

/**
 * M234：Network Portal 目录页薄 SLA 风险摘要（非 PII）。
 *
 * <p>工单目录按 {@code workOrderId} 聚合（{@code taskId} 为 null）；
 * 任务目录按 {@code taskId} 展开。计数语义同 M221/M224：
 * {@code openCount}=RUNNING∪BREACHED，{@code breachedCount}=BREACHED。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NetworkPortalDirectorySlaRiskSummary(
        UUID workOrderId,
        UUID taskId,
        int openCount,
        int breachedCount
) {
    public NetworkPortalDirectorySlaRiskSummary {
        if (workOrderId == null) {
            throw new IllegalArgumentException("workOrderId is required");
        }
        if (openCount < 0 || breachedCount < 0) {
            throw new IllegalArgumentException("SLA counts must be non-negative");
        }
    }
}
