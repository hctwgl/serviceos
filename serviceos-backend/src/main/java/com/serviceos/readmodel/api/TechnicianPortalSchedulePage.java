package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TechnicianPortalSchedulePage(
        UUID networkId,
        List<TechnicianPortalScheduleItem> items,
        Instant asOf
) {
    public TechnicianPortalSchedulePage {
        items = List.copyOf(items);
    }
}
