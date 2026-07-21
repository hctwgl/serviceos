package com.serviceos.configuration.application;

import com.serviceos.audit.api.AuditAppender;
import com.serviceos.audit.api.AuditEntry;
import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.ApproveConfigurationDraftCommand;
import com.serviceos.configuration.api.ClientCompatibilityReport;
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
import com.serviceos.jooq.generated.tables.CfgConfigurationAssetClientTarget;
import com.serviceos.jooq.generated.tables.CfgConfigurationAssetDraft;
import com.serviceos.jooq.generated.tables.CfgConfigurationAssetVersion;
import com.serviceos.jooq.generated.tables.records.CfgConfigurationAssetDraftRecord;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandMetadata;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.Sha256;
import org.jooq.DSLContext;
import org.jooq.UpdateConditionStep;
import org.jooq.UpdateSetMoreStep;
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

import static com.serviceos.jooq.generated.tables.CfgConfigurationAssetClientTarget.CFG_CONFIGURATION_ASSET_CLIENT_TARGET;
import static com.serviceos.jooq.generated.tables.CfgConfigurationAssetDraft.CFG_CONFIGURATION_ASSET_DRAFT;
import static com.serviceos.jooq.generated.tables.CfgConfigurationAssetVersion.CFG_CONFIGURATION_ASSET_VERSION;

/**
 * 配置设计器草稿服务（jOOQ）。
 *
 * <p>事务边界：草稿写/审批/发布同事务；发布成功后标记 PUBLISHED 并绑定不可变 versionId。
 * 生命周期：DRAFT → VALIDATED → APPROVED → PUBLISHED。其余资产类型失败关闭。</p>
 */
@Service
final class JooqConfigurationDraftService implements ConfigurationDraftService {
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
            ConfigurationAssetType.DISPATCH,
            ConfigurationAssetType.NOTIFICATION,
            ConfigurationAssetType.ASSIGNEE_POLICY,
            ConfigurationAssetType.INTEGRATION,
            ConfigurationAssetType.PRICING,
            ConfigurationAssetType.CALENDAR);

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final ConfigurationService configurations;
    private final ConfigurationAssetSchemaValidator schemaValidator;
    private final ConfigurationClientCapabilityGate clientCapabilityGate;
    private final AuditAppender audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    JooqConfigurationDraftService(
            DSLContext dsl,
            AuthorizationService authorization,
            ConfigurationService configurations,
            ConfigurationAssetSchemaValidator schemaValidator,
            ConfigurationClientCapabilityGate clientCapabilityGate,
            AuditAppender audit,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.dsl = dsl;
        this.authorization = authorization;
        this.configurations = configurations;
        this.schemaValidator = schemaValidator;
        this.clientCapabilityGate = clientCapabilityGate;
        this.audit = audit;
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
        List<String> supportedKinds = normalizeSupportedClientKinds(command.supportedClientKinds());
        UUID baseVersionId = command.baseVersionId() != null
                ? command.baseVersionId()
                : findLatestPublishedVersionId(principal.tenantId(), command.assetType(), assetKey);
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        // definition/supported_client_kinds 由 JsonbStringConverter 绑定 String -> JSONB，无需 CAST。
        dsl.insertInto(t)
                .set(t.DRAFT_ID, draftId)
                .set(t.TENANT_ID, principal.tenantId())
                .set(t.ASSET_TYPE, command.assetType().name())
                .set(t.ASSET_KEY, assetKey)
                .set(t.INTENDED_SEMANTIC_VERSION, semanticVersion)
                .set(t.SCHEMA_VERSION, schemaVersion)
                .set(t.DEFINITION, definition)
                .set(t.CONTENT_DIGEST, digest)
                .set(t.STATUS, "DRAFT")
                .set(t.BASE_VERSION_ID, baseVersionId)
                .set(t.AGGREGATE_VERSION, 1L)
                .set(t.CREATED_BY, principal.principalId())
                .set(t.UPDATED_BY, principal.principalId())
                .set(t.CREATED_AT, now)
                .set(t.UPDATED_AT, now)
                .set(t.SUPPORTED_CLIENT_KINDS, writeKindsJson(supportedKinds))
                .execute();
        return enrich(requireDraft(principal.tenantId(), draftId));
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
        // null = 保留原定向目标；非 null 则规范化后覆盖（空列表表示清除为全端默认）。
        boolean updateKinds = command.supportedClientKinds() != null;
        List<String> supportedKinds = updateKinds
                ? normalizeSupportedClientKinds(command.supportedClientKinds())
                : null;
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        // 编辑会使审批失效，强制回到 DRAFT。定向目标仅在调用方显式携带时覆盖（旧 CASE WHEN 语义）。
        UpdateSetMoreStep<CfgConfigurationAssetDraftRecord> update = dsl.update(t)
                .set(t.DEFINITION, definition)
                .set(t.CONTENT_DIGEST, digest)
                .set(t.STATUS, "DRAFT")
                .set(t.VALIDATION_ERRORS, (String) null)
                .set(t.APPROVAL_REF, (String) null)
                .set(t.APPROVED_BY, (String) null)
                .set(t.APPROVED_AT, (Instant) null)
                .set(t.AGGREGATE_VERSION, t.AGGREGATE_VERSION.plus(1))
                .set(t.UPDATED_BY, principal.principalId())
                .set(t.UPDATED_AT, now);
        if (updateKinds) {
            update.set(t.SUPPORTED_CLIENT_KINDS, writeKindsJson(supportedKinds));
        }
        UpdateConditionStep<CfgConfigurationAssetDraftRecord> conditioned = update
                .where(t.TENANT_ID.eq(principal.tenantId()))
                .and(t.DRAFT_ID.eq(command.draftId()))
                .and(t.AGGREGATE_VERSION.eq(command.expectedVersion()))
                .and(t.STATUS.in("DRAFT", "VALIDATED", "APPROVED"));
        int updated = conditioned.execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "配置草稿版本冲突或状态已变更");
        }
        return enrich(requireDraft(principal.tenantId(), command.draftId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationDraftView get(CurrentPrincipal principal, String correlationId, UUID draftId) {
        ConfigurationDraftView draft = requireDraft(principal.tenantId(), draftId);
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, draftId.toString()), correlationId);
        return enrich(draft);
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
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        return dsl.selectFrom(t)
                .where(t.TENANT_ID.eq(principal.tenantId()))
                .and(t.ASSET_TYPE.eq(assetType.name()))
                .and(t.STATUS.in("DRAFT", "VALIDATED", "APPROVED", "PUBLISHED"))
                .orderBy(t.UPDATED_AT.desc())
                .limit(100)
                .fetch(this::map)
                .stream()
                .map(this::enrich)
                .toList();
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

        List<String> errors = new java.util.ArrayList<>();
        String nextStatus = "VALIDATED";
        try {
            schemaValidator.validate(new PublishConfigurationAssetCommand(
                    principal.tenantId(), current.assetType(), current.assetKey(),
                    current.intendedSemanticVersion(), current.schemaVersion(),
                    current.definitionJson(), current.contentDigest()));
        } catch (ConfigurationPublicationException | IllegalArgumentException exception) {
            errors.add(exception.getMessage());
            nextStatus = "DRAFT";
        }
        ClientCompatibilityReport compatibility = clientCapabilityGate.evaluate(
                current.assetType(), current.definitionJson(), current.supportedClientKinds());
        if (compatibility.blocking()) {
            errors.addAll(compatibility.blockingErrors());
            nextStatus = "DRAFT";
        }

        Instant now = clock.instant();
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        dsl.update(t)
                .set(t.STATUS, nextStatus)
                .set(t.VALIDATION_ERRORS, writeJson(errors))
                .set(t.AGGREGATE_VERSION, t.AGGREGATE_VERSION.plus(1))
                .set(t.UPDATED_BY, principal.principalId())
                .set(t.UPDATED_AT, now)
                .where(t.TENANT_ID.eq(principal.tenantId()))
                .and(t.DRAFT_ID.eq(draftId))
                .and(t.STATUS.in("DRAFT", "VALIDATED"))
                .execute();
        ConfigurationDraftView result = enrich(requireDraft(principal.tenantId(), draftId));
        auditCapability("CONFIGURATION_DRAFT_CLIENT_COMPAT_VALIDATED",
                principal, metadata.correlationId(), result, compatibility,
                errors.isEmpty() ? "ALLOW" : "DENY");
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
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        int updated = dsl.update(t)
                .set(t.STATUS, "APPROVED")
                .set(t.APPROVAL_REF, approvalRef)
                .set(t.APPROVED_BY, principal.principalId())
                .set(t.APPROVED_AT, now)
                .set(t.AGGREGATE_VERSION, t.AGGREGATE_VERSION.plus(1))
                .set(t.UPDATED_BY, principal.principalId())
                .set(t.UPDATED_AT, now)
                .where(t.TENANT_ID.eq(principal.tenantId()))
                .and(t.DRAFT_ID.eq(command.draftId()))
                .and(t.AGGREGATE_VERSION.eq(command.expectedVersion()))
                .and(t.STATUS.eq("VALIDATED"))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "配置草稿审批时版本或状态冲突");
        }
        return enrich(requireDraft(principal.tenantId(), command.draftId()));
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

        // 发布前复检：防止 APPROVED 后定义被旁路篡改或能力目录收紧后误放行。
        ClientCompatibilityReport compatibility = clientCapabilityGate.evaluate(
                current.assetType(), current.definitionJson(), current.supportedClientKinds());
        if (compatibility.blocking()) {
            auditCapability("CONFIGURATION_DRAFT_CLIENT_COMPAT_PUBLISH",
                    principal, metadata.correlationId(), current, compatibility, "DENY");
            throw new ConfigurationPublicationException(
                    "配置发布被客户端能力门禁拒绝: "
                            + String.join("; ", compatibility.blockingErrors()));
        }

        // jsonb 回读可能改变空白/键序；发布摘要必须以当前 definition 文本重算。
        String publishDefinition = current.definitionJson().trim();
        String publishDigest = Sha256.digest(publishDefinition);
        ConfigurationAssetVersionReference published = configurations.publishAsset(
                new PublishConfigurationAssetCommand(
                        principal.tenantId(), current.assetType(), current.assetKey(),
                        current.intendedSemanticVersion(), current.schemaVersion(),
                        publishDefinition, publishDigest));
        persistPublishedClientTarget(
                principal.tenantId(), published.versionId(), current.supportedClientKinds());

        Instant now = clock.instant();
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        int updated = dsl.update(t)
                .set(t.STATUS, "PUBLISHED")
                .set(t.PUBLISHED_VERSION_ID, published.versionId())
                .set(t.AGGREGATE_VERSION, t.AGGREGATE_VERSION.plus(1))
                .set(t.UPDATED_BY, principal.principalId())
                .set(t.UPDATED_AT, now)
                .where(t.TENANT_ID.eq(principal.tenantId()))
                .and(t.DRAFT_ID.eq(draftId))
                .and(t.STATUS.eq("APPROVED"))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT, "配置草稿发布时状态已变更");
        }
        ConfigurationDraftView publishedView = enrich(requireDraft(principal.tenantId(), draftId));
        auditCapability("CONFIGURATION_DRAFT_CLIENT_COMPAT_PUBLISH",
                principal, metadata.correlationId(), publishedView, compatibility, "ALLOW");
        return publishedView;
    }

    private ConfigurationDraftView requireDraft(String tenantId, UUID draftId) {
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        return dsl.selectFrom(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.DRAFT_ID.eq(draftId))
                .fetchOptional(this::map)
                .orElseThrow(() -> new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND, "配置草稿不存在"));
    }

    private ConfigurationDraftView map(CfgConfigurationAssetDraftRecord record) {
        CfgConfigurationAssetDraft t = CFG_CONFIGURATION_ASSET_DRAFT;
        return new ConfigurationDraftView(
                record.get(t.DRAFT_ID),
                ConfigurationAssetType.valueOf(record.get(t.ASSET_TYPE)),
                record.get(t.ASSET_KEY),
                record.get(t.INTENDED_SEMANTIC_VERSION),
                record.get(t.SCHEMA_VERSION),
                record.get(t.DEFINITION),
                record.get(t.CONTENT_DIGEST),
                record.get(t.STATUS),
                record.get(t.BASE_VERSION_ID),
                record.get(t.PUBLISHED_VERSION_ID),
                readErrors(record.get(t.VALIDATION_ERRORS)),
                record.get(t.APPROVAL_REF),
                record.get(t.APPROVED_BY),
                record.get(t.APPROVED_AT),
                record.get(t.AGGREGATE_VERSION),
                record.get(t.CREATED_BY),
                record.get(t.UPDATED_BY),
                record.get(t.CREATED_AT),
                record.get(t.UPDATED_AT),
                readKinds(record.get(t.SUPPORTED_CLIENT_KINDS)),
                null);
    }

    private ConfigurationDraftView enrich(ConfigurationDraftView view) {
        ClientCompatibilityReport report = clientCapabilityGate.evaluate(
                view.assetType(), view.definitionJson(), view.supportedClientKinds());
        return view.withClientCompatibility(report);
    }

    private void persistPublishedClientTarget(
            String tenantId, UUID versionId, List<String> supportedClientKinds
    ) {
        List<String> kinds = normalizeSupportedClientKinds(supportedClientKinds);
        if (kinds == null) {
            return;
        }
        CfgConfigurationAssetClientTarget t = CFG_CONFIGURATION_ASSET_CLIENT_TARGET;
        dsl.insertInto(t)
                .set(t.VERSION_ID, versionId)
                .set(t.TENANT_ID, tenantId)
                .set(t.SUPPORTED_CLIENT_KINDS, writeKindsJson(kinds))
                .execute();
    }

    /**
     * null/空 → 未定向（全端默认）；非空 → 规范化去重后的目标集合。
     * 更新场景用空列表表示清除定向。
     */
    private static List<String> normalizeSupportedClientKinds(List<String> raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isEmpty()) {
            return null;
        }
        return ConfigurationClientCapabilityGate.resolveTargetKinds(raw);
    }

    private List<String> readKinds(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return null;
        }
        try {
            List<String> kinds = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            return kinds == null || kinds.isEmpty() ? null : List.copyOf(kinds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("supported_client_kinds 无法解码", exception);
        }
    }

    private String writeKindsJson(List<String> kinds) {
        if (kinds == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(kinds);
        } catch (JacksonException exception) {
            throw new IllegalStateException("supported_client_kinds 无法编码", exception);
        }
    }

    private void auditCapability(
            String action,
            CurrentPrincipal principal,
            String correlationId,
            ConfigurationDraftView draft,
            ClientCompatibilityReport report,
            String decision
    ) {
        String digest = Sha256.digest(
                draft.draftId() + "|"
                        + draft.assetType() + "|"
                        + String.join(",", report.requiredCapabilities()) + "|"
                        + String.join(",", report.blockingErrors()) + "|"
                        + decision);
        audit.append(new AuditEntry(
                UUID.randomUUID(),
                principal.tenantId(),
                principal.principalId(),
                action,
                PUBLISH,
                RESOURCE,
                draft.draftId().toString(),
                decision,
                List.of(),
                "client-capability-gate-v1",
                report.blocking() ? "BLOCKED" : "COMPAT_OK",
                report.blocking() ? "CLIENT_CAPABILITY_INCOMPATIBLE" : null,
                digest,
                correlationId,
                clock.instant()));
    }

    private UUID findLatestPublishedVersionId(
            String tenantId,
            ConfigurationAssetType assetType,
            String assetKey
    ) {
        CfgConfigurationAssetVersion v = CFG_CONFIGURATION_ASSET_VERSION;
        return dsl.select(v.VERSION_ID)
                .from(v)
                .where(v.TENANT_ID.eq(tenantId))
                .and(v.ASSET_TYPE.eq(assetType.name()))
                .and(v.ASSET_KEY.eq(assetKey))
                .and(v.STATUS.eq("PUBLISHED"))
                .orderBy(v.PUBLISHED_AT.desc())
                .limit(1)
                .fetchOptional(v.VERSION_ID)
                .orElse(null);
    }

    private String loadPublishedDefinition(String tenantId, UUID versionId) {
        CfgConfigurationAssetVersion v = CFG_CONFIGURATION_ASSET_VERSION;
        // definition 列经 JsonbStringConverter 直接映射为 String，无需 ::text。
        return dsl.select(v.DEFINITION)
                .from(v)
                .where(v.TENANT_ID.eq(tenantId))
                .and(v.VERSION_ID.eq(versionId))
                .fetchOptional(v.DEFINITION)
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
