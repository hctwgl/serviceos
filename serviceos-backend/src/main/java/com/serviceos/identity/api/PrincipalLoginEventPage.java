package com.serviceos.identity.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record PrincipalLoginEventPage(List<PrincipalLoginEventView> items, Instant asOf) {
    public PrincipalLoginEventPage {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
