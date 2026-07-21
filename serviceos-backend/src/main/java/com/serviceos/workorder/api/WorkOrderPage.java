package com.serviceos.workorder.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * receivedAt/id 倒序的授权工单页。
 *
 * <p>M434：可选 {@code slaRiskSummaries}；缺 PROJECT {@code sla.read} 时为 null（经 NON_NULL 省略）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkOrderPage(
        List<WorkOrderView> items,
        String nextCursor,
        Instant asOf,
        List<WorkOrderDirectorySlaRiskSummary> slaRiskSummaries
) {
    public WorkOrderPage {
        items = List.copyOf(items);
        slaRiskSummaries = slaRiskSummaries == null ? null : List.copyOf(slaRiskSummaries);
    }

    /** 无 SLA 旁载的目录页（兼容既有构造）。 */
    public WorkOrderPage(List<WorkOrderView> items, String nextCursor, Instant asOf) {
        this(items, nextCursor, asOf, null);
    }
}
