package com.serviceos.authorization.api;

import java.time.Instant;
import java.util.List;

public record MeContextsView(
        List<MeContextView> contexts,
        String contextVersion,
        Instant asOf
) {
    public MeContextsView {
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
    }
}
