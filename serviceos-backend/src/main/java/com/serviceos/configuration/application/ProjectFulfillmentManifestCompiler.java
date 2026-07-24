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
        if (document.get("supportedClientKinds") != null) {
            manifest.put("supportedClientKinds", document.get("supportedClientKinds"));
        }
        manifest.put("matchRule", document.getOrDefault("matchRule", Map.of()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) document.getOrDefault(
                "nodes", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> stages = nodes.isEmpty()
                ? (List<Map<String, Object>>) document.getOrDefault("stages", List.of())
                : designerRunbookRows(nodes, document);
        List<Map<String, Object>> compiledStages = new ArrayList<>();
        for (Map<String, Object> stage : stages) {
            compiledStages.add(new LinkedHashMap<>(stage));
        }
        manifest.put("stages", compiledStages);
        if (!nodes.isEmpty()) {
            manifest.put("executionMode", "SERIAL_V1");
            manifest.put("phases", document.getOrDefault("phases", List.of()));
            manifest.put("nodes", nodes);
            manifest.put("transitions", document.getOrDefault("transitions", List.of()));
        }
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> designerRunbookRows(
            List<Map<String, Object>> nodes,
            Map<String, Object> document
    ) {
        List<Map<String, Object>> transitions = (List<Map<String, Object>>) document.getOrDefault(
                "transitions", List.of());
        List<Map<String, Object>> rows = new ArrayList<>();
        int sequence = 1;
        for (Map<String, Object> node : nodes.stream()
                .sorted(java.util.Comparator.comparingDouble(
                        item -> number(item.get("positionY"), Integer.MAX_VALUE)))
                .toList()) {
            if ("START".equals(node.get("nodeType"))) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            String nodeId = String.valueOf(node.get("nodeId"));
            Map<String, Object> task = map(node.get("task"));
            Map<String, Object> form = map(node.get("form"));
            List<Map<String, Object>> evidence = listOfMaps(node.get("evidence"));
            Map<String, Object> sla = map(node.get("sla"));
            row.put("stageCode", node.get("phaseId"));
            row.put("stageName", node.get("nodeName"));
            row.put("sequence", sequence++);
            row.put("stageType", node.get("nodeType"));
            row.put("taskType", task.get("taskType"));
            row.put("ownerType", node.get("responsibilityRole"));
            row.put("description", node.get("description"));
            row.put("formRefs", form.isEmpty() ? List.of() : List.of(form.getOrDefault("formKey", "INLINE_FORM")));
            row.put("evidenceRefs", evidence.stream()
                    .map(item -> item.getOrDefault("evidenceKey", "INLINE_EVIDENCE"))
                    .toList());
            row.put("actions", "SYSTEM_ACTION".equals(node.get("nodeType"))
                    ? List.of(node.get("systemAction")) : List.of());
            row.put("transitions", transitions.stream()
                    .filter(edge -> nodeId.equals(String.valueOf(edge.get("fromNodeId"))))
                    .toList());
            row.put("exceptionPaths", node.get("exceptionStrategy") == null
                    ? List.of() : List.of(Map.of("strategy", node.get("exceptionStrategy"))));
            row.put("slaRef", sla.get("name"));
            row.put("terminal", "END".equals(node.get("nodeType")));
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        return value instanceof List<?> list
                ? list.stream().filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item).toList()
                : List.of();
    }

    private static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    record CompiledManifest(String json, String contentDigest) {
    }
}
