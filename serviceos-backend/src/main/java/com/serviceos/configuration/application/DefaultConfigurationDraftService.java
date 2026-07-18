package com.serviceos.configuration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ApproveConfigurationDraftCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationAssetVersionReference;
import com.serviceos.configuration.api.ConfigurationDraftDiffView;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.CreateConfigurationDraftCommand;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.UpdateConfigurationDraftCommand;
import com.serviceos.configuration.infrastructure.ConfigurationAssetSchemaValidator;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import com.serviceos.shared.infrastructure.PostgresJdbcParameters;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 配置设计器草稿服务。
 *
 * <p>事务边界：草稿写/审批/发布同事务；发布成功后标记 PUBLISHED 并绑定不可变 versionId。
 * 生命周期：DRAFT → VALIDATED → APPROVED → PUBLISHED。其余资产类型失败关闭。</p>
 */
@Service
final class DefaultConfigurationDraftService implements ConfigurationDraftService {
    private static final String WRITE = "configuration.draft.write";
    private static final String APPROVE = "configuration.approve";
    private static final String PUBLISH = "configuration.publish";
    private static final String RESOURCE = "ConfigurationDraft";
    private static final Set<ConfigurationAssetType> DESIGNER_TYPES = Set.of(
            ConfigurationAssetType.WORKFLOW,
            ConfigurationAssetType.FORM,
            ConfigurationAssetType.EVIDENCE,
            ConfigurationAssetType.SLA,
            ConfigurationAssetType.RULE,
            ConfigurationAssetType.DISPATCH);

    private final JdbcClient jdbc;
    private final AuthorizationService authorization;
    private final ConfigurationService configurations;
    private final ConfigurationAssetSchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    DefaultConfigurationDraftService(
            JdbcClient jdbc,
            AuthorizationService authorization,
            ConfigurationService configurations,
            ConfigurationAssetSchemaValidator schemaValidator,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.authorization = authorization;
        this.configurations = configurations;
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ConfigurationDraftView create(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            CreateConfigurationDraftCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(command, "command");
        requireDesignerSupported(command.assetType());
        String definition = requireDefinition(command.definitionJson());
        String assetKey = requireText(command.assetKey(), "assetKey", 128);
        String semanticVersion = requireText(command.intendedSemanticVersion(), "intendedSemanticVersion", 64);
        String schemaVersion = requireText(command.schemaVersion(), "schemaVersion", 64);
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, assetKey), metadata.correlationId());

        UUID draftId = UUID.randomUUID();
        Instant now = clock.instant();
        String digest = Sha256.digest(definition);
        UUID baseVersionId = command.baseVersionId() != null
                ? command.baseVersionId()
                : findLatestPublishedVersionId(principal.tenantId(), command.assetType(), assetKey);
        jdbc.sql("""
                        INSERT INTO cfg_configuration_asset_draft (
                            draft_id, tenant_id, asset_type, asset_key, intended_semantic_version,
                            schema_version, definition, content_digest, status, base_version_id,
                            published_version_id, validation_errors, aggregate_version,
                            created_by, updated_by, created_at, updated_at
                        ) VALUES (
                            :draftId, :tenantId, :assetType, :assetKey, :semanticVersion,
                            :schemaVersion, CAST(:definition AS jsonb), :digest, 'DRAFT', :baseVersionId,
                            NULL, NULL, 1, :actor, :actor, :now, :now
                        )
                        """)
                .param("draftId", draftId)
                .param("tenantId", principal.tenantId())
                .param("assetType", command.assetType().name())
                .param("assetKey", assetKey)
                .param("semanticVersion", semanticVersion)
                .param("schemaVersion", schemaVersion)
                .param("definition", definition)
                .param("digest", digest)
                .param("baseVersionId", baseVersionId)
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .update();
        return requireDraft(principal.tenantId(), draftId);
    }

    @Override
    @Transactional
    public ConfigurationDraftView update(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UpdateConfigurationDraftCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(command, "command");
        ConfigurationDraftView current = requireDraft(principal.tenantId(), command.draftId());
        requireDesignerSupported(current.assetType());
        if (!Set.of("DRAFT", "VALIDATED", "APPROVED").contains(current.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "仅 DRAFT/VALIDATED/APPROVED 草稿可更新，当前状态=" + current.status());
        }
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, current.draftId().toString()),
                metadata.correlationId());
        String definition = requireDefinition(command.definitionJson());
        String digest = Sha256.digest(definition);
        Instant now = clock.instant();
        // 编辑会使审批失效，强制回到 DRAFT。
        int updated = jdbc.sql("""
                        UPDATE cfg_configuration_asset_draft
                           SET definition = CAST(:definition AS jsonb),
                               content_digest = :digest,
                               status = 'DRAFT',
                               validation_errors = NULL,
                               approval_ref = NULL,
                               approved_by = NULL,
                               approved_at = NULL,
                               aggregate_version = aggregate_version + 1,
                               updated_by = :actor,
                               updated_at = :now
                         WHERE tenant_id = :tenantId
                           AND draft_id = :draftId
                           AND aggregate_version = :expectedVersion
                           AND status IN ('DRAFT', 'VALIDATED', 'APPROVED')
                        """)
                .param("definition", definition)
                .param("digest", digest)
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", principal.tenantId())
                .param("draftId", command.draftId())
                .param("expectedVersion", command.expectedVersion())
                .update();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "配置草稿版本冲突或状态已变更");
        }
        return requireDraft(principal.tenantId(), command.draftId());
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationDraftView get(CurrentPrincipal principal, String correlationId, UUID draftId) {
        ConfigurationDraftView draft = requireDraft(principal.tenantId(), draftId);
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, draftId.toString()), correlationId);
        return draft;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigurationDraftView> list(
            CurrentPrincipal principal,
            String correlationId,
            ConfigurationAssetType assetType
    ) {
        Objects.requireNonNull(assetType, "assetType");
        requireDesignerSupported(assetType);
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, assetType.name()), correlationId);
        return jdbc.sql("""
                        SELECT * FROM cfg_configuration_asset_draft
                         WHERE tenant_id = :tenantId
                           AND asset_type = :assetType
                           AND status IN ('DRAFT', 'VALIDATED', 'APPROVED', 'PUBLISHED')
                         ORDER BY updated_at DESC
                         LIMIT 100
                        """)
                .param("tenantId", principal.tenantId())
                .param("assetType", assetType.name())
                .query((rs, rowNum) -> map(rs))
                .list();
    }

    @Override
    @Transactional(noRollbackFor = ConfigurationPublicationException.class)
    public ConfigurationDraftView validate(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID draftId
    ) {
        ConfigurationDraftView current = requireDraft(principal.tenantId(), draftId);
        requireDesignerSupported(current.assetType());
        if (!"DRAFT".equals(current.status()) && !"VALIDATED".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "仅 DRAFT/VALIDATED 草稿可校验，当前状态=" + current.status());
        }
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, draftId.toString()),
                metadata.correlationId());

        List<String> errors = List.of();
        String nextStatus = "VALIDATED";
        try {
            schemaValidator.validate(new PublishConfigurationAssetCommand(
                    principal.tenantId(), current.assetType(), current.assetKey(),
                    current.intendedSemanticVersion(), current.schemaVersion(),
                    current.definitionJson(), current.contentDigest()));
        } catch (ConfigurationPublicationException | IllegalArgumentException exception) {
            errors = List.of(exception.getMessage());
            nextStatus = "DRAFT";
        }

        Instant now = clock.instant();
        jdbc.sql("""
                        UPDATE cfg_configuration_asset_draft
                           SET status = :status,
                               validation_errors = CAST(:errors AS jsonb),
                               aggregate_version = aggregate_version + 1,
                               updated_by = :actor,
                               updated_at = :now
                         WHERE tenant_id = :tenantId
                           AND draft_id = :draftId
                           AND status IN ('DRAFT', 'VALIDATED')
                        """)
                .param("status", nextStatus)
                .param("errors", writeJson(errors))
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", principal.tenantId())
                .param("draftId", draftId)
                .update();
        ConfigurationDraftView result = requireDraft(principal.tenantId(), draftId);
        if (!errors.isEmpty()) {
            throw new ConfigurationPublicationException(
                    "配置草稿校验失败: " + String.join("; ", errors));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationDraftDiffView diff(
            CurrentPrincipal principal,
            String correlationId,
            UUID draftId
    ) {
        ConfigurationDraftView current = requireDraft(principal.tenantId(), draftId);
        requireDesignerSupported(current.assetType());
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, draftId.toString()), correlationId);
        String baseJson = "";
        String baseLabel = "empty-base";
        if (current.baseVersionId() != null) {
            baseJson = loadPublishedDefinition(principal.tenantId(), current.baseVersionId());
            baseLabel = "published:" + current.baseVersionId();
        }
        String draftPretty = pretty(current.definitionJson());
        String basePretty = pretty(baseJson.isBlank() ? "{}" : baseJson);
        String unified = unifiedDiff(basePretty, draftPretty, baseLabel, "draft:" + draftId);
        return new ConfigurationDraftDiffView(
                draftId, current.baseVersionId(), baseLabel, "draft:" + draftId,
                unified, basePretty.equals(draftPretty));
    }

    @Override
    @Transactional
    public ConfigurationDraftView approve(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            ApproveConfigurationDraftCommand command
    ) {
        Objects.requireNonNull(command, "command");
        ConfigurationDraftView current = requireDraft(principal.tenantId(), command.draftId());
        requireDesignerSupported(current.assetType());
        if (!"VALIDATED".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "仅 VALIDATED 草稿可审批，当前状态=" + current.status());
        }
        String approvalRef = requireText(command.approvalRef(), "approvalRef", 128);
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                APPROVE, principal.tenantId(), RESOURCE, command.draftId().toString()),
                metadata.correlationId());
        Instant now = clock.instant();
        int updated = jdbc.sql("""
                        UPDATE cfg_configuration_asset_draft
                           SET status = 'APPROVED',
                               approval_ref = :approvalRef,
                               approved_by = :actor,
                               approved_at = :now,
                               aggregate_version = aggregate_version + 1,
                               updated_by = :actor,
                               updated_at = :now
                         WHERE tenant_id = :tenantId
                           AND draft_id = :draftId
                           AND aggregate_version = :expectedVersion
                           AND status = 'VALIDATED'
                        """)
                .param("approvalRef", approvalRef)
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", principal.tenantId())
                .param("draftId", command.draftId())
                .param("expectedVersion", command.expectedVersion())
                .update();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "配置草稿审批时版本或状态冲突");
        }
        return requireDraft(principal.tenantId(), command.draftId());
    }

    @Override
    @Transactional
    public ConfigurationDraftView publish(
            CurrentPrincipal principal,
            CommandMetadata metadata,
            UUID draftId
    ) {
        ConfigurationDraftView current = requireDraft(principal.tenantId(), draftId);
        requireDesignerSupported(current.assetType());
        if (!"APPROVED".equals(current.status())) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "仅 APPROVED 草稿可发布，当前状态=" + current.status());
        }
        if (current.approvalRef() == null || current.approvalRef().isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "发布前必须存在审批引用");
        }
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                PUBLISH, principal.tenantId(), RESOURCE, draftId.toString()),
                metadata.correlationId());

        // jsonb 回读可能改变空白/键序；发布摘要必须以当前 definition 文本重算。
        String publishDefinition = current.definitionJson().trim();
        String publishDigest = Sha256.digest(publishDefinition);
        ConfigurationAssetVersionReference published = configurations.publishAsset(
                new PublishConfigurationAssetCommand(
                        principal.tenantId(), current.assetType(), current.assetKey(),
                        current.intendedSemanticVersion(), current.schemaVersion(),
                        publishDefinition, publishDigest));

        Instant now = clock.instant();
        int updated = jdbc.sql("""
                        UPDATE cfg_configuration_asset_draft
                           SET status = 'PUBLISHED',
                               published_version_id = :versionId,
                               aggregate_version = aggregate_version + 1,
                               updated_by = :actor,
                               updated_at = :now
                         WHERE tenant_id = :tenantId
                           AND draft_id = :draftId
                           AND status = 'APPROVED'
                        """)
                .param("versionId", published.versionId())
                .param("actor", principal.principalId())
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .param("tenantId", principal.tenantId())
                .param("draftId", draftId)
                .update();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "配置草稿发布时状态已变更");
        }
        return requireDraft(principal.tenantId(), draftId);
    }

    private ConfigurationDraftView requireDraft(String tenantId, UUID draftId) {
        return jdbc.sql("""
                        SELECT * FROM cfg_configuration_asset_draft
                         WHERE tenant_id = :tenantId AND draft_id = :draftId
                        """)
                .param("tenantId", tenantId)
                .param("draftId", draftId)
                .query((rs, rowNum) -> map(rs))
                .optional()
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "配置草稿不存在"));
    }

    private ConfigurationDraftView map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ConfigurationDraftView(
                rs.getObject("draft_id", UUID.class),
                ConfigurationAssetType.valueOf(rs.getString("asset_type")),
                rs.getString("asset_key"),
                rs.getString("intended_semantic_version"),
                rs.getString("schema_version"),
                rs.getString("definition"),
                rs.getString("content_digest"),
                rs.getString("status"),
                rs.getObject("base_version_id", UUID.class),
                rs.getObject("published_version_id", UUID.class),
                readErrors(rs.getString("validation_errors")),
                rs.getString("approval_ref"),
                rs.getString("approved_by"),
                rs.getTimestamp("approved_at") == null
                        ? null : rs.getTimestamp("approved_at").toInstant(),
                rs.getLong("aggregate_version"),
                rs.getString("created_by"),
                rs.getString("updated_by"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private UUID findLatestPublishedVersionId(
            String tenantId,
            ConfigurationAssetType assetType,
            String assetKey
    ) {
        return jdbc.sql("""
                        SELECT version_id
                          FROM cfg_configuration_asset_version
                         WHERE tenant_id = :tenantId
                           AND asset_type = :assetType
                           AND asset_key = :assetKey
                           AND status = 'PUBLISHED'
                         ORDER BY published_at DESC
                         LIMIT 1
                        """)
                .param("tenantId", tenantId)
                .param("assetType", assetType.name())
                .param("assetKey", assetKey)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private String loadPublishedDefinition(String tenantId, UUID versionId) {
        return jdbc.sql("""
                        SELECT definition::text
                          FROM cfg_configuration_asset_version
                         WHERE tenant_id = :tenantId AND version_id = :versionId
                        """)
                .param("tenantId", tenantId)
                .param("versionId", versionId)
                .query(String.class)
                .optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.RESOURCE_NOT_FOUND, "基线发布版本不存在"));
    }

    private String pretty(String json) {
        try {
            var tree = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
        } catch (JacksonException exception) {
            return json == null ? "" : json;
        }
    }

    /** 最小统一 diff：按行 Myers 简化为 LCS 标记，足够审计对照。 */
    private static String unifiedDiff(String left, String right, String leftLabel, String rightLabel) {
        String[] a = left.split("\\R", -1);
        String[] b = right.split("\\R", -1);
        StringBuilder out = new StringBuilder();
        out.append("--- ").append(leftLabel).append('\n');
        out.append("+++ ").append(rightLabel).append('\n');
        int i = 0;
        int j = 0;
        while (i < a.length || j < b.length) {
            if (i < a.length && j < b.length && Objects.equals(a[i], b[j])) {
                out.append(' ').append(a[i]).append('\n');
                i++;
                j++;
                continue;
            }
            if (j < b.length && (i >= a.length || !containsFrom(a, i, b[j]))) {
                out.append('+').append(b[j]).append('\n');
                j++;
                continue;
            }
            if (i < a.length) {
                out.append('-').append(a[i]).append('\n');
                i++;
            }
        }
        return out.toString();
    }

    private static boolean containsFrom(String[] lines, int start, String value) {
        for (int i = start; i < Math.min(lines.length, start + 20); i++) {
            if (Objects.equals(lines[i], value)) {
                return true;
            }
        }
        return false;
    }

    private List<String> readErrors(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("validation_errors 无法解码", exception);
        }
    }

    private String writeJson(List<String> errors) {
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JacksonException exception) {
            throw new IllegalStateException("validation_errors 无法编码", exception);
        }
    }

    private static void requireDesignerSupported(ConfigurationAssetType assetType) {
        if (!DESIGNER_TYPES.contains(assetType)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "配置设计器暂不支持资产类型 " + assetType
                            + "；允许 WORKFLOW/FORM/EVIDENCE/SLA");
        }
    }

    private static String requireDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("definitionJson must not be blank");
        }
        String normalized = definitionJson.trim();
        if (normalized.length() > 1_000_000) {
            throw new IllegalArgumentException("definitionJson exceeds max length");
        }
        return normalized;
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(field + " exceeds max length");
        }
        return normalized;
    }
}
