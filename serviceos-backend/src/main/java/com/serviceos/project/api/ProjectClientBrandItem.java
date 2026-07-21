package com.serviceos.project.api;

import java.util.Objects;

/** 车企品牌目录项。 */
public record ProjectClientBrandItem(
        String clientCode,
        String brandCode,
        String displayName,
        String status,
        int sortOrder
) {
    public ProjectClientBrandItem {
        Objects.requireNonNull(clientCode, "clientCode");
        Objects.requireNonNull(brandCode, "brandCode");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
    }
}
