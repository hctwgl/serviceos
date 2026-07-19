package com.serviceos.project.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 项目有效网点目录查询。
 *
 * <p>仅返回 {@code prj_project_network} 当前有效绑定的 network_id（varchar 业务标识）；
 * 不从 ServiceAssignment 或区域覆盖反推。</p>
 */
public interface ProjectNetworkDirectoryQuery {
    List<String> listActiveNetworkIds(String tenantId, UUID projectId, Instant asOf);
}
