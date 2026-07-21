package com.serviceos.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PrincipalChangeTimelinePage(
        List<PrincipalChangeTimelineItem> items,
        List<String> omittedSources,
        Instant asOf
) {
    public PrincipalChangeTimelinePage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        omittedSources = List.copyOf(Objects.requireNonNull(omittedSources, "omittedSources"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
