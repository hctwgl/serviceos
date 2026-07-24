package com.serviceos.configuration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.CreateProjectFulfillmentProfileCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleSuccessorCommand;
import com.serviceos.configuration.api.ProjectFulfillmentCompareImpact;
import com.serviceos.configuration.api.ProjectFulfillmentDraftView;
import com.serviceos.configuration.api.ProjectFulfillmentDocument;
import com.serviceos.configuration.api.ProjectFulfillmentManifestView;
import com.serviceos.configuration.api.ProjectFulfillmentProfileDetail;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileStatus;
import com.serviceos.configuration.api.ProjectFulfillmentProfileSummary;
import com.serviceos.configuration.api.ProjectFulfillmentResolveQuery;
import com.serviceos.configuration.api.ProjectFulfillmentResolveResult;
import com.serviceos.configuration.api.ProjectFulfillmentResolver;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionStatus;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionView;
import com.serviceos.configuration.api.ProjectFulfillmentSchemeCount;
import com.serviceos.configuration.api.ProjectFulfillmentUsageSummary;
import com.serviceos.configuration.api.ProjectFulfillmentValidationIssue;
import com.serviceos.configuration.api.UpdateProjectFulfillmentDraftCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.identity.api.PrincipalPersonaQuery;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.shared.infrastructure.PostgresJdbcParameters;
import com.serviceos.workorder.api.WorkOrderQuery;
import com.serviceos.workorder.api.WorkOrderQueryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 项目履约配置应用服务。
 *
 * <p>事务：Profile/草稿/发布同事务提交；已发布 Revision 由数据库触发器禁止修改。
 * 授权按 project scope + 细分 capability；页面 allowed-actions 由详情投影。</p>
 */
@Service
final class DefaultProjectFulfillmentProfileService implements ProjectFulfillmentProfileService {
    private static final String READ = "project.fulfillment.read";
    private static final String CREATE = "project.fulfillment.create";
    private static final String DRAFT_WRITE = "project.fulfillment.draft.write";
    private static final String VALIDATE = "project.fulfillment.validate";
    private static final String PUBLISH = "project.fulfillment.publish";
    private static final String SUSPEND = "project.fulfillment.suspend";
    private static final String RESUME = "project.fulfillment.resume";
    private static final String REVISION_READ = "project.fulfillment.revision.read";
    private static final String RESOURCE = "ProjectFulfillmentProfile";
    private static final String TEMPLATE_SURVEY_INSTALL = "HOME_CHARGING_SURVEY_INSTALL";
    /** 与关注项目角标（M409）一致：超过上限只声明 truncated，不返回精确 COUNT(*)。 */
    private static final int ACTIVE_WORK_ORDER_LIMIT = 100;

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final AuditAppender audit;
    private final ProjectFulfillmentDraftValidator validator;
    private final ProjectFulfillmentManifestCompiler compiler;
    private final ProjectFulfillmentWorkflowCompiler workflowCompiler;
    private final ProjectFulfillmentRunbookAssembler runbookAssembler;
    private final ProjectFulfillmentCompareAnalyzer compareAnalyzer;
    private final ProjectFulfillmentDocumentMapper documentMapper;
    private final ProjectFulfillmentResolver fulfillmentResolver;
    private final ConfigurationService configurations;
    private final WorkOrderQueryService workOrders;
    private final PrincipalPersonaQuery personas;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultProjectFulfillmentProfileService(
            JdbcClient jdbc,
            AuthorizationService authorization,
            AuditAppender audit,
            ProjectFulfillmentDraftValidator validator,
            ProjectFulfillmentManifestCompiler compiler,
            ProjectFulfillmentWorkflowCompiler workflowCompiler,
            ProjectFulfillmentRunbookAssembler runbookAssembler,
            ProjectFulfillmentCompareAnalyzer compareAnalyzer,
            ProjectFulfillmentDocumentMapper documentMapper,
            ProjectFulfillmentResolver fulfillmentResolver,
            ConfigurationService configurations,
            WorkOrderQueryService workOrders,
            PrincipalPersonaQuery personas,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.audit = audit;
        this.validator = validator;
        this.compiler = compiler;
        this.workflowCompiler = workflowCompiler;
        this.runbookAssembler = runbookAssembler;
        this.compareAnalyzer = compareAnalyzer;
        this.documentMapper = documentMapper;
        this.fulfillmentResolver = fulfillmentResolver;
        this.configurations = configurations;
        this.workOrders = workOrders;
        this.personas = personas;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ProjectFulfillmentProfileDetail create(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateProjectFulfillmentProfileCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(command, "command");
        requireProject(principal.tenantId(), command.projectId());
        authorization.require(principal, AuthorizationRequest.projectCapability(
                CREATE, principal.tenantId(), RESOURCE, command.projectId().toString(),
                command.projectId().toString()), metadata.correlationId());

        UUID profileId = UUID.randomUUID();
        UUID draftRevisionId = UUID.randomUUID();
        Instant now = clock.instant();
        InitialDraft initialDraft = initialDraft(principal.tenantId(), command);
        try {
            jdbc.sql("""
                    INSERT INTO cfg_project_fulfillment_profile (
                        profile_id, tenant_id, project_id, profile_code, service_product_code,
                        profile_name, description, match_priority, status,
                        active_revision_id, draft_revision_id,
                        aggregate_version, created_by, updated_by, created_at, updated_at
                    ) VALUES (
                        :profileId, :tenantId, :projectId, :profileCode, :product,
                        :name, :description, :matchPriority, 'DRAFT', NULL, NULL,
                        1, :actor, :actor, :now, :now
                    )
                    """)
                    .param("profileId", profileId)
                    .param("tenantId", principal.tenantId())
                    .param("projectId", command.projectId())
                    .param("profileCode", command.profileCode())
                    .param("product", command.serviceProductCode())
                    .param("name", command.profileName())
                    .param("description", command.description())
                    .param("matchPriority", command.matchPriority())
                    .param("actor", principal.principalId())
                    .param("now", PostgresJdbcParameters.timestamptz(now))
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "同一项目下履约方案编码已存在");
        }

        jdbc.sql("""
                INSERT INTO cfg_project_fulfillment_revision (
                    revision_id, tenant_id, profile_id, version_no, revision_status,
                    document_json, workflow_asset_version_id, source_bundle_id, created_at
                ) VALUES (
                    :revisionId, :tenantId, :profileId, 0, 'DRAFT',
                    CAST(:document AS jsonb), :workflowVersionId, :bundleId, :now
                )
                """)
                .param("revisionId", draftRevisionId)
                .param("tenantId", principal.tenantId())
                .param("profileId", profileId)
                .param("document", initialDraft.documentJson())
                .param("workflowVersionId", initialDraft.workflowAssetVersionId())
                .param("bundleId", initialDraft.sourceBundleId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .update();

        jdbc.sql("""
                UPDATE cfg_project_fulfillment_profile
                   SET draft_revision_id = :draftId
                 WHERE tenant_id = :tenantId AND profile_id = :profileId
                """)
                .param("draftId", draftRevisionId)
                .param("tenantId", principal.tenantId())
                .param("profileId", profileId)
                .update();

        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "PROJECT_FULFILLMENT_PROFILE_CREATED", CREATE,
                RESOURCE, profileId.toString(), "ALLOW", List.of(),
                "project-fulfillment-v1", "CREATED", null,
                profileId.toString(), metadata.correlationId(), now));

        return get(principal, metadata.correlationId(), command.projectId(), profileId);
    }

    @Override
    public ProjectFulfillmentUsageSummary usageSummary(
            CurrentPrincipal principal, String correlationId, UUID projectId
    ) {
        // 不包外层事务：workOrder 授权拒绝以 BusinessProblem 抛出，需可 catch soft-omit，
        // 避免嵌套只读事务被标记 rollback-only（同 M409 关注项目角标编排）。
        Objects.requireNonNull(projectId, "projectId");
        requireProject(principal.tenantId(), projectId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), RESOURCE, projectId.toString(), projectId.toString()),
                correlationId);
        Instant asOf = clock.instant();
        try {
            var page = workOrders.list(
                    principal,
                    correlationId,
                    new WorkOrderQuery(null, projectId, "ACTIVE", null, ACTIVE_WORK_ORDER_LIMIT));
            return new ProjectFulfillmentUsageSummary(
                    projectId,
                    page.items().size(),
                    page.nextCursor() != null,
                    asOf);
        } catch (BusinessProblem problem) {
            // soft-gate：缺 workOrder.read 时省略计数，页面显示「不可用」而非伪造 0
            if (problem.code() == ProblemCode.ACCESS_DENIED) {
                return new ProjectFulfillmentUsageSummary(projectId, null, null, asOf);
            }
            throw problem;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectFulfillmentProfileSummary> list(
            CurrentPrincipal principal, String correlationId, UUID projectId
    ) {
        requireProject(principal.tenantId(), projectId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), RESOURCE, projectId.toString(), projectId.toString()),
                correlationId);
        return jdbc.sql("""
                SELECT p.profile_id, p.project_id, p.profile_code, p.service_product_code,
                       p.profile_name, p.match_priority, p.status, p.aggregate_version, p.updated_at,
                       r.version_no AS active_version_no, r.effective_from,
                       COALESCE(r.document_json, d.document_json) AS document_json
                  FROM cfg_project_fulfillment_profile p
             LEFT JOIN cfg_project_fulfillment_revision r
                    ON r.revision_id = p.active_revision_id
             LEFT JOIN cfg_project_fulfillment_revision d
                    ON d.revision_id = p.draft_revision_id
                 WHERE p.tenant_id = :tenantId AND p.project_id = :projectId
                 ORDER BY p.service_product_code, p.match_priority DESC, p.profile_code
                """)
                .param("tenantId", principal.tenantId())
                .param("projectId", projectId)
                .query((rs, rowNum) -> {
                    String document = rs.getString("document_json");
                    Counts counts = countAssets(document);
                    Integer versionNo = (Integer) rs.getObject("active_version_no");
                    return new ProjectFulfillmentProfileSummary(
                            rs.getObject("profile_id", UUID.class),
                            rs.getObject("project_id", UUID.class),
                            rs.getString("profile_code"),
                            rs.getString("service_product_code"),
                            rs.getString("profile_name"),
                            rs.getInt("match_priority"),
                            rs.getString("status"),
                            counts.stages(),
                            counts.forms(),
                            counts.evidence(),
                            versionNo == null ? null : String.valueOf(versionNo),
                            toInstant(rs.getObject("effective_from", OffsetDateTime.class)),
                            counts.workflowSummary(),
                            counts.slaSummary(),
                            rs.getLong("aggregate_version"),
                            toInstant(rs.getObject("updated_at", OffsetDateTime.class)));
                })
                .list();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFulfillmentResolveResult simulateMatch(
            CurrentPrincipal principal,
            String correlationId,
            ProjectFulfillmentResolveQuery query
    ) {
        Objects.requireNonNull(query, "query");
        if (!Objects.equals(principal.tenantId(), query.tenantId())) {
            throw new BusinessProblem(ProblemCode.ACCESS_DENIED, "不得模拟其他租户的履约方案");
        }
        requireProject(principal.tenantId(), query.projectId());
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ,
                principal.tenantId(),
                RESOURCE,
                query.projectId().toString(),
                query.projectId().toString()),
                correlationId);
        return fulfillmentResolver.resolve(query);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectFulfillmentSchemeCount> summarizeSchemeCounts(
            CurrentPrincipal principal, String correlationId, Collection<UUID> projectIds
    ) {
        Objects.requireNonNull(principal, "principal");
        if (projectIds == null || projectIds.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = projectIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        // soft-gate：任选一个项目探测 fulfillment.read；缺能力返回空，目录列显示「—」。
        UUID probeProjectId = ids.getFirst();
        AuthorizationDecision decision = authorization.authorize(
                principal,
                AuthorizationRequest.projectCapability(
                        READ,
                        principal.tenantId(),
                        RESOURCE,
                        probeProjectId.toString(),
                        probeProjectId.toString()),
                correlationId);
        if (decision.effect() != AuthorizationDecision.Effect.ALLOW) {
            return List.of();
        }
        Map<UUID, ProjectFulfillmentSchemeCount> found = jdbc.sql("""
                SELECT project_id,
                       COUNT(*) FILTER (WHERE active_revision_id IS NOT NULL)::int AS published_count,
                       COUNT(*) FILTER (WHERE draft_revision_id IS NOT NULL)::int AS draft_count
                  FROM cfg_project_fulfillment_profile
                 WHERE tenant_id = :tenantId
                   AND project_id IN (:projectIds)
                 GROUP BY project_id
                """)
                .param("tenantId", principal.tenantId())
                .param("projectIds", ids)
                .query((rs, rowNum) -> new ProjectFulfillmentSchemeCount(
                        rs.getObject("project_id", UUID.class),
                        rs.getInt("published_count"),
                        rs.getInt("draft_count")))
                .list()
                .stream()
                .collect(Collectors.toMap(ProjectFulfillmentSchemeCount::projectId, row -> row, (a, b) -> a, LinkedHashMap::new));
        // ALLOW 时对请求中的每个项目补齐 0，便于目录与 DENY（空列表）区分。
        return ids.stream()
                .map(id -> found.getOrDefault(id, new ProjectFulfillmentSchemeCount(id, 0, 0)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFulfillmentProfileDetail get(
            CurrentPrincipal principal, String correlationId, UUID projectId, UUID profileId
    ) {
        ProfileRow row = loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), RESOURCE, profileId.toString(), projectId.toString()),
                correlationId);
        Instant effectiveFrom = null;
        String activeVersion = null;
        if (row.activeRevisionId() != null) {
            var active = jdbc.sql("""
                    SELECT version_no, effective_from
                      FROM cfg_project_fulfillment_revision
                     WHERE revision_id = :id AND tenant_id = :tenantId
                    """)
                    .param("id", row.activeRevisionId())
                    .param("tenantId", principal.tenantId())
                    .query((rs, n) -> new Object[]{
                            rs.getInt("version_no"),
                            toInstant(rs.getObject("effective_from", OffsetDateTime.class))
                    })
                    .optional()
                    .orElse(null);
            if (active != null) {
                activeVersion = String.valueOf(active[0]);
                effectiveFrom = (Instant) active[1];
            }
        }
        return new ProjectFulfillmentProfileDetail(
                row.profileId(), row.projectId(), row.profileCode(), row.serviceProductCode(),
                row.profileName(), row.description(), row.matchPriority(), row.status(),
                row.draftRevisionId(), row.activeRevisionId(),
                activeVersion, effectiveFrom, allowedActions(row.status()), row.aggregateVersion(),
                row.createdAt(), row.updatedAt(), clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFulfillmentDraftView getDraft(
            CurrentPrincipal principal, String correlationId, UUID projectId, UUID profileId
    ) {
        ProfileRow profile = loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), RESOURCE, profileId.toString(), projectId.toString()),
                correlationId);
        if (profile.draftRevisionId() == null) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置草稿不存在");
        }
        return jdbc.sql("""
                SELECT revision_id, document_json, workflow_asset_version_id, source_bundle_id,
                       validation_json, simulation_json::text AS simulation_json,
                       simulation_document_digest, simulated_at
                  FROM cfg_project_fulfillment_revision
                 WHERE tenant_id = :tenantId AND revision_id = :revisionId AND revision_status = 'DRAFT'
                """)
                .param("tenantId", principal.tenantId())
                .param("revisionId", profile.draftRevisionId())
                .query((rs, n) -> {
                    String documentJson = rs.getString("document_json");
                    return new ProjectFulfillmentDraftView(
                            profile.profileId(),
                            rs.getObject("revision_id", UUID.class),
                            profile.serviceProductCode(),
                            profile.profileName(),
                            profile.description(),
                            documentMapper.fromJson(documentJson),
                            documentJson,
                            rs.getObject("workflow_asset_version_id", UUID.class),
                            rs.getObject("source_bundle_id", UUID.class),
                            rs.getString("validation_json"),
                            rs.getString("simulation_json"),
                            rs.getString("simulation_document_digest"),
                            rs.getObject("simulated_at", OffsetDateTime.class) == null
                                    ? null
                                    : rs.getObject("simulated_at", OffsetDateTime.class).toInstant(),
                            profile.aggregateVersion(),
                            profile.updatedAt());
                })
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置草稿不存在"));
    }

    @Override
    @Transactional
    public ProjectFulfillmentDraftView updateDraft(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UpdateProjectFulfillmentDraftCommand command
    ) {
        ProfileRow profile = loadProfileById(principal.tenantId(), command.profileId());
        authorization.require(principal, AuthorizationRequest.projectCapability(
                DRAFT_WRITE, principal.tenantId(), RESOURCE, command.profileId().toString(),
                profile.projectId().toString()), metadata.correlationId());
        if (ProjectFulfillmentProfileStatus.RETIRED.name().equals(profile.status())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "已停用的履约配置不可编辑");
        }
        if (profile.aggregateVersion() != command.expectedVersion()) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "履约配置版本冲突，请重新加载后合并");
        }
        if (profile.draftRevisionId() == null) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置草稿不存在");
        }
        String documentJson = documentMapper.toJson(command.document());
        Instant now = clock.instant();
        String name = command.profileName() == null || command.profileName().isBlank()
                ? profile.profileName() : command.profileName().trim();
        String description = command.description() == null ? profile.description() : command.description().trim();
        int updated = jdbc.sql("""
                UPDATE cfg_project_fulfillment_revision
                   SET document_json = CAST(:document AS jsonb),
                       workflow_asset_version_id = COALESCE(
                           :workflowVersionId, workflow_asset_version_id),
                       source_bundle_id = COALESCE(:bundleId, source_bundle_id),
                       validation_json = NULL,
                       simulation_json = NULL,
                       simulation_document_digest = NULL,
                       simulated_at = NULL
                 WHERE tenant_id = :tenantId
                   AND revision_id = :revisionId
                   AND revision_status = 'DRAFT'
                """)
                .param("document", documentJson)
                .param("workflowVersionId", command.workflowAssetVersionId())
                .param("bundleId", command.sourceBundleId())
                .param("tenantId", principal.tenantId())
                .param("revisionId", profile.draftRevisionId())
                .update();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置草稿不存在");
        }
        int profileUpdated = jdbc.sql("""
                UPDATE cfg_project_fulfillment_profile
                   SET profile_name = :name,
                       description = :description,
                       aggregate_version = aggregate_version + 1,
                       updated_by = :actor,
                       updated_at = :now
                 WHERE tenant_id = :tenantId
                   AND profile_id = :profileId
                   AND aggregate_version = :expected
                """)
                .param("name", name)
                .param("description", description)
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", principal.tenantId())
                .param("profileId", command.profileId())
                .param("expected", command.expectedVersion())
                .update();
        if (profileUpdated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "履约配置版本冲突，请重新加载后合并");
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "PROJECT_FULFILLMENT_DRAFT_CHANGED", DRAFT_WRITE,
                RESOURCE, command.profileId().toString(), "ALLOW", List.of(),
                "project-fulfillment-v1", "UPDATED", null,
                command.profileId().toString(), metadata.correlationId(), now));
        return getDraft(principal, metadata.correlationId(), profile.projectId(), command.profileId());
    }

    @Override
    @Transactional
    public List<ProjectFulfillmentValidationIssue> validate(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId
    ) {
        ProfileRow profile = loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                VALIDATE, principal.tenantId(), RESOURCE, profileId.toString(), projectId.toString()),
                metadata.correlationId());
        DraftRow draft = loadDraft(principal.tenantId(), profile.draftRevisionId());
        Map<String, Object> document = parseDocument(draft.documentJson());
        ProjectFulfillmentDocument structuredDocument = documentMapper.fromJson(draft.documentJson());
        Map<String, Object> workflowDefinition = structuredDocument.nodes().isEmpty()
                ? loadPublishedWorkflowDefinition(principal.tenantId(), draft.workflowAssetVersionId())
                : Map.of("designerGraph", true);
        boolean bundleOk = !structuredDocument.nodes().isEmpty()
                || (draft.sourceBundleId() != null
                && bundleExists(principal.tenantId(), projectId, draft.sourceBundleId()));
        List<ProjectFulfillmentValidationIssue> issues = validator.validate(
                profileId, document, workflowDefinition, bundleOk);
        try {
            String validationJson = objectMapper.writeValueAsString(issues);
            jdbc.sql("""
                    UPDATE cfg_project_fulfillment_revision
                       SET validation_json = CAST(:validation AS jsonb)
                     WHERE revision_id = :revisionId AND tenant_id = :tenantId
                    """)
                    .param("validation", validationJson)
                    .param("revisionId", draft.revisionId())
                    .param("tenantId", principal.tenantId())
                    .update();
        } catch (Exception ex) {
            throw new IllegalStateException("validation persistence failed", ex);
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "PROJECT_FULFILLMENT_VALIDATION_COMPLETED", VALIDATE,
                RESOURCE, profileId.toString(), "ALLOW", List.of(),
                "project-fulfillment-v1",
                issues.stream().anyMatch(i -> "ERROR".equals(i.severity())) ? "BLOCKED" : "OK",
                null,
                profileId.toString(), metadata.correlationId(), clock.instant()));
        return issues;
    }

    @Override
    @Transactional
    public ProjectFulfillmentManifestView compilePreview(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId
    ) {
        ProfileRow profile = loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                VALIDATE, principal.tenantId(), RESOURCE, profileId.toString(), projectId.toString()),
                metadata.correlationId());
        DraftRow draft = loadDraft(principal.tenantId(), profile.draftRevisionId());
        Map<String, Object> document = parseDocument(draft.documentJson());
        BundleRef bundle = draft.sourceBundleId() == null
                ? new BundleRef(null, null)
                : loadBundle(principal.tenantId(), draft.sourceBundleId());
        var compiled = compiler.compile(
                profile.profileId(),
                draft.revisionId(),
                projectId,
                profile.serviceProductCode(),
                profile.profileName(),
                "draft-preview",
                bundle.bundleId(),
                bundle.bundleVersion(),
                draft.workflowAssetVersionId(),
                clock.instant(),
                document);
        Instant simulatedAt = clock.instant();
        jdbc.sql("""
                UPDATE cfg_project_fulfillment_revision
                   SET simulation_json = CAST(:simulation AS jsonb),
                       simulation_document_digest = :documentDigest,
                       simulated_at = :simulatedAt
                 WHERE tenant_id = :tenantId
                   AND revision_id = :revisionId
                   AND revision_status = 'DRAFT'
                """)
                .param("simulation", compiled.json())
                .param("documentDigest", Sha256.digest(draft.documentJson()))
                .param("simulatedAt", PostgresJdbcParameters.timestamptz(simulatedAt))
                .param("tenantId", principal.tenantId())
                .param("revisionId", draft.revisionId())
                .update();
        return new ProjectFulfillmentManifestView(
                compiled.json(),
                compiled.contentDigest(),
                runbookAssembler.fromManifestJson(compiled.json()));
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFulfillmentCompareImpact compareImpact(
            CurrentPrincipal principal, String correlationId, UUID projectId, UUID profileId
    ) {
        ProfileRow profile = loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), RESOURCE, profileId.toString(), projectId.toString()),
                correlationId);
        DraftRow draft = loadDraft(principal.tenantId(), profile.draftRevisionId());
        UUID baselineRevisionId = profile.activeRevisionId();
        String baselineVersionLabel = null;
        String baselineDocumentJson = null;
        if (baselineRevisionId != null) {
            var baseline = jdbc.sql("""
                    SELECT version_no, document_json::text AS document_json
                      FROM cfg_project_fulfillment_revision
                     WHERE tenant_id = :tenantId AND revision_id = :id
                       AND revision_status = 'PUBLISHED'
                    """)
                    .param("tenantId", principal.tenantId())
                    .param("id", baselineRevisionId)
                    .query((rs, n) -> new Object[] {
                            rs.getInt("version_no"),
                            rs.getString("document_json")
                    })
                    .optional()
                    .orElse(null);
            if (baseline != null) {
                baselineVersionLabel = "v" + baseline[0];
                baselineDocumentJson = (String) baseline[1];
            } else {
                baselineRevisionId = null;
            }
        }
        return compareAnalyzer.analyze(
                profile.profileId(),
                draft.revisionId(),
                draft.documentJson(),
                baselineRevisionId,
                baselineVersionLabel,
                baselineDocumentJson,
                clock.instant());
    }

    @Override
    @Transactional
    public ProjectFulfillmentRevisionView publish(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID projectId,
            UUID profileId,
            long expectedVersion,
            Instant effectiveFrom,
            String publishNote
    ) {
        ProfileRow profile = loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                PUBLISH, principal.tenantId(), RESOURCE, profileId.toString(), projectId.toString()),
                metadata.correlationId());
        if (ProjectFulfillmentProfileStatus.RETIRED.name().equals(profile.status())
                || ProjectFulfillmentProfileStatus.SUSPENDED.name().equals(profile.status())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "当前状态不允许发布新版本");
        }
        if (profile.aggregateVersion() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "履约配置版本冲突，请重新加载后发布");
        }
        DraftRow draft = loadDraft(principal.tenantId(), profile.draftRevisionId());
        Map<String, Object> document = parseDocument(draft.documentJson());
        ProjectFulfillmentDocument structuredDocument = documentMapper.fromJson(draft.documentJson());
        Map<String, Object> workflowDefinition = structuredDocument.nodes().isEmpty()
                ? loadPublishedWorkflowDefinition(principal.tenantId(), draft.workflowAssetVersionId())
                : Map.of("designerGraph", true);
        boolean bundleOk = !structuredDocument.nodes().isEmpty()
                || (draft.sourceBundleId() != null
                && bundleExists(principal.tenantId(), projectId, draft.sourceBundleId()));
        List<ProjectFulfillmentValidationIssue> issues = validator.validate(
                profileId, document, workflowDefinition, bundleOk);
        if (issues.stream().anyMatch(i -> "ERROR".equals(i.severity()))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "履约配置存在阻断错误，无法发布：" + issues.getFirst().userMessage());
        }
        if (!structuredDocument.nodes().isEmpty()
                && !Sha256.digest(draft.documentJson()).equals(draft.simulationDocumentDigest())) {
            throw new BusinessProblem(
                    ProblemCode.VALIDATION_FAILED,
                    "发布前必须对当前草稿完成一次成功模拟；草稿修改后需重新模拟");
        }
        if (structuredDocument.nodes().isEmpty()
                && (draft.sourceBundleId() == null || draft.workflowAssetVersionId() == null)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "发布前必须绑定 Workflow 与 Bundle");
        }
        Instant from = effectiveFrom == null ? clock.instant() : effectiveFrom;
        if (from.isBefore(clock.instant().minusSeconds(1))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "生效时间不能早于当前时间");
        }
        validateMatchRuleConflicts(
                principal.tenantId(),
                profile,
                documentMapper.fromJson(draft.documentJson()),
                from);
        int nextVersion = nextPublishedVersion(principal.tenantId(), profileId);
        UUID publishedRevisionId = UUID.randomUUID();
        RuntimeBinding runtimeBinding = structuredDocument.nodes().isEmpty()
                ? new RuntimeBinding(
                draft.workflowAssetVersionId(),
                draft.sourceBundleId(),
                loadBundle(principal.tenantId(), draft.sourceBundleId()).bundleVersion())
                : publishDesignerRuntime(
                principal.tenantId(), profile, structuredDocument, nextVersion, from, draft.sourceBundleId());
        BundleRef bundle = loadBundle(principal.tenantId(), runtimeBinding.bundleId());
        var compiled = compiler.compile(
                profileId, publishedRevisionId, projectId, profile.serviceProductCode(),
                profile.profileName(), String.valueOf(nextVersion), bundle.bundleId(),
                bundle.bundleVersion(), runtimeBinding.workflowAssetVersionId(), from, document);
        Instant now = clock.instant();
        UUID supersedes = profile.activeRevisionId();
        if (supersedes != null) {
            jdbc.sql("""
                    UPDATE cfg_project_fulfillment_revision
                       SET effective_to = :to
                     WHERE revision_id = :id
                       AND tenant_id = :tenantId
                       AND revision_status = 'PUBLISHED'
                       AND effective_to IS NULL
                    """)
                    .param("to", PostgresJdbcParameters.timestamptz(from))
                    .param("id", supersedes)
                    .param("tenantId", principal.tenantId())
                    .update();
        }
        try {
            jdbc.sql("""
                    INSERT INTO cfg_project_fulfillment_revision (
                        revision_id, tenant_id, profile_id, version_no, revision_status,
                        document_json, source_bundle_id, workflow_asset_version_id,
                        manifest_json, validation_json, content_digest,
                        effective_from, effective_to, supersedes_revision_id,
                        published_by, published_at, created_at
                    ) VALUES (
                        :revisionId, :tenantId, :profileId, :versionNo, 'PUBLISHED',
                        CAST(:document AS jsonb), :bundleId, :workflowVersionId,
                        CAST(:manifest AS jsonb), CAST(:validation AS jsonb), :digest,
                        :effectiveFrom, NULL, :supersedes,
                        :publisher, :publishedAt, :createdAt
                    )
                    """)
                    .param("revisionId", publishedRevisionId)
                    .param("tenantId", principal.tenantId())
                    .param("profileId", profileId)
                    .param("versionNo", nextVersion)
                    .param("document", draft.documentJson())
                    .param("bundleId", runtimeBinding.bundleId())
                    .param("workflowVersionId", runtimeBinding.workflowAssetVersionId())
                    .param("manifest", compiled.json())
                    .param("validation", objectMapper.writeValueAsString(issues))
                    .param("digest", compiled.contentDigest())
                    .param("effectiveFrom", PostgresJdbcParameters.timestamptz(from))
                    .param("supersedes", supersedes)
                    .param("publisher", principal.principalId())
                    .param("publishedAt", PostgresJdbcParameters.timestamptz(now))
                    .param("createdAt", PostgresJdbcParameters.timestamptz(now))
                    .update();
        } catch (Exception ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("ex_cfg_pfr_effective_window")) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "生效时间与已有发布版本冲突");
            }
            throw new IllegalStateException("publish revision failed", ex);
        }
        // 重置草稿内容为刚发布文档，保持可继续编辑。
        jdbc.sql("""
                UPDATE cfg_project_fulfillment_revision
                   SET document_json = CAST(:document AS jsonb),
                       workflow_asset_version_id = :workflowVersionId,
                       source_bundle_id = :bundleId,
                       validation_json = NULL,
                       simulation_json = NULL,
                       simulation_document_digest = NULL,
                       simulated_at = NULL
                 WHERE revision_id = :draftId AND tenant_id = :tenantId AND revision_status = 'DRAFT'
                """)
                .param("document", draft.documentJson())
                .param("workflowVersionId", runtimeBinding.workflowAssetVersionId())
                .param("bundleId", runtimeBinding.bundleId())
                .param("draftId", profile.draftRevisionId())
                .param("tenantId", principal.tenantId())
                .update();
        int profileUpdated = jdbc.sql("""
                UPDATE cfg_project_fulfillment_profile
                   SET status = 'ACTIVE',
                       active_revision_id = :activeId,
                       aggregate_version = aggregate_version + 1,
                       updated_by = :actor,
                       updated_at = :now
                 WHERE tenant_id = :tenantId
                   AND profile_id = :profileId
                   AND aggregate_version = :expected
                """)
                .param("activeId", publishedRevisionId)
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", principal.tenantId())
                .param("profileId", profileId)
                .param("expected", expectedVersion)
                .update();
        if (profileUpdated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "履约配置版本冲突，请重新加载后发布");
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                "PROJECT_FULFILLMENT_REVISION_PUBLISHED", PUBLISH,
                RESOURCE, profileId.toString(), "ALLOW", List.of(),
                "project-fulfillment-v1",
                publishNote == null ? "PUBLISHED" : publishNote,
                null, publishedRevisionId.toString(), metadata.correlationId(), now));
        return getRevision(principal, metadata.correlationId(), projectId, profileId, publishedRevisionId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectFulfillmentRevisionView> listRevisions(
            CurrentPrincipal principal, String correlationId, UUID projectId, UUID profileId
    ) {
        loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                REVISION_READ, principal.tenantId(), RESOURCE, profileId.toString(),
                projectId.toString()), correlationId);
        List<ProjectFulfillmentRevisionView> revisions = jdbc.sql("""
                SELECT *
                  FROM cfg_project_fulfillment_revision
                 WHERE tenant_id = :tenantId AND profile_id = :profileId
                   AND revision_status = 'PUBLISHED'
                 ORDER BY version_no DESC
                """)
                .param("tenantId", principal.tenantId())
                .param("profileId", profileId)
                .query((rs, n) -> mapRevision(rs))
                .list();
        return revisions.stream()
                .map(revision -> enrichPublisherDisplayName(principal.tenantId(), revision))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFulfillmentRevisionView getRevision(
            CurrentPrincipal principal,
            String correlationId,
            UUID projectId,
            UUID profileId,
            UUID revisionId
    ) {
        loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                REVISION_READ, principal.tenantId(), RESOURCE, profileId.toString(),
                projectId.toString()), correlationId);
        ProjectFulfillmentRevisionView revision = jdbc.sql("""
                SELECT *
                  FROM cfg_project_fulfillment_revision
                 WHERE tenant_id = :tenantId AND profile_id = :profileId AND revision_id = :revisionId
                """)
                .param("tenantId", principal.tenantId())
                .param("profileId", profileId)
                .param("revisionId", revisionId)
                .query((rs, n) -> mapRevision(rs))
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置版本不存在"));
        return enrichPublisherDisplayName(principal.tenantId(), revision);
    }

    @Override
    @Transactional
    public ProjectFulfillmentProfileDetail suspend(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId,
            long expectedVersion
    ) {
        return transitionStatus(principal, metadata, projectId, profileId, expectedVersion,
                SUSPEND, ProjectFulfillmentProfileStatus.ACTIVE,
                ProjectFulfillmentProfileStatus.SUSPENDED, "PROJECT_FULFILLMENT_PROFILE_SUSPENDED");
    }

    @Override
    @Transactional
    public ProjectFulfillmentProfileDetail resume(
            CurrentPrincipal principal, CommandMetadata metadata, UUID projectId, UUID profileId,
            long expectedVersion
    ) {
        return transitionStatus(principal, metadata, projectId, profileId, expectedVersion,
                RESUME, ProjectFulfillmentProfileStatus.SUSPENDED,
                ProjectFulfillmentProfileStatus.ACTIVE, "PROJECT_FULFILLMENT_PROFILE_RESUMED");
    }

    private ProjectFulfillmentProfileDetail transitionStatus(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID projectId,
            UUID profileId,
            long expectedVersion,
            String capability,
            ProjectFulfillmentProfileStatus from,
            ProjectFulfillmentProfileStatus to,
            String auditAction
    ) {
        ProfileRow profile = loadProfile(principal.tenantId(), projectId, profileId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                capability, principal.tenantId(), RESOURCE, profileId.toString(),
                projectId.toString()), metadata.correlationId());
        if (!from.name().equals(profile.status())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "当前状态不允许该操作：" + profile.status());
        }
        if (profile.aggregateVersion() != expectedVersion) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "履约配置版本冲突");
        }
        Instant now = clock.instant();
        int updated = jdbc.sql("""
                UPDATE cfg_project_fulfillment_profile
                   SET status = :status,
                       aggregate_version = aggregate_version + 1,
                       updated_by = :actor,
                       updated_at = :now
                 WHERE tenant_id = :tenantId AND profile_id = :profileId
                   AND aggregate_version = :expected AND status = :fromStatus
                """)
                .param("status", to.name())
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", principal.tenantId())
                .param("profileId", profileId)
                .param("expected", expectedVersion)
                .param("fromStatus", from.name())
                .update();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "履约配置版本冲突");
        }
        audit.append(new AuditEntry(
                UUID.randomUUID(), principal.tenantId(), principal.principalId(),
                auditAction, capability, RESOURCE, profileId.toString(), "ALLOW", List.of(),
                "project-fulfillment-v1", to.name(), null,
                profileId.toString(), metadata.correlationId(), now));
        return get(principal, metadata.correlationId(), projectId, profileId);
    }

    private InitialDraft initialDraft(
            String tenantId,
            CreateProjectFulfillmentProfileCommand command
    ) {
        if (command.copyFromProfileId() != null) {
            // 复制只允许发生在当前 tenant + project 范围内。运行时绑定与业务文档必须
            // 原子复制，否则新草稿看似完整却无法发布，且不能借 UUID 探测其他项目配置。
            return jdbc.sql("""
                    SELECT r.document_json::text,
                           r.workflow_asset_version_id,
                           r.source_bundle_id
                      FROM cfg_project_fulfillment_revision r
                      JOIN cfg_project_fulfillment_profile p ON p.draft_revision_id = r.revision_id
                     WHERE p.tenant_id = :tenantId
                       AND p.project_id = :projectId
                       AND p.profile_id = :profileId
                       AND r.tenant_id = p.tenant_id
                       AND r.revision_status = 'DRAFT'
                    """)
                    .param("tenantId", tenantId)
                    .param("projectId", command.projectId())
                    .param("profileId", command.copyFromProfileId())
                    .query((rs, rowNum) -> new InitialDraft(
                            rs.getString("document_json"),
                            rs.getObject("workflow_asset_version_id", UUID.class),
                            rs.getObject("source_bundle_id", UUID.class)))
                    .optional()
                    .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                            "复制来源履约配置不存在"));
        }
        String template = command.templateCode() == null
                ? TEMPLATE_SURVEY_INSTALL
                : command.templateCode();
        if (!TEMPLATE_SURVEY_INSTALL.equals(template)
                && !"BLANK".equals(template)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "不支持的起始模板：" + template);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", "2.0.0");
        doc.put("orderTypeName", "勘测安装");
        doc.put("supportedClientKinds", List.of("ADMIN_WEB", "NETWORK_WEB", "TECHNICIAN_WEB"));
        doc.put("stages", List.of());
        if ("BLANK".equals(template)) {
            doc.put("phases", List.of());
            doc.put("nodes", List.of());
            doc.put("transitions", List.of());
        } else {
            doc.put("phases", List.of(
                    phase("CUSTOMER_CONFIRM", "客户确认", 1, "#16a34a"),
                    phase("SITE_SURVEY", "现场勘测", 2, "#2563eb"),
                    phase("INSTALLATION", "施工安装", 3, "#f59e0b"),
                    phase("ACCEPTANCE", "验收交付", 4, "#7c3aed")));
            doc.put("nodes", sampleDesignerNodes());
            doc.put("transitions", sampleDesignerTransitions());
        }
        try {
            return new InitialDraft(objectMapper.writeValueAsString(doc), null, null);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Map<String, Object> phase(
            String id,
            String name,
            int sequence,
            String color
    ) {
        return Map.of(
                "phaseId", id,
                "phaseName", name,
                "sequence", sequence,
                "description", name + "阶段",
                "displayColor", color);
    }

    private static List<Map<String, Object>> sampleDesignerNodes() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(designerNode("START", "START", "开始", null, 420, 40, null));
        nodes.add(designerNode("CONTACT_CUSTOMER", "HUMAN_TASK", "联系客户",
                "CUSTOMER_CONFIRM", 420, 160, "项目客服"));
        nodes.add(designerNode("SCHEDULE_SURVEY", "HUMAN_TASK", "预约勘测",
                "CUSTOMER_CONFIRM", 420, 280, "项目客服"));
        Map<String, Object> survey = designerNode("SITE_SURVEY", "HUMAN_TASK", "现场勘测",
                "SITE_SURVEY", 420, 420, "现场工程师");
        survey.put("form", Map.of(
                "formKey", "site-survey-form",
                "formName", "现场勘测表",
                "fields", List.of(Map.of(
                        "fieldKey", "installable",
                        "label", "是否具备安装条件",
                        "type", "SELECT",
                        "required", true))));
        survey.put("evidence", List.of(Map.of(
                "evidenceKey", "survey-photos",
                "name", "勘测现场照片",
                "type", "PHOTO",
                "required", true,
                "minCount", 3)));
        survey.put("sla", Map.of(
                "name", "现场勘测 24 小时",
                "targetMinutes", 1440,
                "warningMinutes", 1200,
                "timeoutMinutes", 1440));
        nodes.add(survey);
        Map<String, Object> surveyReview = designerNode("SURVEY_REVIEW", "REVIEW", "勘测审核",
                "SITE_SURVEY", 420, 560, "项目运营");
        surveyReview.put("completionResults", List.of("PASS", "REJECT"));
        nodes.add(surveyReview);
        nodes.add(designerNode("SURVEY_SUPPLEMENT", "HUMAN_TASK", "补充勘测资料",
                "SITE_SURVEY", 700, 680, "现场工程师"));
        nodes.add(designerNode("INSTALL", "HUMAN_TASK", "安装施工",
                "INSTALLATION", 420, 720, "安装工程师"));
        Map<String, Object> installReview = designerNode(
                "INSTALL_REVIEW", "REVIEW", "安装资料审核",
                "INSTALLATION", 420, 860, "项目运营");
        installReview.put("completionResults", List.of("PASS", "REJECT"));
        nodes.add(installReview);
        Map<String, Object> submit = designerNode(
                "SUBMIT_OEM", "SYSTEM_ACTION", "提交车企",
                "ACCEPTANCE", 420, 1000, null);
        submit.put("systemAction", Map.of(
                "actionType", "BYD_REVIEW_SUBMIT",
                "target", "比亚迪 CPIM",
                "inputMapping", Map.of("workOrderId", "{workOrderId}"),
                "outputMapping", Map.of("deliveryId", "deliveryId"),
                "idempotencyStrategy", "WORK_ORDER_AND_NODE_EXECUTION",
                "retryPolicy", Map.of(
                        "maxAttempts", 3,
                        "initialDelaySeconds", 30,
                        "multiplier", 2,
                        "maxDelaySeconds", 300),
                "successResult", "SUCCESS",
                "failureResult", "FAILED",
                "failurePolicy", "RETRY_THEN_MANUAL",
                "manualRecoveryBoundary", "项目运营确认外部状态后重试或终止"));
        submit.put("completionResults", List.of("SUCCESS", "FAILED"));
        nodes.add(submit);
        Map<String, Object> wait = designerNode(
                "WAIT_OEM_RECEIPT", "EVENT_WAIT", "等待车企审核回执",
                "ACCEPTANCE", 420, 1140, null);
        wait.put("eventWait", Map.of(
                "eventType", "byd.review.callback",
                "correlationKeyTemplate", "work-order:{workOrderId}",
                "maxWaitSeconds", 259200,
                "timeoutStrategy", "ROUTE_TIMEOUT",
                "reminderTask", false,
                "outputMapping", Map.of("reviewResult", "result")));
        wait.put("completionResults", List.of("SUCCESS", "TIMEOUT"));
        nodes.add(wait);
        nodes.add(designerNode("END_SUCCESS", "END", "结束", null, 420, 1280, null));
        nodes.add(designerNode("END_EXCEPTION", "END", "异常结束", null, 700, 1140, null));
        return List.copyOf(nodes);
    }

    private static Map<String, Object> designerNode(
            String id,
            String type,
            String name,
            String phaseId,
            double x,
            double y,
            String role
    ) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeId", id);
        node.put("nodeType", type);
        node.put("nodeName", name);
        node.put("phaseId", phaseId);
        node.put("description", name + "业务节点");
        node.put("positionX", x);
        node.put("positionY", y);
        node.put("responsibilityRole", role);
        node.put("executionSubjectRule", role == null ? null : "按项目责任范围匹配");
        node.put("reassignable", "HUMAN_TASK".equals(type));
        node.put("task", Set.of("HUMAN_TASK", "REVIEW").contains(type)
                ? Map.of("taskType", id, "taskName", name + "任务")
                : Map.of());
        node.put("form", Map.of());
        node.put("evidence", List.of());
        node.put("sla", Map.of());
        node.put("completionResults", Set.of("HUMAN_TASK", "REVIEW").contains(type)
                ? List.of("COMPLETED") : List.of());
        node.put("systemAction", Map.of());
        node.put("eventWait", Map.of());
        node.put("condition", Map.of());
        node.put("exceptionStrategy", null);
        node.put("notificationRules", List.of());
        return node;
    }

    private static List<Map<String, Object>> sampleDesignerTransitions() {
        return List.of(
                transition("T01", "START", "CONTACT_CUSTOMER", null, false),
                transition("T02", "CONTACT_CUSTOMER", "SCHEDULE_SURVEY", null, false),
                transition("T03", "SCHEDULE_SURVEY", "SITE_SURVEY", null, false),
                transition("T04", "SITE_SURVEY", "SURVEY_REVIEW", null, false),
                transition("T05", "SURVEY_REVIEW", "INSTALL", "PASS", false),
                transition("T06", "SURVEY_REVIEW", "SURVEY_SUPPLEMENT", "REJECT", false),
                transition("T07", "SURVEY_SUPPLEMENT", "SURVEY_REVIEW", null, false),
                transition("T08", "INSTALL", "INSTALL_REVIEW", null, false),
                transition("T09", "INSTALL_REVIEW", "SUBMIT_OEM", null, false),
                transition("T10", "SUBMIT_OEM", "WAIT_OEM_RECEIPT", "SUCCESS", false),
                transition("T11", "SUBMIT_OEM", "END_EXCEPTION", "FAILED", false),
                transition("T12", "WAIT_OEM_RECEIPT", "END_SUCCESS", "SUCCESS", false),
                transition("T13", "WAIT_OEM_RECEIPT", "END_EXCEPTION", "TIMEOUT", false));
    }

    private static Map<String, Object> transition(
            String id,
            String from,
            String to,
            String resultCode,
            boolean defaultBranch
    ) {
        Map<String, Object> transition = new LinkedHashMap<>();
        transition.put("transitionId", id);
        transition.put("fromNodeId", from);
        transition.put("toNodeId", to);
        transition.put("resultCode", resultCode);
        transition.put("branchName", resultCode);
        transition.put("defaultBranch", defaultBranch);
        transition.put("condition", Map.of());
        return transition;
    }

    /**
     * 将当前产品草稿编译并发布到既有不可变配置中心。
     *
     * <p>事务边界：Workflow 资产、Bundle、Revision 和 Profile 指针加入同一数据库事务。
     * 首次发布创建 Bundle；后续版本使用 expectedCurrentBundleId 原子关闭旧开放区间，
     * 因而历史工单仍按旧 bundleId + digest 执行。</p>
     */
    private RuntimeBinding publishDesignerRuntime(
            String tenantId,
            ProfileRow profile,
            ProjectFulfillmentDocument document,
            int versionNo,
            Instant effectiveFrom,
            UUID currentBundleId
    ) {
        String semanticVersion = versionNo + ".0.0";
        ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings baseBindings =
                loadDesignerRuntimeBindings(tenantId, currentBundleId);
        MaterializedDesignerAssets designerAssets = materializeDesignerAssets(
                tenantId, profile, document, semanticVersion, baseBindings);
        var workflow = workflowCompiler.compile(
                profile.profileCode(), profile.profileName(), semanticVersion, document,
                designerAssets.bindings());
        String assetKey = "fulfillment-" + profile.profileId() + "-workflow";
        var asset = configurations.publishAsset(new PublishConfigurationAssetCommand(
                tenantId,
                ConfigurationAssetType.WORKFLOW,
                assetKey,
                semanticVersion,
                "1.0.0",
                workflow.definitionJson(),
                workflow.contentDigest()));

        String brandCode = document.matchRule().brandCodes().isEmpty()
                ? "ALL"
                : document.matchRule().brandCodes().getFirst();
        String provinceCode = document.matchRule().provinceCodes().isEmpty()
                ? null
                : document.matchRule().provinceCodes().getFirst();
        List<UUID> bundleAssetVersionIds = new ArrayList<>();
        if (currentBundleId != null) {
            bundleAssetVersionIds.addAll(jdbc.sql("""
                            SELECT i.asset_version_id
                              FROM cfg_configuration_bundle_item i
                              JOIN cfg_configuration_asset_version v
                                ON v.tenant_id = i.tenant_id
                               AND v.version_id = i.asset_version_id
                             WHERE i.tenant_id = :tenantId
                               AND i.bundle_id = :bundleId
                               AND i.asset_type <> 'WORKFLOW'
                               AND NOT (v.asset_key = ANY(CAST(:replacedKeys AS text[])))
                             ORDER BY i.asset_type, i.asset_version_id
                            """)
                    .param("tenantId", tenantId)
                    .param("bundleId", currentBundleId)
                    .param("replacedKeys", designerAssets.replacedAssetKeys().toArray(String[]::new))
                    .query(UUID.class)
                    .list());
        }
        bundleAssetVersionIds.addAll(designerAssets.assetVersionIds());
        bundleAssetVersionIds.add(asset.versionId());
        var command = new PublishConfigurationBundleCommand(
                tenantId,
                profile.projectId(),
                "fulfillment-" + profile.profileId(),
                semanticVersion,
                brandCode,
                profile.serviceProductCode(),
                provinceCode,
                effectiveFrom,
                null,
                List.copyOf(bundleAssetVersionIds));
        var bundle = currentBundleId == null
                ? configurations.publishBundle(command)
                : configurations.publishBundleSuccessor(
                new PublishConfigurationBundleSuccessorCommand(currentBundleId, command));
        return new RuntimeBinding(asset.versionId(), bundle.bundleId(), bundle.bundleVersion());
    }

    /**
     * 节点草稿只保存业务配置快照；发布时把它绑定到当前 Bundle 内的不可变资产键。
     * 绑定只读取负责人明确选择的来源 Bundle，不查询“最新资产”，否则历史工单会漂移。
     */
    private ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings loadDesignerRuntimeBindings(
            String tenantId,
            UUID bundleId
    ) {
        if (bundleId == null) {
            return ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings.empty();
        }
        List<RuntimeAssetRow> assets = jdbc.sql("""
                        SELECT v.asset_type, v.asset_key, v.definition::text AS definition_json
                          FROM cfg_configuration_bundle_item i
                          JOIN cfg_configuration_asset_version v
                            ON v.tenant_id = i.tenant_id
                           AND v.version_id = i.asset_version_id
                         WHERE i.tenant_id = :tenantId
                           AND i.bundle_id = :bundleId
                         ORDER BY v.asset_type, v.asset_key
                        """)
                .param("tenantId", tenantId)
                .param("bundleId", bundleId)
                .query((rs, rowNum) -> new RuntimeAssetRow(
                        rs.getString("asset_type"),
                        rs.getString("asset_key"),
                        rs.getString("definition_json")))
                .list();
        return new ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings(
                firstAssetKey(assets, "ASSIGNEE", null),
                firstAssetKey(assets, "DISPATCH", null),
                Map.of(),
                Map.of(),
                Map.of(),
                firstAssetKey(assets, "INTEGRATION", "OUTBOUND"));
    }

    /**
     * 把节点内从零创建的表单、证据和 SLA 草稿发布为当前版本独立资产。
     * 公共模板只提供初值；Workflow 最终只引用本次发布的不可变 assetKey。
     */
    private MaterializedDesignerAssets materializeDesignerAssets(
            String tenantId,
            ProfileRow profile,
            ProjectFulfillmentDocument document,
            String semanticVersion,
            ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings base
    ) {
        Map<String, String> formRefs = new LinkedHashMap<>();
        Map<String, String> evidenceRefs = new LinkedHashMap<>();
        Map<String, String> slaRefs = new LinkedHashMap<>();
        List<UUID> versionIds = new ArrayList<>();
        List<String> replacedKeys = new ArrayList<>();
        for (var node : document.nodes()) {
            if (!node.form().isEmpty()) {
                String key = nodeAssetKey(profile.profileId(), node.nodeId(), "form");
                versionIds.add(publishDesignerAsset(
                        tenantId, ConfigurationAssetType.FORM, key, semanticVersion,
                        designerFormDefinition(key, semanticVersion, node)).versionId());
                formRefs.put(node.nodeId(), key);
                replacedKeys.add(key);
            }
            if (!node.evidence().isEmpty()) {
                String key = nodeAssetKey(profile.profileId(), node.nodeId(), "evidence");
                versionIds.add(publishDesignerAsset(
                        tenantId, ConfigurationAssetType.EVIDENCE, key, semanticVersion,
                        designerEvidenceDefinition(key, semanticVersion, node)).versionId());
                evidenceRefs.put(node.nodeId(), key);
                replacedKeys.add(key);
            }
            if (!node.sla().isEmpty()) {
                String key = nodeAssetKey(profile.profileId(), node.nodeId(), "sla");
                versionIds.add(publishDesignerAsset(
                        tenantId, ConfigurationAssetType.SLA, key, semanticVersion,
                        designerSlaDefinition(key, semanticVersion, node)).versionId());
                slaRefs.put(node.nodeId(), key);
                replacedKeys.add(key);
            }
        }
        return new MaterializedDesignerAssets(
                new ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings(
                        base.assigneePolicyRef(),
                        base.dispatchPolicyRef(),
                        formRefs,
                        evidenceRefs,
                        slaRefs,
                        base.integrationRef()),
                List.copyOf(versionIds),
                List.copyOf(replacedKeys));
    }

    private com.serviceos.configuration.api.ConfigurationAssetVersionReference publishDesignerAsset(
            String tenantId,
            ConfigurationAssetType assetType,
            String assetKey,
            String semanticVersion,
            Map<String, Object> definition
    ) {
        try {
            String json = objectMapper.writeValueAsString(definition);
            return configurations.publishAsset(new PublishConfigurationAssetCommand(
                    tenantId, assetType, assetKey, semanticVersion, "1.0.0",
                    json, Sha256.digest(json)));
        } catch (Exception exception) {
            throw new IllegalStateException("节点资产发布失败: " + assetKey, exception);
        }
    }

    private Map<String, Object> designerFormDefinition(
            String key,
            String semanticVersion,
            com.serviceos.configuration.api.ProjectFulfillmentNodeDraft node
    ) {
        Object rawFields = node.form().get("fields");
        List<?> sourceFields = rawFields instanceof List<?> list ? list : List.of();
        List<Map<String, Object>> fields = new ArrayList<>();
        for (int index = 0; index < sourceFields.size(); index++) {
            Object raw = sourceFields.get(index);
            Map<?, ?> field = raw instanceof Map<?, ?> map ? map : Map.of();
            String fieldKey = textOr(field.get("fieldKey"), "field" + (index + 1));
            fields.add(Map.of(
                    "fieldKey", fieldKey,
                    "label", textOr(field.get("label"), "字段 " + (index + 1)),
                    "dataType", designerFormDataType(field.get("type")),
                    "binding", "task.input." + normalizeAssetSegment(node.nodeId())
                            + "." + fieldKey,
                    "required", Boolean.TRUE.equals(field.get("required"))));
        }
        if (fields.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    node.nodeName() + " 的表单已创建但没有字段");
        }
        return Map.of(
                "formKey", key,
                "version", semanticVersion,
                "title", textOr(node.form().get("formName"), node.nodeName() + "表单"),
                "stage", designerAssetStage(node.phaseId()),
                "sections", List.of(Map.of(
                        "sectionKey", "main",
                        "title", node.nodeName(),
                        "fields", fields)),
                "validationRules", List.of());
    }

    private Map<String, Object> designerEvidenceDefinition(
            String key,
            String semanticVersion,
            com.serviceos.configuration.api.ProjectFulfillmentNodeDraft node
    ) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < node.evidence().size(); index++) {
            Map<String, Object> item = node.evidence().get(index);
            int minimum = intOr(item.get("minCount"), Boolean.TRUE.equals(item.get("required")) ? 1 : 0);
            int maximum = Math.max(1, intOr(item.get("maxCount"), Math.max(minimum, 3)));
            items.add(Map.of(
                    "evidenceKey", textOr(item.get("evidenceKey"), "evidence" + (index + 1)),
                    "name", textOr(item.get("name"), "资料 " + (index + 1)),
                    "description", textOr(item.get("description"), node.nodeName() + "履约资料"),
                    "mediaType", designerEvidenceType(item.get("type")),
                    "required", Boolean.TRUE.equals(item.get("required")),
                    "capture", Map.of(
                            "allowCamera", true,
                            "allowGallery", true,
                            "minCount", minimum,
                            "maxCount", maximum,
                            "maxSizeBytes", 5_242_880),
                    "reviewPolicy", Map.of("reviewRequired", true)));
        }
        return Map.of(
                "templateKey", key,
                "version", semanticVersion,
                "title", node.nodeName() + "资料要求",
                "stage", designerAssetStage(node.phaseId()),
                "items", items);
    }

    private Map<String, Object> designerSlaDefinition(
            String key,
            String semanticVersion,
            com.serviceos.configuration.api.ProjectFulfillmentNodeDraft node
    ) {
        String taskType = textOr(node.task().get("taskType"), node.nodeId());
        long targetSeconds = Math.max(
                60L,
                (long) intOr(node.sla().get("targetMinutes"), 60) * 60L);
        return Map.of(
                "policyKey", key,
                "version", semanticVersion,
                "subjectType", "TASK",
                "taskTypes", List.of(taskType),
                "startEvent", "TASK_CREATED",
                "stopEvent", "TASK_COMPLETED",
                "clockMode", "ELAPSED",
                "targetDurationSeconds", targetSeconds);
    }

    private static String nodeAssetKey(UUID profileId, String nodeId, String suffix) {
        return "fulfillment." + profileId + "."
                + normalizeAssetSegment(nodeId) + "." + suffix;
    }

    private static String normalizeAssetSegment(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "-")
                .replaceAll("-+", "-");
    }

    private static String designerAssetStage(String phaseId) {
        if (phaseId == null || phaseId.isBlank()) {
            return "OTHER";
        }
        return phaseId.toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9_-]", "_");
    }

    private static String designerFormDataType(Object value) {
        String type = value == null ? "STRING" : value.toString().toUpperCase(java.util.Locale.ROOT);
        return switch (type) {
            // 设计器一期尚未采集稳定的枚举编码与选项集合，若直接发布为 ENUM，
            // Technician Web 会按能力契约拒绝打开该任务。这里发布为 STRING，
            // 等设计器能够完整冻结枚举选项后再升级类型，避免生成不可执行的配置。
            case "SELECT" -> "STRING";
            case "TEXTAREA" -> "TEXT";
            case "NUMBER" -> "DECIMAL";
            case "CHECKBOX" -> "BOOLEAN";
            case "STRING", "TEXT", "INTEGER", "DECIMAL", "BOOLEAN", "DATE", "DATETIME",
                    "ENUM", "MULTI_ENUM", "ADDRESS", "GEOPOINT", "SIGNATURE", "FILE_REF",
                    "OBJECT", "OBJECT_LIST" -> type;
            default -> "STRING";
        };
    }

    private static String designerEvidenceType(Object value) {
        String type = value == null ? "PHOTO" : value.toString().toUpperCase(java.util.Locale.ROOT);
        return Set.of("PHOTO", "VIDEO", "DOCUMENT", "SIGNATURE", "GENERATED_REPORT").contains(type)
                ? type : "DOCUMENT";
    }

    private static String textOr(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString().trim();
    }

    private static int intOr(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String firstAssetKey(
            List<RuntimeAssetRow> assets,
            String assetType,
            String direction
    ) {
        return assets.stream()
                .filter(asset -> assetType.equals(asset.assetType()))
                .filter(asset -> direction == null || direction.equals(assetDirection(asset)))
                .map(RuntimeAssetRow::assetKey)
                .findFirst()
                .orElse(null);
    }

    private String assetDirection(RuntimeAssetRow asset) {
        try {
            return objectMapper.readTree(asset.definitionJson()).path("direction").asText(null);
        } catch (Exception exception) {
            throw new IllegalStateException("配置资产方向无法解析: " + asset.assetKey(), exception);
        }
    }

    private static Map<String, Object> stage(
            String code, String name, int sequence, String ownerType, String taskType
    ) {
        return stage(code, name, sequence, ownerType, taskType, false);
    }

    private static Map<String, Object> stage(
            String code, String name, int sequence, String ownerType, String taskType, boolean terminal
    ) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("stageCode", code);
        stage.put("stageName", name);
        stage.put("sequence", sequence);
        stage.put("stageType", terminal ? "END" : "USER_TASK");
        stage.put("taskType", taskType);
        stage.put("ownerType", ownerType);
        stage.put("terminal", terminal);
        stage.put("formRefs", List.of());
        stage.put("evidenceRefs", List.of());
        stage.put("actions", List.of());
        stage.put("transitions", List.of());
        stage.put("exceptionPaths", List.of());
        return stage;
    }

    private List<String> allowedActions(String status) {
        List<String> actions = new ArrayList<>();
        actions.add("VIEW");
        actions.add("VIEW_REVISIONS");
        if ("RETIRED".equals(status)) {
            return List.copyOf(actions);
        }
        actions.add("EDIT_DRAFT");
        actions.add("VALIDATE");
        actions.add("COMPILE_PREVIEW");
        if ("SUSPENDED".equals(status)) {
            actions.add("RESUME");
        } else {
            actions.add("PUBLISH");
            if ("ACTIVE".equals(status)) {
                actions.add("SUSPEND");
            }
        }
        return List.copyOf(actions);
    }

    private void requireProject(String tenantId, UUID projectId) {
        Integer count = jdbc.sql("""
                SELECT COUNT(1) FROM prj_project
                 WHERE tenant_id = :tenantId AND project_id = :projectId
                """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query(Integer.class)
                .single();
        if (count == null || count == 0) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
    }

    private ProfileRow loadProfile(String tenantId, UUID projectId, UUID profileId) {
        return jdbc.sql("""
                SELECT * FROM cfg_project_fulfillment_profile
                 WHERE tenant_id = :tenantId AND project_id = :projectId AND profile_id = :profileId
                """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("profileId", profileId)
                .query((rs, n) -> mapProfile(rs))
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置不存在"));
    }

    private ProfileRow loadProfileById(String tenantId, UUID profileId) {
        return jdbc.sql("""
                SELECT * FROM cfg_project_fulfillment_profile
                 WHERE tenant_id = :tenantId AND profile_id = :profileId
                """)
                .param("tenantId", tenantId)
                .param("profileId", profileId)
                .query((rs, n) -> mapProfile(rs))
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置不存在"));
    }

    private DraftRow loadDraft(String tenantId, UUID draftRevisionId) {
        if (draftRevisionId == null) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置草稿不存在");
        }
        return jdbc.sql("""
                SELECT revision_id, document_json::text AS document_json,
                       workflow_asset_version_id, source_bundle_id,
                       simulation_document_digest
                  FROM cfg_project_fulfillment_revision
                 WHERE tenant_id = :tenantId AND revision_id = :id AND revision_status = 'DRAFT'
                """)
                .param("tenantId", tenantId)
                .param("id", draftRevisionId)
                .query((rs, n) -> new DraftRow(
                        rs.getObject("revision_id", UUID.class),
                        rs.getString("document_json"),
                        rs.getObject("workflow_asset_version_id", UUID.class),
                        rs.getObject("source_bundle_id", UUID.class),
                        rs.getString("simulation_document_digest")))
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "履约配置草稿不存在"));
    }

    private Map<String, Object> parseDocument(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "履约配置文档不是合法 JSON");
        }
    }

    private Map<String, Object> loadPublishedWorkflowDefinition(String tenantId, UUID versionId) {
        if (versionId == null) {
            return null;
        }
        String definitionJson = jdbc.sql("""
                SELECT definition::text FROM cfg_configuration_asset_version
                 WHERE tenant_id = :tenantId AND version_id = :versionId
                   AND asset_type = 'WORKFLOW' AND status = 'PUBLISHED'
                """)
                .param("tenantId", tenantId)
                .param("versionId", versionId)
                .query(String.class)
                .optional()
                .orElse(null);
        return definitionJson == null ? null : parseDocument(definitionJson);
    }

    private boolean bundleExists(String tenantId, UUID projectId, UUID bundleId) {
        Integer count = jdbc.sql("""
                SELECT COUNT(1) FROM cfg_configuration_bundle
                 WHERE tenant_id = :tenantId AND project_id = :projectId
                   AND bundle_id = :bundleId AND status = 'PUBLISHED'
                """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("bundleId", bundleId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    private BundleRef loadBundle(String tenantId, UUID bundleId) {
        return jdbc.sql("""
                SELECT bundle_id, bundle_version
                  FROM cfg_configuration_bundle
                 WHERE tenant_id = :tenantId AND bundle_id = :bundleId
                """)
                .param("tenantId", tenantId)
                .param("bundleId", bundleId)
                .query((rs, n) -> new BundleRef(
                        rs.getObject("bundle_id", UUID.class),
                        rs.getString("bundle_version")))
                .optional()
                .orElse(new BundleRef(bundleId, null));
    }

    private int nextPublishedVersion(String tenantId, UUID profileId) {
        Integer max = jdbc.sql("""
                SELECT COALESCE(MAX(version_no), 0)
                  FROM cfg_project_fulfillment_revision
                 WHERE tenant_id = :tenantId AND profile_id = :profileId
                   AND revision_status = 'PUBLISHED'
                """)
                .param("tenantId", tenantId)
                .param("profileId", profileId)
                .query(Integer.class)
                .single();
        return (max == null ? 0 : max) + 1;
    }

    private Counts countAssets(String documentJson) {
        if (documentJson == null || documentJson.isBlank()) {
            return new Counts(0, 0, 0, null, null);
        }
        try {
            Map<String, Object> doc = parseDocument(documentJson);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stages = (List<Map<String, Object>>) doc.getOrDefault(
                    "stages", List.of());
            int forms = 0;
            int evidence = 0;
            for (Map<String, Object> stage : stages) {
                Object fr = stage.get("formRefs");
                Object er = stage.get("evidenceRefs");
                if (fr instanceof List<?> list) {
                    forms += list.size();
                }
                if (er instanceof List<?> list) {
                    evidence += list.size();
                }
            }
            return new Counts(stages.size(), forms, evidence,
                    stages.isEmpty() ? null : stages.size() + " 个阶段",
                    null);
        } catch (RuntimeException ex) {
            return new Counts(0, 0, 0, null, null);
        }
    }

    private static ProfileRow mapProfile(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProfileRow(
                rs.getObject("profile_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("profile_code"),
                rs.getString("service_product_code"),
                rs.getString("profile_name"),
                rs.getString("description"),
                rs.getInt("match_priority"),
                rs.getString("status"),
                rs.getObject("draft_revision_id", UUID.class),
                rs.getObject("active_revision_id", UUID.class),
                rs.getLong("aggregate_version"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class)));
    }

    private static ProjectFulfillmentRevisionView mapRevision(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new ProjectFulfillmentRevisionView(
                rs.getObject("revision_id", UUID.class),
                rs.getObject("profile_id", UUID.class),
                rs.getInt("version_no"),
                rs.getString("revision_status"),
                rs.getString("document_json"),
                rs.getString("manifest_json"),
                rs.getString("validation_json"),
                rs.getString("content_digest"),
                rs.getObject("source_bundle_id", UUID.class),
                rs.getObject("workflow_asset_version_id", UUID.class),
                toInstant(rs.getObject("effective_from", OffsetDateTime.class)),
                toInstant(rs.getObject("effective_to", OffsetDateTime.class)),
                rs.getObject("supersedes_revision_id", UUID.class),
                rs.getString("published_by"),
                null,
                toInstant(rs.getObject("published_at", OffsetDateTime.class)),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)));
    }

    /**
     * 发布记录持久化稳定的主体标识，产品读模型在读取时通过 identity 公开端口解析当前显示名。
     * 解析失败时保持为空，禁止把主体 UUID 当作人员名称返回给产品页面。
     */
    private ProjectFulfillmentRevisionView enrichPublisherDisplayName(
            String tenantId,
            ProjectFulfillmentRevisionView revision
    ) {
        UUID publisherId;
        try {
            publisherId = revision.publishedBy() == null
                    ? null
                    : UUID.fromString(revision.publishedBy());
        } catch (IllegalArgumentException ignored) {
            publisherId = null;
        }
        String displayName = publisherId == null
                ? null
                : personas.displayName(tenantId, publisherId).orElse(null);
        return new ProjectFulfillmentRevisionView(
                revision.revisionId(),
                revision.profileId(),
                revision.versionNo(),
                revision.revisionStatus(),
                revision.documentJson(),
                revision.manifestJson(),
                revision.validationJson(),
                revision.contentDigest(),
                revision.sourceBundleId(),
                revision.workflowAssetVersionId(),
                revision.effectiveFrom(),
                revision.effectiveTo(),
                revision.supersedesRevisionId(),
                revision.publishedBy(),
                displayName,
                revision.publishedAt(),
                revision.createdAt());
    }


    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    /**
     * 发布期阻止运行时无法唯一解释的方案重叠。
     *
     * <p>同一服务产品下，只有优先级和具体度都相同且每个结构化维度存在交集时才阻断。
     * 空集合是通配，因此与任意明确集合存在交集。校验发生在关闭旧版本之前，失败时事务
     * 不会改变当前生效版本或草稿。</p>
     */
    private void validateMatchRuleConflicts(
            String tenantId,
            ProfileRow profile,
            ProjectFulfillmentDocument proposed,
            Instant effectiveFrom
    ) {
        List<PublishedMatchRule> others = jdbc.sql("""
                SELECT p.profile_id, p.profile_code, p.profile_name, p.match_priority,
                       r.document_json::text AS document_json
                  FROM cfg_project_fulfillment_profile p
                  JOIN cfg_project_fulfillment_revision r
                    ON r.tenant_id = p.tenant_id AND r.profile_id = p.profile_id
                 WHERE p.tenant_id = :tenantId
                   AND p.project_id = :projectId
                   AND p.service_product_code = :product
                   AND p.profile_id <> :profileId
                   AND p.status = 'ACTIVE'
                   AND r.revision_status = 'PUBLISHED'
                   AND r.effective_from <= :at
                   AND (r.effective_to IS NULL OR r.effective_to > :at)
                """)
                .param("tenantId", tenantId)
                .param("projectId", profile.projectId())
                .param("product", profile.serviceProductCode())
                .param("profileId", profile.profileId())
                .param("at", PostgresJdbcParameters.timestamptz(effectiveFrom))
                .query((rs, rowNum) -> new PublishedMatchRule(
                        rs.getObject("profile_id", UUID.class),
                        rs.getString("profile_code"),
                        rs.getString("profile_name"),
                        rs.getInt("match_priority"),
                        documentMapper.fromJson(rs.getString("document_json")).matchRule()))
                .list();
        for (PublishedMatchRule other : others) {
            if (profile.matchPriority() != other.matchPriority()
                    || proposed.matchRule().constrainedDimensionCount()
                    != other.rule().constrainedDimensionCount()) {
                continue;
            }
            if (overlaps(proposed.matchRule().brandCodes(), other.rule().brandCodes())
                    && overlaps(proposed.matchRule().provinceCodes(), other.rule().provinceCodes())) {
                throw new BusinessProblem(
                        ProblemCode.VALIDATION_FAILED,
                        "履约方案与“" + other.profileName() + "”（" + other.profileCode()
                                + "）的适用范围、优先级和具体度冲突");
            }
        }
    }

    private static boolean overlaps(List<String> left, List<String> right) {
        return left.isEmpty() || right.isEmpty() || left.stream().anyMatch(right::contains);
    }

    private record ProfileRow(
            UUID profileId,
            UUID projectId,
            String profileCode,
            String serviceProductCode,
            String profileName,
            String description,
            int matchPriority,
            String status,
            UUID draftRevisionId,
            UUID activeRevisionId,
            long aggregateVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private record PublishedMatchRule(
            UUID profileId,
            String profileCode,
            String profileName,
            int matchPriority,
            com.serviceos.configuration.api.ProjectFulfillmentMatchRule rule
    ) {
    }

    private record DraftRow(
            UUID revisionId,
            String documentJson,
            UUID workflowAssetVersionId,
            UUID sourceBundleId,
            String simulationDocumentDigest
    ) {
    }

    private record RuntimeBinding(
            UUID workflowAssetVersionId,
            UUID bundleId,
            String bundleVersion
    ) {
    }

    private record RuntimeAssetRow(
            String assetType,
            String assetKey,
            String definitionJson
    ) {
    }

    private record MaterializedDesignerAssets(
            ProjectFulfillmentWorkflowCompiler.RuntimeAssetBindings bindings,
            List<UUID> assetVersionIds,
            List<String> replacedAssetKeys
    ) {
    }

    private record BundleRef(UUID bundleId, String bundleVersion) {
    }

    private record InitialDraft(
            String documentJson,
            UUID workflowAssetVersionId,
            UUID sourceBundleId
    ) {
    }

    private record Counts(
            int stages, int forms, int evidence, String workflowSummary, String slaSummary
    ) {
    }
}
