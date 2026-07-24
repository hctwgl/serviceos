package com.serviceos.configuration;

import com.serviceos.configuration.api.ConfigurationBundleReference;
import com.serviceos.shared.Sha256;
import com.serviceos.shared.infrastructure.PostgresJdbcParameters;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 入站/建单 IT 共享种子：为项目+服务产品发布 ACTIVE 履约 Profile Revision。
 */
public final class ProjectFulfillmentTestSupport {
    private ProjectFulfillmentTestSupport() {
    }

    public static UUID seedPublishedProfile(
            JdbcClient jdbc,
            String tenantId,
            UUID projectId,
            String serviceProductCode,
            ConfigurationBundleReference bundle,
            UUID workflowVersionId,
            Instant effectiveFrom
    ) {
        UUID profileId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        UUID publishedId = UUID.randomUUID();
        Instant now = Instant.now();
        String document = documentJson();
        String manifest = manifestJson(
                profileId, publishedId, projectId, serviceProductCode, bundle, workflowVersionId, effectiveFrom);
        String digest = Sha256.digest(manifest);

        jdbc.sql("""
                INSERT INTO cfg_project_fulfillment_profile (
                    profile_id, tenant_id, project_id, profile_code, service_product_code,
                    profile_name, description, match_priority, status,
                    active_revision_id, draft_revision_id,
                    aggregate_version, created_by, updated_by, created_at, updated_at
                ) VALUES (
                    :profileId, :tenantId, :projectId, :product, :product, 'IT履约方案',
                    'test', 0, 'ACTIVE', NULL, NULL,
                    1, 'it', 'it', :now, :now
                )
                """)
                .param("profileId", profileId)
                .param("tenantId", tenantId)
                .param("projectId", projectId)
                .param("product", serviceProductCode)
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .update();

        jdbc.sql("""
                INSERT INTO cfg_project_fulfillment_revision (
                    revision_id, tenant_id, profile_id, version_no, revision_status,
                    document_json, created_at
                ) VALUES (
                    :draftId, :tenantId, :profileId, 0, 'DRAFT',
                    CAST(:document AS jsonb), :now
                )
                """)
                .param("draftId", draftId)
                .param("tenantId", tenantId)
                .param("profileId", profileId)
                .param("document", document)
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .update();

        jdbc.sql("""
                INSERT INTO cfg_project_fulfillment_revision (
                    revision_id, tenant_id, profile_id, version_no, revision_status,
                    document_json, source_bundle_id, workflow_asset_version_id,
                    manifest_json, validation_json, content_digest,
                    effective_from, effective_to, supersedes_revision_id,
                    published_by, published_at, created_at
                ) VALUES (
                    :publishedId, :tenantId, :profileId, 1, 'PUBLISHED',
                    CAST(:document AS jsonb), :bundleId, :workflowVersionId,
                    CAST(:manifest AS jsonb), '[]'::jsonb, :digest,
                    :effectiveFrom, NULL, NULL,
                    'it', :now, :now
                )
                """)
                .param("publishedId", publishedId)
                .param("tenantId", tenantId)
                .param("profileId", profileId)
                .param("document", document)
                .param("bundleId", bundle.bundleId())
                .param("workflowVersionId", workflowVersionId)
                .param("manifest", manifest)
                .param("digest", digest)
                .param("effectiveFrom", PostgresJdbcParameters.timestamptz(effectiveFrom))
                .param("now", PostgresJdbcParameters.timestamptz(now))
                .update();

        jdbc.sql("""
                UPDATE cfg_project_fulfillment_profile
                   SET draft_revision_id = :draftId,
                       active_revision_id = :publishedId
                 WHERE profile_id = :profileId
                """)
                .param("draftId", draftId)
                .param("publishedId", publishedId)
                .param("profileId", profileId)
                .update();
        return profileId;
    }

    private static String documentJson() {
        return """
                {"schemaVersion":"1.0.0","orderTypeName":"勘测安装","stages":[
                  {"stageCode":"SURVEY","stageName":"勘测","sequence":1,"ownerType":"TECHNICIAN","terminal":false},
                  {"stageCode":"END","stageName":"完成","sequence":2,"ownerType":"PLATFORM","terminal":true,"stageType":"END"}
                ]}
                """.replace("\n", "").replace(" ", "");
    }

    private static String manifestJson(
            UUID profileId,
            UUID revisionId,
            UUID projectId,
            String product,
            ConfigurationBundleReference bundle,
            UUID workflowVersionId,
            Instant effectiveFrom
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("profileId", profileId.toString());
        manifest.put("revisionId", revisionId.toString());
        manifest.put("projectId", projectId.toString());
        manifest.put("serviceProductCode", product);
        manifest.put("orderTypeCode", product);
        manifest.put("profileName", "IT履约方案");
        manifest.put("version", "1");
        manifest.put("bundleRef", Map.of(
                "bundleId", bundle.bundleId().toString(),
                "bundleVersion", bundle.bundleVersion()));
        manifest.put("workflowRef", Map.of("assetVersionId", workflowVersionId.toString()));
        manifest.put("effectiveFrom", effectiveFrom.toString());
        manifest.put("stages", List.of());
        try {
            return new tools.jackson.databind.json.JsonMapper().writeValueAsString(manifest);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
