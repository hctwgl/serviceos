package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AdminUserDirectoryPage(
        List<AdminUserDirectoryItem> items,
        String nextCursor,
        Instant asOf
) {
    public AdminUserDirectoryPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
