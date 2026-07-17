package com.serviceos.identity.api;

import java.time.Instant;
import java.util.List;

public record SecurityPrincipalDetail(
        SecurityPrincipalView principal,
        List<PrincipalPersonaView> personas,
        Instant asOf
) {
    public SecurityPrincipalDetail {
        personas = personas == null ? List.of() : List.copyOf(personas);
    }
}
