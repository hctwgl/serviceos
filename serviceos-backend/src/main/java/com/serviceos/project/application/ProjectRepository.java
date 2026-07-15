package com.serviceos.project.application;

import com.serviceos.project.domain.Project;

import java.util.Optional;
import java.util.UUID;

/**
 * project 模块内部持久化端口，不允许暴露到 project.api。
 */
public interface ProjectRepository {
    void insert(Project project);

    void insertRegionBindings(Project project, String createdBy);

    void insertNetworkBindings(Project project, String createdBy);

    Optional<Project> findById(String tenantId, UUID projectId);
}
