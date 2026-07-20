package com.serviceos.configuration.application;

import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 将履约草稿文档编译为确定性 Manifest，并计算 SHA-256 digest。
 *
 * <p>前端运行说明书必须读取本编译结果，不得自行拼装。</p>
 */
@Component
final class ProjectFulfillmentManifestCompiler {
    private final JsonMapper canonicalMapper;

    ProjectFulfillmentManifestCompiler() {
        this.canonicalMapper = JsonMapper.builder()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .build();
    }

    CompiledManifest compile(
            UUID profileId,
            UUID revisionId,
            UUID projectId,
            String serviceProductCode,
            String profileName,
            String versionLabel,
            UUID bundleId,
            String bundleVersion,
            UUID workflowVersionId,
            java.time.Instant effectiveFrom,
            Map<String, Object> document
    ) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("profileId", profileId.toString());
        manifest.put("revisionId", revisionId.toString());
        manifest.put("projectId", projectId.toString());
        manifest.put("serviceProductCode", serviceProductCode);
        manifest.put("orderTypeCode", serviceProductCode);
        manifest.put("orderTypeName", stringOr(document.get("orderTypeName"), serviceProductCode));
        manifest.put("profileName", profileName);
        manifest.put("version", versionLabel);
        Map<String, Object> bundleRef = new LinkedHashMap<>();
        bundleRef.put("bundleId", bundleId == null ? null : bundleId.toString());
        bundleRef.put("bundleVersion", bundleVersion);
        manifest.put("bundleRef", bundleRef);
        Map<String, Object> workflowRef = new LinkedHashMap<>();
        workflowRef.put("assetVersionId", workflowVersionId == null ? null : workflowVersionId.toString());
        workflowRef.put("assetKey", document.get("workflowAssetKey"));
        manifest.put("workflowRef", workflowRef);
        manifest.put("effectiveFrom", effectiveFrom == null ? null : effectiveFrom.toString());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = (List<Map<String, Object>>) document.getOrDefault(
                "stages", List.of());
        List<Map<String, Object>> compiledStages = new ArrayList<>();
        for (Map<String, Object> stage : stages) {
            compiledStages.add(new LinkedHashMap<>(stage));
        }
        manifest.put("stages", compiledStages);
        try {
            String json = canonicalMapper.writeValueAsString(manifest);
            return new CompiledManifest(json, Sha256.digest(json));
        } catch (Exception ex) {
            throw new IllegalStateException("manifest compilation failed", ex);
        }
    }

    private static String stringOr(Object value, String fallback) {
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    record CompiledManifest(String json, String contentDigest) {
    }
}
