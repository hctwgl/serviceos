package com.serviceos.authorization.api;

import com.serviceos.identity.api.PrincipalPersonaView;

import java.time.Instant;
import java.util.List;

public record MeProfileView(
        String principalId,
        String tenantId,
        String displayName,
        List<PrincipalPersonaView> personas,
        String contextVersion,
        Instant asOf
) {
    public MeProfileView {
        personas = personas == null ? List.of() : List.copyOf(personas);
    }
}
