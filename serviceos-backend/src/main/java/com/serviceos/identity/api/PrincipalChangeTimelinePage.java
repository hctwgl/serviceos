package com.serviceos.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PrincipalChangeTimelinePage(List<PrincipalChangeTimelineItem> items, Instant asOf) {
    public PrincipalChangeTimelinePage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
