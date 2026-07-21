package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 项目创建/范围编辑用的实体选择选项。
 *
 * <p>车企与区域来自已授权项目事实聚合；网点请使用独立 ServiceNetwork 目录 API。
 * 不含臆造的区域名称字典。</p>
 */
public record ProjectReferenceOptions(
        List<ProjectClientOption> clients,
        List<ProjectRegionOption> regions,
        Instant asOf
) {
    public ProjectReferenceOptions {
        clients = List.copyOf(Objects.requireNonNull(clients, "clients"));
        regions = List.copyOf(Objects.requireNonNull(regions, "regions"));
        Objects.requireNonNull(asOf, "asOf");
    }
}
