package com.serviceos.configuration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationDecision;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.CreateProjectFulfillmentProfileCommand;
import com.serviceos.configuration.api.ProjectFulfillmentCompareImpact;
import com.serviceos.configuration.api.ProjectFulfillmentDraftView;
import com.serviceos.configuration.api.ProjectFulfillmentManifestView;
import com.serviceos.configuration.api.ProjectFulfillmentProfileDetail;
import com.serviceos.configuration.api.ProjectFulfillmentProfileService;
import com.serviceos.configuration.api.ProjectFulfillmentProfileStatus;
import com.serviceos.configuration.api.ProjectFulfillmentProfileSummary;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionStatus;
import com.serviceos.configuration.api.ProjectFulfillmentRevisionView;
import com.serviceos.configuration.api.ProjectFulfillmentSchemeCount;
import com.serviceos.configuration.api.ProjectFulfillmentValidationIssue;
import com.serviceos.configuration.api.UpdateProjectFulfillmentDraftCommand;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.infrastructure.PostgresJdbcParameters;
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

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final AuditAppender audit;
    private final ProjectFulfillmentDraftValidator validator;
    private final ProjectFulfillmentManifestCompiler compiler;
    private final ProjectFulfillmentRunbookAssembler runbookAssembler;
    private final ProjectFulfillmentCompareAnalyzer compareAnalyzer;
    private final ProjectFulfillmentDocumentMapper documentMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultProjectFulfillmentProfileService(
            JdbcClient jdbc,
            AuthorizationService authorization,
            AuditAppender audit,
            ProjectFulfillmentDraftValidator validator,
            ProjectFulfillmentManifestCompiler compiler,
            ProjectFulfillmentRunbookAssembler runbookAssembler,
            ProjectFulfillmentCompareAnalyzer compareAnalyzer,
            ProjectFulfillmentDocumentMapper documentMapper,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.audit = audit;
        this.validator = validator;
        this.compiler = compiler;
        this.runbookAssembler = runbookAssembler;
        this.compareAnalyzer = compareAnalyzer;
        this.documentMapper = documentMapper;
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
        String documentJson = initialDocument(command);
        try {
            jdbc.sql("""
                    INSERT INTO cfg_project_fulfillment_profile (
                        profile_id, tenant_id, project_id, service_product_code, profile_name,
                        description, status, active_revision_id, draft_revision_id,
                        aggregate_version, created_by, updated_by, created_at, updated_at
                    ) VALUES (
                        :profileId, :tenantId, :projectId, :product, :name,
                        :description, 'DRAFT', NULL, NULL,
                        1, :actor, :actor, :now, :now
                    )
                    """)
                    .param("profileId", profileId)
                    .param("tenantId", principal.tenantId())
                    .param("projectId", command.projectId())
                    .param("product", command.serviceProductCode())
                    .param("name", command.profileName())
                    .param("description", command.description())
                    .param("actor", principal.principalId())
                    .param("now", PostgresJdbcParameters.timestamptz(now))
                    .update();
        } catch (DuplicateKeyException ex) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "同一项目下该工单类型已存在履约配置");
        }

        jdbc.sql("""
                INSERT INTO cfg_project_fulfillment_revision (
                    revision_id, tenant_id, profile_id, version_no, revision_status,
                    document_json, created_at
                ) VALUES (
                    :revisionId, :tenantId, :profileId, 0, 'DRAFT',
                    CAST(:document AS jsonb), :now
                )
                """)
                .param("revisionId", draftRevisionId)
                .param("tenantId", principal.tenantId())
                .param("profileId", profileId)
                .param("document", documentJson)
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
    @Transactional(readOnly = true)
    public List<ProjectFulfillmentProfileSummary> list(
            CurrentPrincipal principal, String correlationId, UUID projectId
    ) {
        requireProject(principal.tenantId(), projectId);
        authorization.require(principal, AuthorizationRequest.projectCapability(
                READ, principal.tenantId(), RESOURCE, projectId.toString(), projectId.toString()),
                correlationId);
        return jdbc.sql("""
                SELECT p.profile_id, p.project_id, p.service_product_code, p.profile_name, p.status,
                       p.aggregate_version, p.updated_at,
                       r.version_no AS active_version_no, r.effective_from,
                       COALESCE(r.document_json, d.document_json) AS document_json
                  FROM cfg_project_fulfillment_profile p
             LEFT JOIN cfg_project_fulfillment_revision r
                    ON r.revision_id = p.active_revision_id
             LEFT JOIN cfg_project_fulfillment_revision d
                    ON d.revision_id = p.draft_revision_id
                 WHERE p.tenant_id = :tenantId AND p.project_id = :projectId
                 ORDER BY p.service_product_code
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
                            rs.getString("service_product_code"),
                            rs.getString("profile_name"),
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
                row.profileId(), row.projectId(), row.serviceProductCode(), row.profileName(),
                row.description(), row.status(), row.draftRevisionId(), row.activeRevisionId(),
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
                       validation_json
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
                       workflow_asset_version_id = :workflowVersionId,
                       source_bundle_id = :bundleId,
                       validation_json = NULL
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
        boolean workflowOk = draft.workflowAssetVersionId() != null
                && assetVersionExists(principal.tenantId(), draft.workflowAssetVersionId(), "WORKFLOW");
        boolean bundleOk = draft.sourceBundleId() != null
                && bundleExists(principal.tenantId(), projectId, draft.sourceBundleId());
        List<ProjectFulfillmentValidationIssue> issues = validator.validate(
                profileId, document, workflowOk, bundleOk);
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
    @Transactional(readOnly = true)
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
        boolean workflowOk = draft.workflowAssetVersionId() != null
                && assetVersionExists(principal.tenantId(), draft.workflowAssetVersionId(), "WORKFLOW");
        boolean bundleOk = draft.sourceBundleId() != null
                && bundleExists(principal.tenantId(), projectId, draft.sourceBundleId());
        List<ProjectFulfillmentValidationIssue> issues = validator.validate(
                profileId, document, workflowOk, bundleOk);
        if (issues.stream().anyMatch(i -> "ERROR".equals(i.severity()))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "履约配置存在阻断错误，无法发布：" + issues.getFirst().userMessage());
        }
        if (draft.sourceBundleId() == null || draft.workflowAssetVersionId() == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "发布前必须绑定 Workflow 与 Bundle");
        }
        Instant from = effectiveFrom == null ? clock.instant() : effectiveFrom;
        if (from.isBefore(clock.instant().minusSeconds(1))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "生效时间不能早于当前时间");
        }
        int nextVersion = nextPublishedVersion(principal.tenantId(), profileId);
        UUID publishedRevisionId = UUID.randomUUID();
        BundleRef bundle = loadBundle(principal.tenantId(), draft.sourceBundleId());
        var compiled = compiler.compile(
                profileId, publishedRevisionId, projectId, profile.serviceProductCode(),
                profile.profileName(), String.valueOf(nextVersion), bundle.bundleId(),
                bundle.bundleVersion(), draft.workflowAssetVersionId(), from, document);
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
                    .param("bundleId", draft.sourceBundleId())
                    .param("workflowVersionId", draft.workflowAssetVersionId())
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
                       validation_json = NULL
                 WHERE revision_id = :draftId AND tenant_id = :tenantId AND revision_status = 'DRAFT'
                """)
                .param("document", draft.documentJson())
                .param("workflowVersionId", draft.workflowAssetVersionId())
                .param("bundleId", draft.sourceBundleId())
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
        return jdbc.sql("""
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
        return jdbc.sql("""
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

    private String initialDocument(CreateProjectFulfillmentProfileCommand command) {
        if (command.copyFromProfileId() != null) {
            String copied = jdbc.sql("""
                    SELECT document_json::text
                      FROM cfg_project_fulfillment_revision r
                      JOIN cfg_project_fulfillment_profile p ON p.draft_revision_id = r.revision_id
                     WHERE p.profile_id = :profileId
                    """)
                    .param("profileId", command.copyFromProfileId())
                    .query(String.class)
                    .optional()
                    .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                            "复制来源履约配置不存在"));
            return copied;
        }
        String template = command.templateCode() == null
                ? TEMPLATE_SURVEY_INSTALL
                : command.templateCode();
        if (!TEMPLATE_SURVEY_INSTALL.equals(template)
                && !"BLANK".equals(template)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "不支持的起始模板：" + template);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", "1.0.0");
        doc.put("orderTypeName", "勘测安装");
        doc.put("supportedClientKinds", List.of("ADMIN_WEB", "NETWORK_WEB", "TECHNICIAN_WEB"));
        if ("BLANK".equals(template)) {
            doc.put("stages", List.of());
        } else {
            doc.put("stages", List.of(
                    stage("INTAKE", "接单受理", 1, "PLATFORM", "DISPATCH"),
                    stage("SURVEY", "现场勘测", 2, "TECHNICIAN", "SURVEY"),
                    stage("INSTALLATION", "上门安装", 3, "TECHNICIAN", "INSTALL"),
                    stage("FINAL_REVIEW", "终审", 4, "PLATFORM", "REVIEW", true)));
        }
        try {
            return objectMapper.writeValueAsString(doc);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
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
                       workflow_asset_version_id, source_bundle_id
                  FROM cfg_project_fulfillment_revision
                 WHERE tenant_id = :tenantId AND revision_id = :id AND revision_status = 'DRAFT'
                """)
                .param("tenantId", tenantId)
                .param("id", draftRevisionId)
                .query((rs, n) -> new DraftRow(
                        rs.getObject("revision_id", UUID.class),
                        rs.getString("document_json"),
                        rs.getObject("workflow_asset_version_id", UUID.class),
                        rs.getObject("source_bundle_id", UUID.class)))
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

    private boolean assetVersionExists(String tenantId, UUID versionId, String assetType) {
        Integer count = jdbc.sql("""
                SELECT COUNT(1) FROM cfg_configuration_asset_version
                 WHERE tenant_id = :tenantId AND version_id = :versionId
                   AND asset_type = :assetType AND status = 'PUBLISHED'
                """)
                .param("tenantId", tenantId)
                .param("versionId", versionId)
                .param("assetType", assetType)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
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
                rs.getString("service_product_code"),
                rs.getString("profile_name"),
                rs.getString("description"),
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
                toInstant(rs.getObject("published_at", OffsetDateTime.class)),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)));
    }


    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record ProfileRow(
            UUID profileId,
            UUID projectId,
            String serviceProductCode,
            String profileName,
            String description,
            String status,
            UUID draftRevisionId,
            UUID activeRevisionId,
            long aggregateVersion,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    private record DraftRow(
            UUID revisionId,
            String documentJson,
            UUID workflowAssetVersionId,
            UUID sourceBundleId
    ) {
    }

    private record BundleRef(UUID bundleId, String bundleVersion) {
    }

    private record Counts(
            int stages, int forms, int evidence, String workflowSummary, String slaSummary
    ) {
    }
}
