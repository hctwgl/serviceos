package com.serviceos.project.api;

/** 当前项目服务范围内可配置分工的标准行政区。 */
public record ProjectTeamRegionOption(
        String code,
        String name,
        String level,
        String parentCode
) {
}
