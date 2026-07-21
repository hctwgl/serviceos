package com.serviceos.configuration.application;

import com.serviceos.authorization.api.AuthorizationRequest;
import com.serviceos.authorization.api.AuthorizationService;
import com.serviceos.configuration.api.AnalyzeConfigurationDependenciesCommand;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationDependencyAnalysisService;
import com.serviceos.configuration.api.ConfigurationDependencyItem;
import com.serviceos.configuration.api.ConfigurationDependencyReport;
import com.serviceos.configuration.api.ConfigurationDependencyStatus;
import com.serviceos.configuration.api.ConfigurationDraftService;
import com.serviceos.configuration.api.ConfigurationDraftView;
import com.serviceos.identity.api.CurrentPrincipal;
import com.serviceos.jooq.generated.tables.CfgConfigurationAssetDraft;
import com.serviceos.jooq.generated.tables.CfgConfigurationAssetVersion;
import com.serviceos.jooq.generated.tables.CfgConfigurationBundleItem;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.CfgConfigurationAssetDraft.CFG_CONFIGURATION_ASSET_DRAFT;
import static com.serviceos.jooq.generated.tables.CfgConfigurationAssetVersion.CFG_CONFIGURATION_ASSET_VERSION;
import static com.serviceos.jooq.generated.tables.CfgConfigurationBundleItem.CFG_CONFIGURATION_BUNDLE_ITEM;

/**
 * WORKFLOW 依赖扫描（jOOQ）。
 *
 * <p>只做确定性引用提取与存在性核对；不执行表达式、不猜测默认依赖。</p>
 */
@Service
final class JooqConfigurationDependencyAnalysisService implements ConfigurationDependencyAnalysisService {
    private static final String WRITE = "configuration.draft.write";
    private static final String RESOURCE = "ConfigurationDependency";

    private static final Map<String, ConfigurationAssetType> REF_FIELDS = Map.ofEntries(
            Map.entry("formRef", ConfigurationAssetType.FORM),
            Map.entry("slaRef", ConfigurationAssetType.SLA),
            Map.entry("evidenceRef", ConfigurationAssetType.EVIDENCE),
            Map.entry("subProcessRef", ConfigurationAssetType.WORKFLOW),
            Map.entry("integrationRef", ConfigurationAssetType.INTEGRATION),
            Map.entry("assigneePolicyRef", ConfigurationAssetType.ASSIGNEE_POLICY),
            Map.entry("dispatchPolicyRef", ConfigurationAssetType.DISPATCH),
            Map.entry("ruleRef", ConfigurationAssetType.RULE));

    private final DSLContext dsl;
    private final AuthorizationService authorization;
    private final ConfigurationDraftService drafts;
    private final ObjectMapper objectMapper;

    JooqConfigurationDependencyAnalysisService(
            DSLContext dsl,
            AuthorizationService authorization,
            ConfigurationDraftService drafts,
            ObjectMapper objectMapper
    ) {
        this.dsl = dsl;
        this.authorization = authorization;
        this.drafts = drafts;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationDependencyReport analyzeDraft(
            CurrentPrincipal principal,
            String correlationId,
            UUID draftId
    ) {
        ConfigurationDraftView draft = drafts.get(principal, correlationId, draftId);
        ConfigurationDependencyReport report = analyze(principal, correlationId,
                new AnalyzeConfigurationDependenciesCommand(
                        draft.assetType(), draft.assetKey(), draft.definitionJson(), null));
        return new ConfigurationDependencyReport(
                report.assetType(), report.assetKey(), draftId, report.bundleId(),
                report.complete(), report.dependencies());
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationDependencyReport analyze(
            CurrentPrincipal principal,
            String correlationId,
            AnalyzeConfigurationDependenciesCommand command
    ) {
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.assetType(), "assetType");
        authorization.require(principal, AuthorizationRequest.tenantCapability(
                WRITE, principal.tenantId(), RESOURCE, command.assetType().name()), correlationId);
        if (command.assetType() != ConfigurationAssetType.WORKFLOW) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "M291 依赖分析目前仅支持 WORKFLOW 定义");
        }
        String definition = requireDefinition(command.definitionJson());
        String assetKey = command.assetKey() == null || command.assetKey().isBlank()
                ? "anonymous-workflow" : command.assetKey().trim();
        JsonNode root = parse(definition);
        List<ConfigurationDependencyItem> items = new ArrayList<>();
        for (JsonNode node : root.path("nodes")) {
            String nodeId = textOrNull(node, "nodeId");
            for (Map.Entry<String, ConfigurationAssetType> ref : REF_FIELDS.entrySet()) {
                if (!node.hasNonNull(ref.getKey())) {
                    continue;
                }
                String refValue = node.path(ref.getKey()).asText().trim();
                if (refValue.isEmpty()) {
                    continue;
                }
                items.add(resolveDependency(
                        principal.tenantId(), command.bundleId(),
                        ref.getKey(), refValue, nodeId, ref.getValue()));
            }
        }
        boolean complete = items.stream().allMatch(item ->
                item.status() == ConfigurationDependencyStatus.SATISFIED);
        return new ConfigurationDependencyReport(
                ConfigurationAssetType.WORKFLOW, assetKey, null, command.bundleId(),
                complete, List.copyOf(items));
    }

    private ConfigurationDependencyItem resolveDependency(
            String tenantId,
            UUID bundleId,
            String refField,
            String refValue,
            String sourceNodeId,
            ConfigurationAssetType expectedType
    ) {
        if (bundleId != null) {
            UUID versionId = findInBundle(tenantId, bundleId, expectedType, refValue);
            if (versionId != null) {
                return new ConfigurationDependencyItem(
                        refField, refValue, sourceNodeId, expectedType,
                        ConfigurationDependencyStatus.SATISFIED, versionId,
                        "命中 Bundle 内已发布资产");
            }
            return new ConfigurationDependencyItem(
                    refField, refValue, sourceNodeId, expectedType,
                    ConfigurationDependencyStatus.MISSING, null,
                    "Bundle 内未命中 " + expectedType.name() + " assetKey=" + refValue);
        }
        UUID versionId = findPublished(tenantId, expectedType, refValue);
        if (versionId != null) {
            return new ConfigurationDependencyItem(
                    refField, refValue, sourceNodeId, expectedType,
                    ConfigurationDependencyStatus.SATISFIED, versionId,
                    "命中租户已发布资产");
        }
        boolean draftExists = existsOpenDraft(tenantId, expectedType, refValue);
        if (draftExists) {
            return new ConfigurationDependencyItem(
                    refField, refValue, sourceNodeId, expectedType,
                    ConfigurationDependencyStatus.MISSING, null,
                    "仅有开放草稿、尚无已发布版本");
        }
        return new ConfigurationDependencyItem(
                refField, refValue, sourceNodeId, expectedType,
                ConfigurationDependencyStatus.MISSING, null,
                "未找到已发布资产或开放草稿");
    }

    private UUID findPublished(String tenantId, ConfigurationAssetType type, String assetKey) {
        CfgConfigurationAssetVersion v = CFG_CONFIGURATION_ASSET_VERSION;
        return dsl.select(v.VERSION_ID)
                .from(v)
                .where(v.TENANT_ID.eq(tenantId))
                .and(v.ASSET_TYPE.eq(type.name()))
                .and(v.ASSET_KEY.eq(assetKey))
                .and(v.STATUS.eq("PUBLISHED"))
                .orderBy(v.PUBLISHED_AT.desc())
                .limit(1)
                .fetchOptional(v.VERSION_ID)
                .orElse(null);
    }

    private UUID findInBundle(
            String tenantId,
            UUID bundleId,
            ConfigurationAssetType type,
            String assetKey
    ) {
        CfgConfigurationBundleItem i = CFG_CONFIGURATION_BUNDLE_ITEM;
        CfgConfigurationAssetVersion v = CFG_CONFIGURATION_ASSET_VERSION;
        return dsl.select(v.VERSION_ID)
                .from(i)
                .join(v)
                .on(v.TENANT_ID.eq(i.TENANT_ID))
                .and(v.VERSION_ID.eq(i.ASSET_VERSION_ID))
                .where(i.TENANT_ID.eq(tenantId))
                .and(i.BUNDLE_ID.eq(bundleId))
                .and(i.ASSET_TYPE.eq(type.name()))
                .and(v.ASSET_KEY.eq(assetKey))
                .and(v.STATUS.eq("PUBLISHED"))
                .limit(1)
                .fetchOptional(v.VERSION_ID)
                .orElse(null);
    }

    private boolean existsOpenDraft(String tenantId, ConfigurationAssetType type, String assetKey) {
        CfgConfigurationAssetDraft d = CFG_CONFIGURATION_ASSET_DRAFT;
        return dsl.fetchExists(d,
                d.TENANT_ID.eq(tenantId),
                d.ASSET_TYPE.eq(type.name()),
                d.ASSET_KEY.eq(assetKey),
                d.STATUS.in("DRAFT", "VALIDATED", "APPROVED"));
    }

    private JsonNode parse(String definitionJson) {
        try {
            return objectMapper.readTree(definitionJson);
        } catch (JacksonException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "WORKFLOW 定义不是合法 JSON");
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String requireDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            throw new IllegalArgumentException("definitionJson must not be blank");
        }
        return definitionJson.trim();
    }
}
