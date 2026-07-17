package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;

public record MeNavigationView(
        String contextId,
        String portal,
        String contextVersion,
        String navigationCatalogVersion,
        List<MeNavigationItemView> items,
        Instant asOf
) {
    public MeNavigationView {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
