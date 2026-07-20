package com.serviceos.project.api;

import java.util.Objects;

/** 项目目录可用的车企选项（授权项目聚合 + 主数据目录显示名）。 */
public record ProjectClientOption(String clientId, String displayName, int projectCount) {
    public ProjectClientOption {
        Objects.requireNonNull(clientId, "clientId");
        if (clientId.isBlank()) {
            throw new IllegalArgumentException("clientId must not be blank");
        }
        displayName = displayName == null || displayName.isBlank() ? clientId : displayName.trim();
        if (projectCount < 0) {
            throw new IllegalArgumentException("projectCount must not be negative");
        }
    }
}
