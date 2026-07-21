package com.serviceos.workorder.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * receivedAt/id 倒序的授权工单页。
 *
 * <p>M434：可选 {@code slaRiskSummaries}；缺 PROJECT {@code sla.read} 时为 null（经 NON_NULL 省略）。
 * M436/M444：{@code totalCount}/{@code totalCountTruncated} 随基座返回；
 * M444 起 {@code totalCount} 为当前筛选精确全量，{@code totalCountTruncated} 恒为 false。</p>
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
    public WorkOrderPage {
        items = List.copyOf(items);
        slaRiskSummaries = slaRiskSummaries == null ? null : List.copyOf(slaRiskSummaries);
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        // M444：目录路径不再产生 truncated；保留字段兼容，true 视为契约违例。
        if (totalCountTruncated) {
            throw new IllegalArgumentException("totalCountTruncated must be false");
        }
    }

    /** 无 SLA 旁载的目录页（兼容既有构造；total 按本页条数近似）。 */
    public WorkOrderPage(List<WorkOrderView> items, String nextCursor, Instant asOf) {
        this(items, nextCursor, asOf, null, items.size(), false);
    }
}
