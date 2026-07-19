package com.serviceos.configuration.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationPublicationException;

import java.util.LinkedHashSet;
import java.util.Set;

/** 从 FORM/EVIDENCE 定义提取客户端所需能力编码。 */
final class ConfigurationClientCapabilityAnalyzer {
    private final ObjectMapper objectMapper;

    ConfigurationClientCapabilityAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Set<String> requiredCapabilities(ConfigurationAssetType assetType, String definitionJson) {
        if (assetType != ConfigurationAssetType.FORM && assetType != ConfigurationAssetType.EVIDENCE) {
            return Set.of();
        }
        JsonNode root = parse(definitionJson);
        if (assetType == ConfigurationAssetType.FORM) {
            return extractForm(root);
        }
        return extractEvidence(root);
    }

    private Set<String> extractForm(JsonNode root) {
        Set<String> required = new LinkedHashSet<>();
        for (JsonNode section : root.path("sections")) {
            if (present(section, "visibility")) {
                required.add(ClientCapabilityCodes.FORM_SECTION_VISIBILITY);
            }
            for (JsonNode field : section.path("fields")) {
                String dataType = text(field, "dataType");
                if (!dataType.isBlank()) {
                    required.add(ClientCapabilityCodes.formFieldType(dataType));
                }
                if (present(field, "visibleWhen")) {
                    required.add(ClientCapabilityCodes.FORM_VISIBLE_WHEN);
                }
                if (present(field, "requiredWhen")) {
                    required.add(ClientCapabilityCodes.FORM_REQUIRED_WHEN);
                }
                if (present(field, "optionsRef")) {
                    required.add(ClientCapabilityCodes.FORM_OPTIONS_REF);
                }
            }
        }
        if (root.path("validationRules").isArray() && !root.path("validationRules").isEmpty()) {
            required.add(ClientCapabilityCodes.FORM_VALIDATION_RULES);
        }
        return Set.copyOf(required);
    }

    private Set<String> extractEvidence(JsonNode root) {
        Set<String> required = new LinkedHashSet<>();
        for (JsonNode item : root.path("items")) {
            String mediaType = text(item, "mediaType");
            if (!mediaType.isBlank()) {
                required.add(ClientCapabilityCodes.evidenceMediaType(mediaType));
            }
            if (present(item, "requiredWhen")) {
                required.add(ClientCapabilityCodes.EVIDENCE_REQUIRED_WHEN);
            }
        }
        return Set.copyOf(required);
    }

    private JsonNode parse(String definitionJson) {
        try {
            return objectMapper.readTree(definitionJson == null ? "{}" : definitionJson);
        } catch (Exception exception) {
            throw new ConfigurationPublicationException(
                    "客户端能力分析失败：定义 JSON 无法解析");
        }
    }

    private static boolean present(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && !value.isMissingNode()
                && !(value.isTextual() && value.asText().isBlank());
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? "" : value.asText().trim();
    }
}
