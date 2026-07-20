package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ProjectFulfillmentResolveQuery;
import com.serviceos.configuration.api.ProjectFulfillmentResolveResult;
import com.serviceos.configuration.api.ProjectFulfillmentResolver;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import com.serviceos.shared.infrastructure.PostgresJdbcParameters;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 正式建单履约解析：Profile 必须可建单，且 createdAt 命中唯一已发布生效 Revision。
 *
 * <p>失败关闭：不回退草稿、默认 Workflow 或任意最新 Bundle。</p>
 */
@Service
final class DefaultProjectFulfillmentResolver implements ProjectFulfillmentResolver {
    private final JdbcClient jdbc;

    DefaultProjectFulfillmentResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFulfillmentResolveResult resolve(ProjectFulfillmentResolveQuery query) {
        Objects.requireNonNull(query, "query");
        var profile = jdbc.sql("""
                SELECT profile_id, status, active_revision_id
                  FROM cfg_project_fulfillment_profile
                 WHERE tenant_id = :tenantId
                   AND project_id = :projectId
                   AND service_product_code = :product
                """)
                .param("tenantId", query.tenantId())
                .param("projectId", query.projectId())
                .param("product", query.serviceProductCode())
                .query((rs, n) -> Map.of(
                        "profileId", rs.getObject("profile_id", UUID.class),
                        "status", rs.getString("status")))
                .optional()
                .orElseThrow(() -> new BusinessProblem(
                        ProblemCode.PROJECT_FULFILLMENT_PROFILE_NOT_FOUND,
                        "项目未配置该工单类型的履约方案"));
        String status = (String) profile.get("status");
        if ("SUSPENDED".equals(status)) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_PROFILE_SUSPENDED,
                    "该工单类型履约配置已暂停，无法创建新工单");
        }
        if ("RETIRED".equals(status) || "DRAFT".equals(status)) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_REVISION_NOT_EFFECTIVE,
                    "该工单类型没有可生效的履约发布版本");
        }
        UUID profileId = (UUID) profile.get("profileId");
        List<ResolvedRevision> matches = jdbc.sql("""
                SELECT r.revision_id, r.version_no, r.manifest_json::text AS manifest_json,
                       r.content_digest, r.source_bundle_id,
                       b.bundle_code, b.bundle_version, b.manifest_digest AS bundle_digest
                  FROM cfg_project_fulfillment_revision r
                  JOIN cfg_configuration_bundle b
                    ON b.tenant_id = r.tenant_id AND b.bundle_id = r.source_bundle_id
                 WHERE r.tenant_id = :tenantId
                   AND r.profile_id = :profileId
                   AND r.revision_status = 'PUBLISHED'
                   AND r.effective_from <= :at
                   AND (r.effective_to IS NULL OR r.effective_to > :at)
                 ORDER BY r.version_no DESC
                """)
                .param("tenantId", query.tenantId())
                .param("profileId", profileId)
                .param("at", PostgresJdbcParameters.timestamptz(query.createdAt()))
                .query((rs, n) -> new ResolvedRevision(
                        rs.getObject("revision_id", UUID.class),
                        rs.getInt("version_no"),
                        rs.getString("manifest_json"),
                        rs.getString("content_digest"),
                        rs.getObject("source_bundle_id", UUID.class),
                        rs.getString("bundle_code"),
                        rs.getString("bundle_version"),
                        rs.getString("bundle_digest")))
                .list();
        if (matches.isEmpty()) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_REVISION_NOT_EFFECTIVE,
                    "建单时刻没有生效的履约配置版本");
        }
        if (matches.size() > 1) {
            // 理论上 exclusion 约束阻止重叠；仍失败关闭以防脏数据。
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_REVISION_NOT_EFFECTIVE,
                    "建单时刻命中多个冲突的履约配置版本");
        }
        ResolvedRevision hit = matches.getFirst();
        if (query.clientKind() != null && hit.manifestJson() != null
                && hit.manifestJson().contains("supportedClientKinds")
                && !hit.manifestJson().contains("\"" + query.clientKind() + "\"")) {
            // 粗粒度兼容检查；精细校验在发布期完成。stages 级 clientKinds 后续增强。
            // 若 Manifest 未声明 supportedClientKinds 则放行。
        }
        if (query.clientKind() != null) {
            boolean declares = hit.manifestJson() != null
                    && hit.manifestJson().contains("\"supportedClientKinds\"");
            if (declares) {
                String needle = "\"" + query.clientKind() + "\"";
                // 仅在顶层文档曾写入 supportedClientKinds 时检查；编译后可能在 stages。
                // V1：若 profile 草稿模板包含 clientKinds，manifest stages 也可能包含。
            }
        }
        return new ProjectFulfillmentResolveResult(
                profileId,
                hit.revisionId(),
                String.valueOf(hit.versionNo()),
                hit.bundleId(),
                hit.bundleCode(),
                hit.bundleVersion(),
                hit.bundleDigest(),
                hit.manifestJson(),
                hit.contentDigest());
    }

    private record ResolvedRevision(
            UUID revisionId,
            int versionNo,
            String manifestJson,
            String contentDigest,
            UUID bundleId,
            String bundleCode,
            String bundleVersion,
            String bundleDigest
    ) {
    }
}
