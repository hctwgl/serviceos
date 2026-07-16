package com.serviceos.integration.api;

import java.time.Instant;
import java.util.List;

/** createdAt/deliveryId 正序的稳定外发交付队列页。 */
public record OutboundDeliveryQueuePage(
        List<OutboundDeliveryQueueItem> items,
        String nextCursor,
        Instant asOf
) {
    public OutboundDeliveryQueuePage {
        items = List.copyOf(items);
    }
}
