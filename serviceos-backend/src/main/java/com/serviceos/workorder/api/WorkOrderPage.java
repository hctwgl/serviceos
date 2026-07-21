package com.serviceos.workorder.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * receivedAt/id 倒序的授权工单页。
 *
 * <p>M434：可选 {@code slaRiskSummaries}；缺 PROJECT {@code sla.read} 时为 null（经 NON_NULL 省略）。
 * M436：{@code totalCount}/{@code totalCountTruncated} 随基座返回（上限 100，超出只声明 truncated）。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkOrderPage(
        List<WorkOrderView> items,
        String nextCursor,
        Instant asOf,
        List<WorkOrderDirectorySlaRiskSummary> slaRiskSummaries,
        int totalCount,
        boolean totalCountTruncated
) {
    public static final int TOTAL_COUNT_LIMIT = 100;

    public WorkOrderPage {
        items = List.copyOf(items);
        slaRiskSummaries = slaRiskSummaries == null ? null : List.copyOf(slaRiskSummaries);
        if (totalCount < 0 || totalCount > TOTAL_COUNT_LIMIT) {
            throw new IllegalArgumentException(
                    "totalCount must be between 0 and " + TOTAL_COUNT_LIMIT);
        }
        if (totalCountTruncated && totalCount != TOTAL_COUNT_LIMIT) {
            throw new IllegalArgumentException(
                    "truncatedated totalCount must equal " + TOTAL_COUNT_LIMIT);
        }
    }

    /** 无 SLA 旁载的目录页（兼容既有构造；total 按本页条数近似）。 */
    public WorkOrderPage(List<WorkOrderView> items, String nextCursor, Instant asOf) {
        this(items, nextCursor, asOf, null, items.size(), false);
    }
}
