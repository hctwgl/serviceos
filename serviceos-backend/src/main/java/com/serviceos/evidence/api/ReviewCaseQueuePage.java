package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;

/** createdAt/reviewCaseId 正序的稳定审核队列页。 */
public record ReviewCaseQueuePage(
        List<ReviewCaseQueueItem> items,
        String nextCursor,
        Instant asOf
) {
    public ReviewCaseQueuePage {
        items = List.copyOf(items);
    }
}
