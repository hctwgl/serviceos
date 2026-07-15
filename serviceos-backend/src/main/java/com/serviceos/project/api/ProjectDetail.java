package com.serviceos.project.api;

import java.time.Instant;

/** 项目当前权威事实；关系历史通过独立分页端点读取。 */
public record ProjectDetail(ProjectView project, Instant asOf) {
}
