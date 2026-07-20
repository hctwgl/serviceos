package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record RegionCatalogPage(List<RegionCatalogItem> items, Instant asOf) {
    public RegionCatalogPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
