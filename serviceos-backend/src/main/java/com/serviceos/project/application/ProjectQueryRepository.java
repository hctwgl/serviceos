package com.serviceos.project.application;

import com.serviceos.project.domain.Project;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 项目查询专用持久化端口。目录查询必须在 SQL 中收敛实时授权范围，禁止先读取全租户数据再在内存过滤。
 */
public interface ProjectQueryRepository {
    List<Project> findPage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String clientId,
            String status,
            LocalDate activeOn,
            String cursorCode,
            UUID cursorId,
            int fetchSize
    );

    List<ProjectScopeRevision> findScopeRevisionPage(
            String tenantId, UUID projectId, Long cursorVersion, int fetchSize);
}
