package com.serviceos.project.api;

import java.util.Objects;

/** 租户车企主数据目录项。 */
public record ProjectClientDirectoryItem(
        String clientCode,
        String displayName,
        String status
) {
    public ProjectClientDirectoryItem {
        Objects.requireNonNull(clientCode, "clientCode");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
    }
}
