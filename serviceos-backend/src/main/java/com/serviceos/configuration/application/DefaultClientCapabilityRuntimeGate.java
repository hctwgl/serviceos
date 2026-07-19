package com.serviceos.configuration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.serviceos.configuration.api.ClientCapabilityRuntimeGate;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ClientMetadata;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运行时能力门禁实现。与发布门禁共用能力目录与提取器，但按单一 clientKind 失败关闭。
 */
@Service
final class DefaultClientCapabilityRuntimeGate implements ClientCapabilityRuntimeGate {
    private final ConfigurationClientCapabilityAnalyzer analyzer;
    private final ObjectMapper objectMapper;

    DefaultClientCapabilityRuntimeGate() {
        this(new ObjectMapper());
    }

    DefaultClientCapabilityRuntimeGate(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.analyzer = new ConfigurationClientCapabilityAnalyzer(objectMapper);
    }

    @Override
    public void requireCompatible(
            String clientKind, ConfigurationAssetType assetType, String definitionJson
    ) {
        if (!enforceable(clientKind)
                || (assetType != ConfigurationAssetType.FORM
                && assetType != ConfigurationAssetType.EVIDENCE)) {
            return;
        }
        Set<String> required = analyzer.requiredCapabilities(assetType, definitionJson);
        denyIfMissing(clientKind, required);
    }

    @Override
    public void requireCompatibleEvidenceSlots(
            String clientKind, List<String> mediaTypes, List<String> requirementDefinitionJsons
    ) {
        if (!enforceable(clientKind)) {
            return;
        }
        requireCompatible(clientKind, ConfigurationAssetType.EVIDENCE,
                synthesizeEvidenceDefinition(mediaTypes, requirementDefinitionJsons));
    }

    private void denyIfMissing(String clientKind, Set<String> required) {
        Set<String> supported = ClientCapabilityCatalog.capabilitiesOf(clientKind);
        List<String> missing = required.stream()
                .filter(code -> !supported.contains(code))
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        if (missing.isEmpty()) {
            return;
        }
        String detail = "当前客户端（" + clientKindLabel(clientKind) + "）不支持本任务所需配置能力："
                + missing.stream()
                .map(code -> ClientCapabilityCodes.label(code) + "（" + code + "）")
                .collect(Collectors.joining("；"))
                + "。请升级客户端，或由兼容端处理该任务。";
        throw new BusinessProblem(ProblemCode.CLIENT_CAPABILITY_UNSUPPORTED, detail);
    }

    private String synthesizeEvidenceDefinition(
            List<String> mediaTypes, List<String> requirementDefinitionJsons
    ) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode items = root.putArray("items");
        int size = Math.max(
                mediaTypes == null ? 0 : mediaTypes.size(),
                requirementDefinitionJsons == null ? 0 : requirementDefinitionJsons.size());
        for (int i = 0; i < size; i++) {
            ObjectNode item = items.addObject();
            if (mediaTypes != null && i < mediaTypes.size() && mediaTypes.get(i) != null) {
                item.put("mediaType", mediaTypes.get(i));
            }
            if (requirementDefinitionJsons != null
                    && i < requirementDefinitionJsons.size()
                    && requirementDefinitionJsons.get(i) != null
                    && !requirementDefinitionJsons.get(i).isBlank()) {
                try {
                    JsonNode definition = objectMapper.readTree(requirementDefinitionJsons.get(i));
                    if (definition.has("requiredWhen") && !definition.get("requiredWhen").isNull()) {
                        item.set("requiredWhen", definition.get("requiredWhen"));
                    }
                    if ((!item.has("mediaType") || item.path("mediaType").asText().isBlank())
                            && definition.has("mediaType")) {
                        item.put("mediaType", definition.path("mediaType").asText());
                    }
                } catch (Exception ignored) {
                    // 槽位定义损坏时仍用 mediaType 做最低门禁；解析失败不伪装成功。
                }
            }
        }
        return root.toString();
    }

    private static boolean enforceable(String clientKind) {
        return ClientCapabilityCatalog.TECHNICIAN_WEB.equals(clientKind)
                || ClientCapabilityCatalog.TECHNICIAN_IOS.equals(clientKind);
    }

    private static String clientKindLabel(String clientKind) {
        if (ClientCapabilityCatalog.TECHNICIAN_WEB.equals(clientKind)) {
            return "师傅 H5";
        }
        if (ClientCapabilityCatalog.TECHNICIAN_IOS.equals(clientKind)) {
            return "师傅 iOS";
        }
        return clientKind == null ? ClientMetadata.UNKNOWN_KIND : clientKind;
    }
}
