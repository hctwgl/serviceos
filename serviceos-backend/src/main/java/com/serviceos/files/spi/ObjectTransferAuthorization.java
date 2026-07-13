package com.serviceos.files.spi;

import java.time.Instant;
import java.util.Map;

public record ObjectTransferAuthorization(
        String method,
        String url,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
    public ObjectTransferAuthorization {
        requiredHeaders = requiredHeaders == null ? Map.of() : Map.copyOf(requiredHeaders);
    }
}
