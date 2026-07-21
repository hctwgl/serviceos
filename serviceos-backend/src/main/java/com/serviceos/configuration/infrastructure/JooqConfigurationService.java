package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetVersionReference;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.ConfigurationResolutionException;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.jooq.generated.tables.CfgBundleChannelActivation;
import com.serviceos.jooq.generated.tables.CfgConfigurationAssetClientTarget;
import com.serviceos.jooq.generated.tables.CfgConfigurationAssetVersion;
import com.serviceos.jooq.generated.tables.CfgConfigurationBundle;
import com.serviceos.jooq.generated.tables.CfgConfigurationBundleItem;
import com.serviceos.jooq.generated.tables.PrjProject;
import com.serviceos.shared.Sha256;
import org.jooq.impl.DSL;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.CfgBundleChannelActivation.CFG_BUNDLE_CHANNEL_ACTIVATION;
import static com.serviceos.jooq.generated.tables.CfgConfigurationAssetClientTarget.CFG_CONFIGURATION_ASSET_CLIENT_TARGET;
import static com.serviceos.jooq.generated.tables.CfgConfigurationAssetVersion.CFG_CONFIGURATION_ASSET_VERSION;
import static com.serviceos.jooq.generated.tables.CfgConfigurationBundle.CFG_CONFIGURATION_BUNDLE;
import static com.serviceos.jooq.generated.tables.CfgConfigurationBundleItem.CFG_CONFIGURATION_BUNDLE_ITEM;
import static com.serviceos.jooq.generated.tables.PrjProject.PRJ_PROJECT;

/**
 * PostgreSQL 配置发布与解析参考实现（jOOQ）。
 *
 * <p>发布操作用 scope advisory lock 串行化同一作用域，避免并发发布出重叠有效期。
 * 解析时区域精确配置优先于省级通配配置；同一优先级出现多条必须失败关闭。</p>
 */
@Service
final class JooqConfigurationService implements ConfigurationService {
    private final DSLContext dsl;
    private final Clock clock;
    private final ConfigurationAssetSchemaValidator schemaValidator;
    private final ObjectMapper definitionMapper = new ObjectMapper();

    JooqConfigurationService(
            DSLContext dsl,
            Clock clock,
            ConfigurationAssetSchemaValidator schemaValidator
    ) {
        this.dsl = dsl;
        this.clock = clock;
        this.schemaValidator = schemaValidator;
    }

    @Override
    @Transactional
    public ConfigurationAssetVersionReference publishAsset(PublishConfigurationAssetCommand command) {
        schemaValidator.validate(command);
        String actualDigest = Sha256.digest(command.definitionJson());
        if (!actualDigest.equals(command.contentDigest())) {
            throw new ConfigurationPublicationException("asset content digest does not match definition");
        }

        UUID versionId = UUID.randomUUID();
        Instant publishedAt = clock.instant();
        CfgConfigurationAssetVersion t = CFG_CONFIGURATION_ASSET_VERSION;
        // definition 列经 JsonbStringConverter 直接绑定 String，无需手写 CAST。
        int inserted = dsl.insertInto(t)
                .set(t.VERSION_ID, versionId)
                .set(t.TENANT_ID, command.tenantId())
                .set(t.ASSET_TYPE, command.assetType().name())
                .set(t.ASSET_KEY, command.assetKey())
                .set(t.SEMANTIC_VERSION, command.semanticVersion())
                .set(t.SCHEMA_VERSION, command.schemaVersion())
                .set(t.DEFINITION, command.definitionJson())
                .set(t.CONTENT_DIGEST, command.contentDigest())
                .set(t.STATUS, "PUBLISHED")
                .set(t.PUBLISHED_AT, publishedAt)
                .onConflict(t.TENANT_ID, t.ASSET_TYPE, t.ASSET_KEY, t.SEMANTIC_VERSION)
                .doNothing()
                .execute();

        AssetRow row = inserted == 1
                ? new AssetRow(versionId, command.assetType(), command.assetKey(),
                command.semanticVersion(), command.schemaVersion(), command.definitionJson(), command.contentDigest())
                : findAsset(command);
        if (!row.schemaVersion().equals(command.schemaVersion())
                || !row.contentDigest().equals(command.contentDigest())) {
            throw new ConfigurationPublicationException(
                    "asset version already exists with different immutable content");
        }
        return row.reference();
    }

    @Override
    @Transactional
    public ConfigurationBundleReference publishBundle(PublishConfigurationBundleCommand command) {
        ProjectRow project = requireActiveProject(command.tenantId(), command.projectId());
        List<AssetRow> assets = findAssets(command.tenantId(), command.assetVersionIds());
        if (assets.size() != command.assetVersionIds().size()) {
            throw new ConfigurationPublicationException(
                    "every bundle item must reference a published asset in the same tenant");
        }
        schemaValidator.validateBundle(assets.stream().map(AssetRow::definition).toList());
        String actualManifestDigest = bundleDigest(command, assets);

        String scopeKey = String.join("|", command.tenantId(), command.projectId().toString(),
                command.brandCode(), command.serviceProductCode(), nullToEmpty(command.provinceCode()));
        // advisory lock 随事务释放；锁键与解析作用域一一对应。
        dsl.select(DSL.field("pg_advisory_xact_lock(hashtextextended({0}, {1}))", Object.class,
                        DSL.val(scopeKey), DSL.val(0L)))
                .fetchSingle();

        ExistingBundle version = findBundleVersion(command.tenantId(), command.bundleCode(),
                command.bundleVersion());
        if (version != null) {
            if (!version.manifestDigest().equals(actualManifestDigest)) {
                throw new ConfigurationPublicationException(
                        "bundle version already exists with different immutable content");
            }
            return version.reference();
        }
        if (hasOverlappingBundle(command)) {
            throw new ConfigurationPublicationException(
                    "published bundle validity overlaps the same resolution scope");
        }

        UUID bundleId = UUID.randomUUID();
        Instant publishedAt = clock.instant();
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        dsl.insertInto(b)
                .set(b.BUNDLE_ID, bundleId)
                .set(b.TENANT_ID, command.tenantId())
                .set(b.PROJECT_ID, project.projectId())
                .set(b.BUNDLE_CODE, command.bundleCode())
                .set(b.BUNDLE_VERSION, command.bundleVersion())
                .set(b.BRAND_CODE, command.brandCode())
                .set(b.SERVICE_PRODUCT_CODE, command.serviceProductCode())
                .set(b.PROVINCE_CODE, command.provinceCode())
                .set(b.EFFECTIVE_FROM, command.effectiveFrom())
                .set(b.EFFECTIVE_UNTIL, command.effectiveUntil())
                .set(b.MANIFEST_DIGEST, actualManifestDigest)
                .set(b.STATUS, "PUBLISHED")
                .set(b.PUBLISHED_AT, publishedAt)
                .execute();

        CfgConfigurationBundleItem item = CFG_CONFIGURATION_BUNDLE_ITEM;
        for (AssetRow asset : assets) {
            dsl.insertInto(item)
                    .set(item.TENANT_ID, command.tenantId())
                    .set(item.BUNDLE_ID, bundleId)
                    .set(item.ASSET_TYPE, asset.assetType().name())
                    .set(item.ASSET_VERSION_ID, asset.versionId())
                    .set(item.CONTENT_DIGEST, asset.contentDigest())
                    .execute();
        }
        return new ConfigurationBundleReference(bundleId, project.projectId(), command.bundleCode(),
                command.bundleVersion(), actualManifestDigest);
    }

    @Override
    @Transactional(readOnly = true, noRollbackFor = ConfigurationResolutionException.class)
    public ConfigurationBundleReference resolve(ResolveConfigurationBundleQuery query) {
        ProjectRow project = findActiveProject(query.tenantId(), query.projectCode());
        if (project == null) {
            throw new ConfigurationResolutionException(
                    ConfigurationResolutionException.Reason.PROJECT_NOT_ACTIVE,
                    "project is missing, inactive, or outside its validity window");
        }

        // M286/M288/M290：多槽位 CANARY 累计分流；preferCanary 取 primary 或首个槽位。
        List<ChannelBundle> canaries = resolveActiveChannels(query, project.projectId(), "CANARY");
        ChannelBundle stable = resolveActiveChannels(query, project.projectId(), "STABLE").stream()
                .findFirst().orElse(null);
        if (query.preferCanary() && !canaries.isEmpty()) {
            ChannelBundle forced = canaries.stream()
                    .filter(item -> "primary".equals(item.slotCode()))
                    .findFirst()
                    .orElse(canaries.getFirst());
            return forced.reference();
        }
        ChannelBundle selectedCanary = selectCanaryByTraffic(query, canaries);
        if (selectedCanary != null) {
            return selectedCanary.reference();
        }
        if (stable != null) {
            return stable.reference();
        }
        // M293：项目一旦进入通道激活模型，停用后不得回退到模糊 Bundle 扫描。
        if (isChannelManagedProject(query.tenantId(), project.projectId())) {
            throw new ConfigurationResolutionException(
                    ConfigurationResolutionException.Reason.NO_MATCH,
                    "project uses bundle channel activations but no ACTIVE STABLE/CANARY matches");
        }

        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        // 区域精确（province 命中）优先于省级通配；region_rank 与旧 CASE WHEN 语义一致。
        Field<Integer> regionRank = DSL.case_()
                .when(b.PROVINCE_CODE.eq(query.provinceCode()), 1)
                .otherwise(0)
                .as("region_rank");
        List<ResolvedBundle> candidates = dsl.select(
                        b.BUNDLE_ID, b.PROJECT_ID, b.BUNDLE_CODE, b.BUNDLE_VERSION,
                        b.MANIFEST_DIGEST, regionRank)
                .from(b)
                .where(b.TENANT_ID.eq(query.tenantId()))
                .and(b.PROJECT_ID.eq(project.projectId()))
                .and(b.BRAND_CODE.eq(query.brandCode()))
                .and(b.SERVICE_PRODUCT_CODE.eq(query.serviceProductCode()))
                .and(b.PROVINCE_CODE.eq(query.provinceCode()).or(b.PROVINCE_CODE.isNull()))
                .and(b.STATUS.eq("PUBLISHED"))
                .and(b.EFFECTIVE_FROM.le(query.effectiveAt()))
                .and(b.EFFECTIVE_UNTIL.isNull().or(b.EFFECTIVE_UNTIL.gt(query.effectiveAt())))
                .orderBy(regionRank.desc(), b.BUNDLE_CODE, b.BUNDLE_VERSION)
                .fetch(record -> new ResolvedBundle(
                        record.value1(), record.value2(), record.value3(),
                        record.value4(), record.value5(), record.value6()));

        if (candidates.isEmpty()) {
            throw new ConfigurationResolutionException(
                    ConfigurationResolutionException.Reason.NO_MATCH,
                    "no published configuration bundle matches the work order");
        }
        int highestRank = candidates.getFirst().regionRank();
        List<ResolvedBundle> preferred = candidates.stream()
                .filter(candidate -> candidate.regionRank() == highestRank)
                .toList();
        if (preferred.size() != 1) {
            throw new ConfigurationResolutionException(
                    ConfigurationResolutionException.Reason.AMBIGUOUS_MATCH,
                    "multiple published configuration bundles match the same priority");
        }
        return preferred.getFirst().reference();
    }

    private boolean isChannelManagedProject(String tenantId, UUID projectId) {
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        return dsl.fetchExists(a,
                a.TENANT_ID.eq(tenantId),
                a.PROJECT_ID.eq(projectId));
    }

    private List<ChannelBundle> resolveActiveChannels(
            ResolveConfigurationBundleQuery query,
            UUID projectId,
            String channel
    ) {
        // 通道激活是项目级发布指针：命中后不再做 province/时间窗扫描，避免与兼容扫描的互斥生效窗冲突。
        CfgBundleChannelActivation a = CFG_BUNDLE_CHANNEL_ACTIVATION;
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        return dsl.select(b.BUNDLE_ID, b.PROJECT_ID, b.BUNDLE_CODE, b.BUNDLE_VERSION,
                        b.MANIFEST_DIGEST, a.TRAFFIC_PERCENT, a.SLOT_CODE)
                .from(a)
                .join(b)
                .on(b.TENANT_ID.eq(a.TENANT_ID))
                .and(b.BUNDLE_ID.eq(a.BUNDLE_ID))
                .where(a.TENANT_ID.eq(query.tenantId()))
                .and(a.PROJECT_ID.eq(projectId))
                .and(a.CHANNEL.eq(channel))
                .and(a.STATUS.eq("ACTIVE"))
                .and(b.STATUS.eq("PUBLISHED"))
                .and(b.BRAND_CODE.eq(query.brandCode()))
                .and(b.SERVICE_PRODUCT_CODE.eq(query.serviceProductCode()))
                .orderBy(a.SLOT_CODE)
                .fetch(record -> new ChannelBundle(
                        new ConfigurationBundleReference(
                                record.value1(), record.value2(), record.value3(),
                                record.value4(), record.value5()),
                        record.value6(), record.value7()));
    }

    private static ChannelBundle selectCanaryByTraffic(
            ResolveConfigurationBundleQuery query,
            List<ChannelBundle> canaries
    ) {
        if (canaries.isEmpty()) {
            return null;
        }
        int total = canaries.stream().mapToInt(ChannelBundle::trafficPercent).sum();
        if (total <= 0) {
            return null;
        }
        String key = query.canaryRoutingKey() != null
                ? query.canaryRoutingKey()
                : String.join("|", query.tenantId(), query.projectCode(), query.brandCode(),
                query.serviceProductCode(), query.provinceCode());
        int bucket = Math.floorMod(Sha256.digest(key).hashCode(), 100);
        int cursor = 0;
        for (ChannelBundle canary : canaries) {
            cursor += canary.trafficPercent();
            if (bucket < cursor) {
                return canary;
            }
        }
        return null;
    }

    private record ChannelBundle(
            ConfigurationBundleReference reference,
            int trafficPercent,
            String slotCode
    ) {
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationAssetDefinition requireBundleAsset(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            ConfigurationAssetType assetType
    ) {
        List<ConfigurationAssetDefinition> assets = listBundleAssets(
                tenantId, bundleId, expectedManifestDigest, assetType);
        if (assets.isEmpty()) {
            throw new ConfigurationResolutionException(
                    ConfigurationResolutionException.Reason.NO_MATCH,
                    "published configuration bundle must contain the required asset type");
        }
        if (assetType == ConfigurationAssetType.WORKFLOW && assets.size() > 1) {
            // M277：Bundle 可含多个 WORKFLOW；根流程是未被任何 SUB_PROCESS 引用的那一个。
            Set<String> referenced = new HashSet<>();
            for (ConfigurationAssetDefinition asset : assets) {
                try {
                    JsonNode root = definitionMapper.readTree(asset.definitionJson());
                    for (JsonNode node : root.path("nodes")) {
                        if ("SUB_PROCESS".equals(node.path("nodeType").asText())
                                && node.hasNonNull("subProcessRef")) {
                            referenced.add(node.path("subProcessRef").asText());
                        }
                    }
                } catch (Exception exception) {
                    throw new ConfigurationResolutionException(
                            ConfigurationResolutionException.Reason.NO_MATCH,
                            "cannot inspect workflow definition for subprocess roots");
                }
            }
            List<ConfigurationAssetDefinition> roots = assets.stream()
                    .filter(asset -> !referenced.contains(asset.assetKey()))
                    .toList();
            if (roots.size() != 1) {
                throw new ConfigurationResolutionException(
                        ConfigurationResolutionException.Reason.AMBIGUOUS_MATCH,
                        "published configuration bundle must contain exactly one root WORKFLOW");
            }
            return roots.getFirst();
        }
        if (assets.size() != 1) {
            throw new ConfigurationResolutionException(
                    ConfigurationResolutionException.Reason.AMBIGUOUS_MATCH,
                    "published configuration bundle must contain exactly one required asset type");
        }
        return assets.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigurationAssetDefinition> listBundleAssets(
            String tenantId,
            UUID bundleId,
            String expectedManifestDigest,
            ConfigurationAssetType assetType
    ) {
        String requiredTenant = requiredText(tenantId, "tenantId", 64);
        UUID requiredBundle = java.util.Objects.requireNonNull(bundleId, "bundleId");
        String requiredManifestDigest = requiredText(expectedManifestDigest, "expectedManifestDigest", 64);
        ConfigurationAssetType requiredType = java.util.Objects.requireNonNull(assetType, "assetType");
        CfgConfigurationBundle bundle = CFG_CONFIGURATION_BUNDLE;
        CfgConfigurationBundleItem item = CFG_CONFIGURATION_BUNDLE_ITEM;
        CfgConfigurationAssetVersion asset = CFG_CONFIGURATION_ASSET_VERSION;
        CfgConfigurationAssetClientTarget target = CFG_CONFIGURATION_ASSET_CLIENT_TARGET;
        // definition/supported_client_kinds 经 JsonbStringConverter 直接映射为 String，无需 ::text。
        return dsl.select(asset.VERSION_ID, asset.ASSET_TYPE, asset.ASSET_KEY,
                        asset.SEMANTIC_VERSION, asset.SCHEMA_VERSION, asset.DEFINITION,
                        asset.CONTENT_DIGEST, target.SUPPORTED_CLIENT_KINDS)
                .from(bundle)
                .join(item)
                .on(item.TENANT_ID.eq(bundle.TENANT_ID))
                .and(item.BUNDLE_ID.eq(bundle.BUNDLE_ID))
                .join(asset)
                .on(asset.TENANT_ID.eq(item.TENANT_ID))
                .and(asset.VERSION_ID.eq(item.ASSET_VERSION_ID))
                .and(asset.ASSET_TYPE.eq(item.ASSET_TYPE))
                .and(asset.CONTENT_DIGEST.eq(item.CONTENT_DIGEST))
                .leftJoin(target)
                .on(target.VERSION_ID.eq(asset.VERSION_ID))
                .and(target.TENANT_ID.eq(asset.TENANT_ID))
                .where(bundle.TENANT_ID.eq(requiredTenant))
                .and(bundle.BUNDLE_ID.eq(requiredBundle))
                .and(bundle.MANIFEST_DIGEST.eq(requiredManifestDigest))
                .and(bundle.STATUS.eq("PUBLISHED"))
                .and(item.ASSET_TYPE.eq(requiredType.name()))
                .and(asset.STATUS.eq("PUBLISHED"))
                .orderBy(asset.ASSET_KEY, asset.SEMANTIC_VERSION, asset.VERSION_ID)
                .fetch(record -> new ConfigurationAssetDefinition(
                        record.value1(),
                        ConfigurationAssetType.valueOf(record.value2()),
                        record.value3(), record.value4(), record.value5(), record.value6(),
                        record.value7(),
                        readSupportedClientKinds(record.value8())));
    }

    @Override
    @Transactional(readOnly = true)
    public ConfigurationAssetDefinition requireAssetVersion(
            String tenantId,
            UUID versionId,
            ConfigurationAssetType assetType,
            String expectedContentDigest
    ) {
        String requiredTenant = requiredText(tenantId, "tenantId", 64);
        UUID requiredVersion = java.util.Objects.requireNonNull(versionId, "versionId");
        ConfigurationAssetType requiredType = java.util.Objects.requireNonNull(assetType, "assetType");
        String requiredDigest = requiredText(expectedContentDigest, "expectedContentDigest", 64);
        CfgConfigurationAssetVersion asset = CFG_CONFIGURATION_ASSET_VERSION;
        CfgConfigurationAssetClientTarget target = CFG_CONFIGURATION_ASSET_CLIENT_TARGET;
        return dsl.select(asset.VERSION_ID, asset.ASSET_TYPE, asset.ASSET_KEY,
                        asset.SEMANTIC_VERSION, asset.SCHEMA_VERSION, asset.DEFINITION,
                        asset.CONTENT_DIGEST, target.SUPPORTED_CLIENT_KINDS)
                .from(asset)
                .leftJoin(target)
                .on(target.VERSION_ID.eq(asset.VERSION_ID))
                .and(target.TENANT_ID.eq(asset.TENANT_ID))
                .where(asset.TENANT_ID.eq(requiredTenant))
                .and(asset.VERSION_ID.eq(requiredVersion))
                .and(asset.ASSET_TYPE.eq(requiredType.name()))
                .and(asset.CONTENT_DIGEST.eq(requiredDigest))
                .and(asset.STATUS.eq("PUBLISHED"))
                .fetchOptional(record -> new ConfigurationAssetDefinition(
                        record.value1(),
                        ConfigurationAssetType.valueOf(record.value2()),
                        record.value3(), record.value4(), record.value5(), record.value6(),
                        record.value7(),
                        readSupportedClientKinds(record.value8())))
                .orElseThrow(() -> new ConfigurationResolutionException(
                        ConfigurationResolutionException.Reason.NO_MATCH,
                        "published configuration asset version does not match the frozen identity"));
    }

    private List<String> readSupportedClientKinds(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) {
            return List.of();
        }
        try {
            JsonNode node = definitionMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            java.util.ArrayList<String> kinds = new java.util.ArrayList<>();
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) {
                    kinds.add(item.asText());
                }
            });
            return List.copyOf(kinds);
        } catch (Exception exception) {
            throw new IllegalStateException("supported_client_kinds 无法解码", exception);
        }
    }

    private AssetRow findAsset(PublishConfigurationAssetCommand command) {
        CfgConfigurationAssetVersion t = CFG_CONFIGURATION_ASSET_VERSION;
        return dsl.select(t.VERSION_ID, t.ASSET_TYPE, t.ASSET_KEY, t.SEMANTIC_VERSION,
                        t.SCHEMA_VERSION, t.DEFINITION, t.CONTENT_DIGEST)
                .from(t)
                .where(t.TENANT_ID.eq(command.tenantId()))
                .and(t.ASSET_TYPE.eq(command.assetType().name()))
                .and(t.ASSET_KEY.eq(command.assetKey()))
                .and(t.SEMANTIC_VERSION.eq(command.semanticVersion()))
                .fetchSingle(JooqConfigurationService::assetRow);
    }

    private List<AssetRow> findAssets(String tenantId, List<UUID> versionIds) {
        CfgConfigurationAssetVersion t = CFG_CONFIGURATION_ASSET_VERSION;
        return dsl.select(t.VERSION_ID, t.ASSET_TYPE, t.ASSET_KEY, t.SEMANTIC_VERSION,
                        t.SCHEMA_VERSION, t.DEFINITION, t.CONTENT_DIGEST)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.STATUS.eq("PUBLISHED"))
                .and(t.VERSION_ID.in(versionIds))
                .fetch(JooqConfigurationService::assetRow);
    }

    private static AssetRow assetRow(
            Record7<UUID, String, String, String, String, String, String> record
    ) {
        return new AssetRow(
                record.value1(),
                ConfigurationAssetType.valueOf(record.value2()),
                record.value3(), record.value4(), record.value5(), record.value6(), record.value7());
    }

    private ProjectRow requireActiveProject(String tenantId, UUID projectId) {
        ProjectRow project = findActiveProjectRow(tenantId, projectId, null);
        if (project == null) {
            throw new ConfigurationPublicationException("configuration bundle requires an active project");
        }
        return project;
    }

    private ProjectRow findActiveProject(String tenantId, String projectCode) {
        return findActiveProjectRow(tenantId, null, projectCode);
    }

    private ProjectRow findActiveProjectRow(String tenantId, UUID projectId, String projectCode) {
        PrjProject p = PRJ_PROJECT;
        org.jooq.Condition keyCondition = projectId != null
                ? p.PROJECT_ID.eq(projectId)
                : p.PROJECT_CODE.eq(projectCode);
        return dsl.select(p.PROJECT_ID, p.PROJECT_CODE)
                .from(p)
                .where(p.TENANT_ID.eq(tenantId))
                .and(keyCondition)
                .and(p.PROJECT_STATUS.eq("ACTIVE"))
                .and(p.STARTS_ON.le(DSL.currentLocalDate()))
                .and(p.ENDS_ON.isNull().or(p.ENDS_ON.ge(DSL.currentLocalDate())))
                .fetchOptional(record -> new ProjectRow(record.value1(), record.value2()))
                .orElse(null);
    }

    private ExistingBundle findBundleVersion(String tenantId, String bundleCode, String bundleVersion) {
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        return dsl.select(b.BUNDLE_ID, b.PROJECT_ID, b.BUNDLE_CODE, b.BUNDLE_VERSION, b.MANIFEST_DIGEST)
                .from(b)
                .where(b.TENANT_ID.eq(tenantId))
                .and(b.BUNDLE_CODE.eq(bundleCode))
                .and(b.BUNDLE_VERSION.eq(bundleVersion))
                .fetchOptional(record -> new ExistingBundle(
                        record.value1(), record.value2(), record.value3(),
                        record.value4(), record.value5()))
                .orElse(null);
    }

    private boolean hasOverlappingBundle(PublishConfigurationBundleCommand command) {
        CfgConfigurationBundle b = CFG_CONFIGURATION_BUNDLE;
        // 生效窗开区间重叠判定；effective_until 为空按 'infinity' 处理，与旧 SQL COALESCE 语义一致。
        Field<Instant> infinity = DSL.field("'infinity'::timestamptz", b.EFFECTIVE_UNTIL.getDataType());
        return dsl.fetchExists(dsl.selectOne()
                .from(b)
                .where(b.TENANT_ID.eq(command.tenantId()))
                .and(b.PROJECT_ID.eq(command.projectId()))
                .and(b.BRAND_CODE.eq(command.brandCode()))
                .and(b.SERVICE_PRODUCT_CODE.eq(command.serviceProductCode()))
                .and(DSL.coalesce(b.PROVINCE_CODE, "")
                        .eq(DSL.coalesce(DSL.val(command.provinceCode(), b.PROVINCE_CODE), "")))
                .and(b.STATUS.eq("PUBLISHED"))
                .and(b.EFFECTIVE_FROM.lt(DSL.coalesce(
                        DSL.val(command.effectiveUntil(), b.EFFECTIVE_UNTIL), infinity)))
                .and(DSL.val(command.effectiveFrom(), b.EFFECTIVE_FROM)
                        .lt(DSL.coalesce(b.EFFECTIVE_UNTIL, infinity))));
    }

    private static String bundleDigest(PublishConfigurationBundleCommand command, List<AssetRow> assets) {
        String itemDigest = assets.stream()
                .sorted(Comparator.comparing((AssetRow asset) -> asset.assetType().name())
                        .thenComparing(AssetRow::assetKey)
                        .thenComparing(AssetRow::semanticVersion)
                        .thenComparing(AssetRow::versionId))
                .map(asset -> asset.assetType().name() + ":" + asset.versionId() + ":" + asset.contentDigest())
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();
        return Sha256.digest(String.join("|",
                command.tenantId(), command.projectId().toString(), command.bundleCode(),
                command.bundleVersion(), command.brandCode(), command.serviceProductCode(),
                nullToEmpty(command.provinceCode()), command.effectiveFrom().toString(),
                command.effectiveUntil() == null ? "" : command.effectiveUntil().toString(), itemDigest));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds max length " + maxLength);
        }
        return normalized;
    }

    private record AssetRow(
            UUID versionId,
            ConfigurationAssetType assetType,
            String assetKey,
            String semanticVersion,
            String schemaVersion,
            String definitionJson,
            String contentDigest
    ) {
        ConfigurationAssetVersionReference reference() {
            return new ConfigurationAssetVersionReference(
                    versionId, assetType, assetKey, semanticVersion, contentDigest);
        }

        ConfigurationAssetDefinition definition() {
            return new ConfigurationAssetDefinition(
                    versionId, assetType, assetKey, semanticVersion,
                    schemaVersion, definitionJson, contentDigest);
        }
    }

    private record ProjectRow(UUID projectId, String projectCode) {
    }

    private record ExistingBundle(
            UUID bundleId,
            UUID projectId,
            String bundleCode,
            String bundleVersion,
            String manifestDigest
    ) {
        ConfigurationBundleReference reference() {
            return new ConfigurationBundleReference(
                    bundleId, projectId, bundleCode, bundleVersion, manifestDigest);
        }
    }

    private record ResolvedBundle(
            UUID bundleId,
            UUID projectId,
            String bundleCode,
            String bundleVersion,
            String manifestDigest,
            int regionRank
    ) {
        ConfigurationBundleReference reference() {
            return new ConfigurationBundleReference(
                    bundleId, projectId, bundleCode, bundleVersion, manifestDigest);
        }
    }
}
