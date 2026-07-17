package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record EligibilityView(
        UUID technicianProfileId,
        UUID serviceNetworkId,
        Instant evaluatedAt,
        boolean eligible,
        String reason
) {}
