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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 正式建单履约解析。
 *
 * <p>候选范围限定为当前项目、服务产品和 ACTIVE Profile，再按发布版本中冻结的结构化
 * 适用范围硬匹配，最后依次比较 matchPriority 与具体度。零命中或并列命中均失败关闭，
 * 禁止用数据库顺序、创建时间或主键兜底。</p>
 */
@Service
final class DefaultProjectFulfillmentResolver implements ProjectFulfillmentResolver {
    private final JdbcClient jdbc;
    private final ProjectFulfillmentDocumentMapper documents;

    DefaultProjectFulfillmentResolver(
            JdbcClient jdbc,
            ProjectFulfillmentDocumentMapper documents
    ) {
        this.jdbc = jdbc;
        this.documents = documents;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectFulfillmentResolveResult resolve(ProjectFulfillmentResolveQuery query) {
        Objects.requireNonNull(query, "query");
        List<ProfileState> profiles = loadProfileStates(query);
        requireResolvableProfileState(profiles);

        List<ResolvedRevision> effective = loadEffectiveRevisions(query);
        if (effective.isEmpty()) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_REVISION_NOT_EFFECTIVE,
                    "建单时刻没有生效的履约配置版本");
        }

        List<MatchedRevision> matches = effective.stream()
                .map(revision -> match(revision, query))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(MatchedRevision::matchPriority).reversed()
                        .thenComparing(Comparator.comparingInt(MatchedRevision::specificity).reversed()))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_PROFILE_NOT_FOUND,
                    "没有履约方案满足当前品牌、区域和客户端适用范围");
        }
        MatchedRevision hit = matches.getFirst();
        if (matches.size() > 1
                && matches.get(1).matchPriority() == hit.matchPriority()
                && matches.get(1).specificity() == hit.specificity()) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_MATCH_NOT_UNIQUE,
                    "多个履约方案具有相同匹配优先级和具体度，请调整方案适用范围或优先级");
        }

        ResolvedRevision revision = hit.revision();
        return new ProjectFulfillmentResolveResult(
                revision.profileId(),
                revision.profileCode(),
                revision.profileName(),
                revision.revisionId(),
                String.valueOf(revision.versionNo()),
                hit.matchPriority(),
                hit.specificity(),
                hit.explanation(),
                revision.bundleId(),
                revision.bundleCode(),
                revision.bundleVersion(),
                revision.bundleDigest(),
                revision.manifestJson(),
                revision.contentDigest());
    }

    private List<ProfileState> loadProfileStates(ProjectFulfillmentResolveQuery query) {
        return jdbc.sql("""
                SELECT profile_id, status
                  FROM cfg_project_fulfillment_profile
                 WHERE tenant_id = :tenantId
                   AND project_id = :projectId
                   AND service_product_code = :product
                """)
                .param("tenantId", query.tenantId())
                .param("projectId", query.projectId())
                .param("product", query.serviceProductCode())
                .query((rs, n) -> new ProfileState(
                        rs.getObject("profile_id", UUID.class),
                        rs.getString("status")))
                .list();
    }

    private static void requireResolvableProfileState(List<ProfileState> profiles) {
        if (profiles.isEmpty()) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_PROFILE_NOT_FOUND,
                    "项目未配置该工单类型的履约方案");
        }
        boolean hasActive = profiles.stream().anyMatch(profile -> "ACTIVE".equals(profile.status()));
        if (!hasActive && profiles.stream().anyMatch(profile -> "SUSPENDED".equals(profile.status()))) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_PROFILE_SUSPENDED,
                    "该工单类型的履约方案均已暂停，无法创建新工单");
        }
        if (!hasActive) {
            throw new BusinessProblem(
                    ProblemCode.PROJECT_FULFILLMENT_REVISION_NOT_EFFECTIVE,
                    "该工单类型没有可生效的履约发布版本");
        }
    }

    private List<ResolvedRevision> loadEffectiveRevisions(ProjectFulfillmentResolveQuery query) {
        return jdbc.sql("""
                SELECT p.profile_id, p.profile_code, p.profile_name, p.match_priority,
                       r.revision_id, r.version_no, r.manifest_json::text AS manifest_json,
                       r.content_digest, r.source_bundle_id,
                       b.bundle_code, b.bundle_version, b.manifest_digest AS bundle_digest
                  FROM cfg_project_fulfillment_profile p
                  JOIN cfg_project_fulfillment_revision r
                    ON r.tenant_id = p.tenant_id AND r.profile_id = p.profile_id
                  JOIN cfg_configuration_bundle b
                    ON b.tenant_id = r.tenant_id AND b.bundle_id = r.source_bundle_id
                 WHERE p.tenant_id = :tenantId
                   AND p.project_id = :projectId
                   AND p.service_product_code = :product
                   AND p.status = 'ACTIVE'
                   AND r.revision_status = 'PUBLISHED'
                   AND r.effective_from <= :at
                   AND (r.effective_to IS NULL OR r.effective_to > :at)
                """)
                .param("tenantId", query.tenantId())
                .param("projectId", query.projectId())
                .param("product", query.serviceProductCode())
                .param("at", PostgresJdbcParameters.timestamptz(query.createdAt()))
                .query((rs, n) -> new ResolvedRevision(
                        rs.getObject("profile_id", UUID.class),
                        rs.getString("profile_code"),
                        rs.getString("profile_name"),
                        rs.getInt("match_priority"),
                        rs.getObject("revision_id", UUID.class),
                        rs.getInt("version_no"),
                        rs.getString("manifest_json"),
                        rs.getString("content_digest"),
                        rs.getObject("source_bundle_id", UUID.class),
                        rs.getString("bundle_code"),
                        rs.getString("bundle_version"),
                        rs.getString("bundle_digest")))
                .list();
    }

    private MatchedRevision match(
            ResolvedRevision revision,
            ProjectFulfillmentResolveQuery query
    ) {
        Map<String, Object> manifest = documents.parseMap(revision.manifestJson());
        Map<?, ?> rule = manifest.get("matchRule") instanceof Map<?, ?> map ? map : Map.of();
        List<String> brandCodes = values(rule.get("brandCodes"));
        List<String> provinceCodes = values(rule.get("provinceCodes"));
        if (!matchesDimension(brandCodes, query.brandCode())
                || !matchesDimension(provinceCodes, query.provinceCode())) {
            return null;
        }

        int specificity = 0;
        List<String> explanation = new ArrayList<>();
        explanation.add("服务产品=" + query.serviceProductCode());
        if (!brandCodes.isEmpty()) {
            specificity++;
            explanation.add("品牌=" + query.brandCode());
        }
        if (!provinceCodes.isEmpty()) {
            specificity++;
            explanation.add("省级区域=" + query.provinceCode());
        }
        return new MatchedRevision(
                revision, revision.matchPriority(), specificity, List.copyOf(explanation));
    }

    private static boolean matchesDimension(List<String> accepted, String actual) {
        return accepted.isEmpty() || (actual != null && accepted.contains(actual));
    }

    private static List<String> values(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private record ProfileState(UUID profileId, String status) {
    }

    private record ResolvedRevision(
            UUID profileId,
            String profileCode,
            String profileName,
            int matchPriority,
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

    private record MatchedRevision(
            ResolvedRevision revision,
            int matchPriority,
            int specificity,
            List<String> explanation
    ) {
    }
}
