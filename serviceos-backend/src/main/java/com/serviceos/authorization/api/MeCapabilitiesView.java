package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;

public record MeCapabilitiesView(
        String contextId,
        String portal,
        List<String> capabilityCodes,
        String contextVersion,
        Instant asOf
) {
    public MeCapabilitiesView {
        capabilityCodes = capabilityCodes == null ? List.of() : List.copyOf(capabilityCodes);
    }
}
