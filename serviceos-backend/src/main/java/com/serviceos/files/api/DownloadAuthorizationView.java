package com.serviceos.files.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DownloadAuthorizationView(
        UUID authorizationId,
        UUID fileId,
        String method,
        String downloadUrl,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}
