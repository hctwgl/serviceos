package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;

public record WorkOrderTaskPage(List<WorkOrderTaskSummary> items,String nextCursor,Instant asOf) {
    public WorkOrderTaskPage { items=List.copyOf(items); }
}
