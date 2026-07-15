package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;

public record TaskDirectoryPage(List<TaskDirectoryItem> items,String nextCursor,Instant asOf) {
    public TaskDirectoryPage { items=List.copyOf(items); }
}
