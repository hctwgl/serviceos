package com.serviceos.configuration.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationAssetVersionReference;
import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.configuration.api.ConfigurationPublicationException;
import com.serviceos.configuration.api.ConfigurationResolutionException;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.PublishConfigurationAssetCommand;
import com.serviceos.configuration.api.PublishConfigurationBundleCommand;
import com.serviceos.configuration.api.ResolveConfigurationBundleQuery;
import com.serviceos.shared.Sha256;
import com.serviceos.shared.infrastructure.PostgresJdbcParameters;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * PostgreSQL 配置发布与解析参考实现。
 *
 * <p>发布操作用 scope advisory lock 串行化同一作用域，避免并发发布出重叠有效期。
 * 解析时区域精确配置优先于省级通配配置；同一优先级出现多条必须失败关闭。</p>
 */
@Service
final class JdbcConfigurationService implements ConfigurationService {
    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcConfigurationService(JdbcClient jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    JdbcConfigurationService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ConfigurationAssetVersionReference publishAsset(PublishConfigurationAssetCommand command) {
        String actualDigest = Sha256.digest(command.definitionJson());
        if (!actualDigest.equals(command.contentDigest())) {
            throw new ConfigurationPublicationException("asset content digest does not match definition");
        }

        UUID versionId = UUID.randomUUID();
        Instant publishedAt = clock.instant();
        int inserted = jdbc.sql("""
                INSERT INTO cfg_configuration_asset_version (
                    version_id, tenant_id, asset_type, asset_key, semantic_version,
                    schema_version, definition, content_digest, status, published_at
                ) VALUES (
                    :versionId, :tenantId, :assetType, :assetKey, :semanticVersion,
                    :schemaVersion, CAST(:definition AS jsonb), :contentDigest, 'PUBLISHED', :publishedAt
                ) ON CONFLICT (tenant_id, asset_type, asset_key, semantic_version) DO NOTHING
                """)
                .param("versionId", versionId)
                .param("tenantId", command.tenantId())
                .param("assetType", command.assetType().name())
                .param("assetKey", command.assetKey())
                .param("semanticVersion", command.semanticVersion())
                .param("schemaVersion", command.schemaVersion())
                .param("definition", command.definitionJson())
                .param("contentDigest", command.contentDigest())
                .param("publishedAt", PostgresJdbcParameters.timestamptz(publishedAt))
                .update();

        AssetRow row = inserted == 1
                ? new AssetRow(versionId, command.assetType(), command.assetKey(),
                command.semanticVersion(), command.schemaVersion(), command.contentDigest())
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
        long distinctTypes = assets.stream().map(AssetRow::assetType).distinct().count();
        if (distinctTypes != assets.size()) {
            throw new ConfigurationPublicationException("a bundle may contain only one version per asset type");
        }
        String actualManifestDigest = bundleDigest(command, assets);

        String scopeKey = String.join("|", command.tenantId(), command.projectId().toString(),
                command.brandCode(), command.serviceProductCode(), nullToEmpty(command.provinceCode()));
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scopeKey, 0))")
                .param("scopeKey", scopeKey)
                .query((rs, rowNum) -> 0)
                .single();

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
        jdbc.sql("""
                INSERT INTO cfg_configuration_bundle (
                    bundle_id, tenant_id, project_id, bundle_code, bundle_version,
                    brand_code, service_product_code, province_code, effective_from,
                    effective_until, manifest_digest, status, published_at
                ) VALUES (
                    :bundleId, :tenantId, :projectId, :bundleCode, :bundleVersion,
                    :brandCode, :serviceProductCode, :provinceCode, :effectiveFrom,
                    :effectiveUntil, :manifestDigest, 'PUBLISHED', :publishedAt
                )
                """)
                .param("bundleId", bundleId)
                .param("tenantId", command.tenantId())
                .param("projectId", project.projectId())
                .param("bundleCode", command.bundleCode())
                .param("bundleVersion", command.bundleVersion())
                .param("brandCode", command.brandCode())
                .param("serviceProductCode", command.serviceProductCode())
                .param("provinceCode", command.provinceCode(), java.sql.Types.VARCHAR)
                .param("effectiveFrom", PostgresJdbcParameters.timestamptz(command.effectiveFrom()))
                .param("effectiveUntil", PostgresJdbcParameters.timestamptz(command.effectiveUntil()))
                .param("manifestDigest", actualManifestDigest)
                .param("publishedAt", PostgresJdbcParameters.timestamptz(publishedAt))
                .update();

        for (AssetRow asset : assets) {
            jdbc.sql("""
                    INSERT INTO cfg_configuration_bundle_item (
                        tenant_id, bundle_id, asset_type, asset_version_id, content_digest
                    ) VALUES (:tenantId, :bundleId, :assetType, :assetVersionId, :contentDigest)
                    """)
                    .param("tenantId", command.tenantId())
                    .param("bundleId", bundleId)
                    .param("assetType", asset.assetType().name())
                    .param("assetVersionId", asset.versionId())
                    .param("contentDigest", asset.contentDigest())
                    .update();
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

        List<ResolvedBundle> candidates = jdbc.sql("""
                SELECT bundle_id, project_id, bundle_code, bundle_version, manifest_digest,
                       CASE WHEN province_code = :provinceCode THEN 1 ELSE 0 END AS region_rank
                  FROM cfg_configuration_bundle
                 WHERE tenant_id = :tenantId
                   AND project_id = :projectId
                   AND brand_code = :brandCode
                   AND service_product_code = :serviceProductCode
                   AND (province_code = :provinceCode OR province_code IS NULL)
                   AND status = 'PUBLISHED'
                   AND effective_from <= :effectiveAt
                   AND (effective_until IS NULL OR effective_until > :effectiveAt)
                 ORDER BY region_rank DESC, bundle_code, bundle_version
                """)
                .param("provinceCode", query.provinceCode())
                .param("tenantId", query.tenantId())
                .param("projectId", project.projectId())
                .param("brandCode", query.brandCode())
                .param("serviceProductCode", query.serviceProductCode())
                .param("effectiveAt", PostgresJdbcParameters.timestamptz(query.effectiveAt()))
                .query((rs, rowNum) -> new ResolvedBundle(
                        rs.getObject("bundle_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("bundle_code"),
                        rs.getString("bundle_version"),
                        rs.getString("manifest_digest"),
                        rs.getInt("region_rank")))
                .list();

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

    private AssetRow findAsset(PublishConfigurationAssetCommand command) {
        return jdbc.sql("""
                SELECT version_id, asset_type, asset_key, semantic_version,
                       schema_version, content_digest
                  FROM cfg_configuration_asset_version
                 WHERE tenant_id = :tenantId AND asset_type = :assetType
                   AND asset_key = :assetKey AND semantic_version = :semanticVersion
                """)
                .param("tenantId", command.tenantId())
                .param("assetType", command.assetType().name())
                .param("assetKey", command.assetKey())
                .param("semanticVersion", command.semanticVersion())
                .query(JdbcConfigurationService::assetRow)
                .single();
    }

    private List<AssetRow> findAssets(String tenantId, List<UUID> versionIds) {
        return jdbc.sql("""
                SELECT version_id, asset_type, asset_key, semantic_version,
                       schema_version, content_digest
                  FROM cfg_configuration_asset_version
                 WHERE tenant_id = :tenantId AND status = 'PUBLISHED'
                   AND version_id IN (:versionIds)
                """)
                .param("tenantId", tenantId)
                .param("versionIds", versionIds)
                .query(JdbcConfigurationService::assetRow)
                .list();
    }

    private static AssetRow assetRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new AssetRow(
                rs.getObject("version_id", UUID.class),
                ConfigurationAssetType.valueOf(rs.getString("asset_type")),
                rs.getString("asset_key"),
                rs.getString("semantic_version"),
                rs.getString("schema_version"),
                rs.getString("content_digest"));
    }

    private ProjectRow requireActiveProject(String tenantId, UUID projectId) {
        ProjectRow project = jdbc.sql("""
                SELECT project_id, project_code FROM prj_project
                 WHERE tenant_id = :tenantId AND project_id = :projectId
                   AND project_status = 'ACTIVE'
                   AND starts_on <= CURRENT_DATE
                   AND (ends_on IS NULL OR ends_on >= CURRENT_DATE)
                """)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .query((rs, rowNum) -> new ProjectRow(
                        rs.getObject("project_id", UUID.class), rs.getString("project_code")))
                .optional().orElse(null);
        if (project == null) {
            throw new ConfigurationPublicationException("configuration bundle requires an active project");
        }
        return project;
    }

    private ProjectRow findActiveProject(String tenantId, String projectCode) {
        return jdbc.sql("""
                SELECT project_id, project_code FROM prj_project
                 WHERE tenant_id = :tenantId AND project_code = :projectCode
                   AND project_status = 'ACTIVE'
                   AND starts_on <= CURRENT_DATE
                   AND (ends_on IS NULL OR ends_on >= CURRENT_DATE)
                """)
                .param("tenantId", tenantId)
                .param("projectCode", projectCode)
                .query((rs, rowNum) -> new ProjectRow(
                        rs.getObject("project_id", UUID.class), rs.getString("project_code")))
                .optional().orElse(null);
    }

    private ExistingBundle findBundleVersion(String tenantId, String bundleCode, String bundleVersion) {
        return jdbc.sql("""
                SELECT bundle_id, project_id, bundle_code, bundle_version, manifest_digest
                  FROM cfg_configuration_bundle
                 WHERE tenant_id = :tenantId AND bundle_code = :bundleCode
                   AND bundle_version = :bundleVersion
                """)
                .param("tenantId", tenantId)
                .param("bundleCode", bundleCode)
                .param("bundleVersion", bundleVersion)
                .query((rs, rowNum) -> new ExistingBundle(
                        rs.getObject("bundle_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getString("bundle_code"),
                        rs.getString("bundle_version"),
                        rs.getString("manifest_digest")))
                .optional().orElse(null);
    }

    private boolean hasOverlappingBundle(PublishConfigurationBundleCommand command) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM cfg_configuration_bundle
                     WHERE tenant_id = :tenantId AND project_id = :projectId
                       AND brand_code = :brandCode
                       AND service_product_code = :serviceProductCode
                       AND COALESCE(province_code, '') = COALESCE(:provinceCode, '')
                       AND status = 'PUBLISHED'
                       AND effective_from < COALESCE(:effectiveUntil, 'infinity'::timestamptz)
                       AND :effectiveFrom < COALESCE(effective_until, 'infinity'::timestamptz)
                )
                """)
                .param("tenantId", command.tenantId())
                .param("projectId", command.projectId())
                .param("brandCode", command.brandCode())
                .param("serviceProductCode", command.serviceProductCode())
                .param("provinceCode", command.provinceCode(), java.sql.Types.VARCHAR)
                .param("effectiveUntil", PostgresJdbcParameters.timestamptz(command.effectiveUntil()))
                .param("effectiveFrom", PostgresJdbcParameters.timestamptz(command.effectiveFrom()))
                .query(Boolean.class)
                .single();
    }

    private static String bundleDigest(PublishConfigurationBundleCommand command, List<AssetRow> assets) {
        String itemDigest = assets.stream()
                .sorted(Comparator.comparing(asset -> asset.assetType().name()))
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

    private record AssetRow(
            UUID versionId,
            ConfigurationAssetType assetType,
            String assetKey,
            String semanticVersion,
            String schemaVersion,
            String contentDigest
    ) {
        ConfigurationAssetVersionReference reference() {
            return new ConfigurationAssetVersionReference(
                    versionId, assetType, assetKey, semanticVersion, contentDigest);
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
