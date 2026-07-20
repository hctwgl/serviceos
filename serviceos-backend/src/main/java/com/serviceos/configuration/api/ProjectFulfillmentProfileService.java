package com.serviceos.configuration.api;

import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.CommandMetadata;

import java.util.List;
import java.util.UUID;

/**
 * 项目工单类型履约配置命令与查询端口。
 *
 * <p>编排既有 Bundle/资产，不实现第二套流程/表单/资料/SLA 引擎。</p>
 */
public interface ProjectFulfillmentProfileService {
    ProjectFulfillmentProfileDetail create(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateProjectFulfillmentProfileCommand command);

    List<ProjectFulfillmentProfileSummary> list(
            CurrentPrincipal principal, String correlationId, UUID projectId);

    ProjectFulfillmentProfileDetail get(
            CurrentPrincipal principal, String correlationId, UUID projectId, UUID profileId);

    ProjectFulfillmentDraftView getDraft(
            CurrentPrincipal principal, String correlationId, UUID projectId, UUID profileId);

    ProjectFulfillmentDraftView updateDraft(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UpdateProjectFulfillmentDraftCommand command);

    List<ProjectFulfillmentValidationIssue> validate(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId);

    ProjectFulfillmentManifestView compilePreview(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId);

    ProjectFulfillmentRevisionView publish(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID projectId,
            UUID profileId,
            long expectedVersion,
            java.time.Instant effectiveFrom,
            String publishNote);

    List<ProjectFulfillmentRevisionView> listRevisions(
            CurrentPrincipal principal, String correlationId, UUID projectId, UUID profileId);

    ProjectFulfillmentRevisionView getRevision(
            CurrentPrincipal principal,
            String correlationId,
            UUID projectId,
            UUID profileId,
            UUID revisionId);

    ProjectFulfillmentProfileDetail suspend(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId,
            long expectedVersion);

    ProjectFulfillmentProfileDetail resume(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId,
            long expectedVersion);
}
