package com.serviceos.project.api;

import java.util.Objects;

/** 行政区名称目录项。 */
public record RegionCatalogItem(
        String regionCode,
        String parentCode,
        String regionName,
        String regionLevel,
        int sortOrder,
        int childCount
) {
    public RegionCatalogItem {
        Objects.requireNonNull(regionCode, "regionCode");
        Objects.requireNonNull(regionName, "regionName");
        Objects.requireNonNull(regionLevel, "regionLevel");
        if (childCount < 0) {
            throw new IllegalArgumentException("childCount must be >= 0");
        }
    }
}
