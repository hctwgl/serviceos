package com.serviceos.sla.api;

import java.util.Objects;
import java.util.UUID;

/** SLA 工作台查询必须显式限定 project，避免用 tenant 级列表替代 Project Scope。 */
public record SlaInstanceQuery(UUID projectId, String status, String cursor, int limit) {
    public SlaInstanceQuery {
        projectId = Objects.requireNonNull(projectId, "projectId must not be null");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
