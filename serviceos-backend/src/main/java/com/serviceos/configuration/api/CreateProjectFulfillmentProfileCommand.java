package com.serviceos.configuration.api;

import java.util.Objects;
import java.util.UUID;

/** 创建项目工单类型履约配置。 */
public record CreateProjectFulfillmentProfileCommand(
        UUID projectId,
        String serviceProductCode,
        String profileName,
        String description,
        String templateCode,
        UUID copyFromProfileId,
        String profileCode,
        int matchPriority
) {
    public CreateProjectFulfillmentProfileCommand {
        projectId = Objects.requireNonNull(projectId, "projectId");
        serviceProductCode = text(serviceProductCode, "serviceProductCode", 96);
        profileName = text(profileName, "profileName", 200);
        description = description == null ? null : description.trim();
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("description exceeds 2000");
        }
        if (templateCode != null) {
            templateCode = text(templateCode, "templateCode", 96);
        }
        profileCode = text(profileCode, "profileCode", 96);
        if (matchPriority < -10_000 || matchPriority > 10_000) {
            throw new IllegalArgumentException("matchPriority must be between -10000 and 10000");
        }
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(name + " exceeds " + max);
        }
        return trimmed;
    }
}
