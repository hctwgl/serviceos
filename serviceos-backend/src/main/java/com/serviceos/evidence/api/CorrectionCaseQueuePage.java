package com.serviceos.evidence.api;

import java.time.Instant;
import java.util.List;

/** createdAt/correctionCaseId 正序的稳定整改队列页。 */
public record CorrectionCaseQueuePage(
        List<CorrectionCaseQueueItem> items,
        String nextCursor,
        Instant asOf
) {
    public CorrectionCaseQueuePage {
        items = List.copyOf(items);
    }
}
