package com.serviceos.readmodel.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Network Portal 工作台计数/摘要。 */
public record NetworkPortalWorkbenchView(
        UUID networkId,
        int activeWorkOrderCount,
        int activeTaskCount,
        int activeTechnicianCount,
        List<NetworkPortalCapacityItem> capacity,
        Instant asOf
) {
    public NetworkPortalWorkbenchView {
        capacity = capacity == null ? List.of() : List.copyOf(capacity);
    }
}
