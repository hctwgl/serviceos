package com.serviceos.configuration.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 建单时解析有效履约 Revision 的输入。 */
public record ProjectFulfillmentResolveQuery(
        String tenantId,
        UUID projectId,
        String serviceProductCode,
        Instant createdAt,
        String clientKind,
        String brandCode,
        String provinceCode
) {
    public ProjectFulfillmentResolveQuery {
        tenantId = require(tenantId, "tenantId", 64);
        projectId = Objects.requireNonNull(projectId, "projectId");
        serviceProductCode = require(serviceProductCode, "serviceProductCode", 96);
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (clientKind != null) {
            clientKind = require(clientKind, "clientKind", 64);
        }
        if (brandCode != null) {
            brandCode = require(brandCode, "brandCode", 64);
        }
        if (provinceCode != null) {
            provinceCode = require(provinceCode, "provinceCode", 16);
        }
    }

    private static String require(String value, String name, int max) {
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
