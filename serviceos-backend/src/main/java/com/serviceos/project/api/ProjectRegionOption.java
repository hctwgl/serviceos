package com.serviceos.project.api;

import java.util.Objects;

/** 项目目录可用的区域编码选项（来自已授权项目生效 REGION 关系）。 */
public record ProjectRegionOption(String regionCode, int projectCount) {
    public ProjectRegionOption {
        Objects.requireNonNull(regionCode, "regionCode");
        if (regionCode.isBlank()) {
            throw new IllegalArgumentException("regionCode must not be blank");
        }
        if (projectCount < 0) {
            throw new IllegalArgumentException("projectCount must not be negative");
        }
    }
}
