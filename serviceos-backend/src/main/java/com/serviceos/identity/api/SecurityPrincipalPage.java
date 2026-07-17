package com.serviceos.identity.api;

import java.time.Instant;
import java.util.List;

public record SecurityPrincipalPage(
        List<SecurityPrincipalView> items,
        String nextCursor,
        Instant asOf
) {
    public SecurityPrincipalPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
