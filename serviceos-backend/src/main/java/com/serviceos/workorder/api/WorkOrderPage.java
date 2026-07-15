package com.serviceos.workorder.api;

import java.time.Instant;
import java.util.List;

/** receivedAt/id 倒序的授权工单页。 */
public record WorkOrderPage(List<WorkOrderView> items, String nextCursor, Instant asOf) {
    public WorkOrderPage { items = List.copyOf(items); }
}
