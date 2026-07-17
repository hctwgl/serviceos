package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TechnicianPortalFeedPage(
        UUID networkId,
        List<TechnicianPortalFeedItem> items,
        String nextCursor,
        Instant asOf
) {
    public TechnicianPortalFeedPage {
        items = List.copyOf(items);
    }
}
