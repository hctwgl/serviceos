package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Network Portal 通用列表包装。 */
public record NetworkPortalPage<T>(
        UUID networkId,
        List<T> items,
        Instant asOf
) {
    public NetworkPortalPage {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
