package com.serviceos.task.api;

import java.time.Instant;
import java.util.List;

public record TaskExecutionAttemptPage(
        long resourceVersion,
        List<TaskExecutionAttemptView> items,
        String nextCursor,
        Instant asOf
) {
    public TaskExecutionAttemptPage {
        items = List.copyOf(items);
    }
}
