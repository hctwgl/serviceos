package com.serviceos.network.api;

import java.time.Instant;
import java.util.UUID;

public record DeactivationImpactView(
        String subjectType,
        UUID subjectId,
        NetworkWorkImpact impact,
        Instant evaluatedAt
) {}
