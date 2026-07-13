package com.serviceos.files.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UploadSessionView(
        UUID uploadSessionId,
        UUID fileId,
        String status,
        String uploadMethod,
        String uploadUrl,
        Map<String, String> requiredHeaders,
        Instant uploadAuthorizationExpiresAt,
        Instant sessionExpiresAt
) {
}
