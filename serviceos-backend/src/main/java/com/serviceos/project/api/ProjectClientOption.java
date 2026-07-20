package com.serviceos.project.api;

import java.util.Objects;

/** 项目目录可用的车企选项（来自已授权项目中的 clientId 聚合）。 */
public record ProjectClientOption(String clientId, int projectCount) {
    public ProjectClientOption {
        Objects.requireNonNull(clientId, "clientId");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        if (projectCount < 0) {
            throw new IllegalArgumentException("projectCount must not be negative");
        }
    }
}
