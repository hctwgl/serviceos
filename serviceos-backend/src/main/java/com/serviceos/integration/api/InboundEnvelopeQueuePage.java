package com.serviceos.integration.api;

import java.time.Instant;
import java.util.List;

public record InboundEnvelopeQueuePage(
        List<InboundEnvelopeQueueItem> items,
        String nextCursor,
        Instant asOf
) {
    public InboundEnvelopeQueuePage {
        items = List.copyOf(items);
    }
}
