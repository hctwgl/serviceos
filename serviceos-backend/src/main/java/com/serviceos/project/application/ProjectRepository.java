package com.serviceos.project.application;

import com.serviceos.project.domain.Project;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

/**
 * project 模块内部持久化端口，不允许暴露到 project.api。
 */
public interface ProjectRepository {
    void insert(Project project);

    void insertRegionBindings(Project project, String createdBy);

    void insertNetworkBindings(Project project, String createdBy);

    Optional<Project> findById(String tenantId, UUID projectId);

    Optional<Project> findByIdForUpdate(String tenantId, UUID projectId);

    boolean advanceVersion(String tenantId, UUID projectId, long expectedVersion);

    void reviseRegionBindings(
            String tenantId, UUID projectId, List<String> removed, List<String> added,
            String actorId, Instant revisedAt);

    void reviseNetworkBindings(
            String tenantId, UUID projectId, List<String> removed, List<String> added,
            String actorId, Instant revisedAt);

    void insertScopeRevision(ProjectScopeRevision revision);

    Optional<ProjectScopeRevision> findScopeRevision(String tenantId, UUID revisionId);

}
