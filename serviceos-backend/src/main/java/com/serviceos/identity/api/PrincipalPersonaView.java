package com.serviceos.identity.api;

import java.time.Instant;
import java.util.UUID;

public record PrincipalPersonaView(
        UUID id,
        String personaType,
        String status,
        Instant validFrom,
        Instant validTo,
        long version
) {
}
