package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ProjectClientDirectoryPage(List<ProjectClientDirectoryItem> items, Instant asOf) {
    public ProjectClientDirectoryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
