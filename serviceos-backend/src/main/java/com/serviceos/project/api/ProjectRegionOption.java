package com.serviceos.project.api;

import java.util.Objects;

/** 项目目录可用的区域选项（授权项目聚合 + 行政区名称目录）。 */
public record ProjectRegionOption(String regionCode, String regionName, int projectCount) {
    public ProjectRegionOption {
        Objects.requireNonNull(regionCode, "regionCode");
        if (regionCode.isBlank()) {
            throw new IllegalArgumentException("regionCode must not be blank");
        }
        regionName = regionName == null || regionName.isBlank() ? regionCode : regionName.trim();
        if (projectCount < 0) {
            throw new IllegalArgumentException("projectCount must not be negative");
        }
    }
}
