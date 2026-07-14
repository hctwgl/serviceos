package com.serviceos.evidence.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.evidence.application.EvidenceTemplateResolver;
import com.serviceos.evidence.application.ResolvedEvidenceRequirement;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/** M37 固定资料解释器：只处理无 requiredWhen 的要求，绝不猜测 ADR-018 表达式。 */
@Component
final class JsonEvidenceTemplateResolver implements EvidenceTemplateResolver {
    private static final String EMPTY_INPUT_DIGEST = Sha256.digest("{}");

    private final ObjectMapper objectMapper;

    JsonEvidenceTemplateResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ResolvedEvidenceRequirement> resolve(
            ConfigurationAssetDefinition template, String stageCode
    ) {
        JsonNode definition = read(template.definitionJson());
        String configuredStage = requiredText(definition, "stage");
        if (!configuredStage.equals(stageCode)) {
            return List.of();
        }

        List<ResolvedEvidenceRequirement> requirements = new ArrayList<>();
        for (JsonNode item : definition.path("items")) {
            String evidenceKey = requiredText(item, "evidenceKey");
            if (item.has("requiredWhen") && !item.path("requiredWhen").isNull()) {
                throw new IllegalArgumentException(
                        "Evidence requirement uses unsupported SERVICEOS_EXPR_V1: " + evidenceKey);
            }
            boolean required = item.path("required").asBoolean();
            JsonNode capture = item.path("capture");
            int minCount = capture.has("minCount")
                    ? capture.path("minCount").asInt() : required ? 1 : 0;
            Integer maxCount = capture.has("maxCount")
                    ? capture.path("maxCount").asInt() : null;
            if (required && minCount == 0) {
                throw new IllegalArgumentException(
                        "Required evidence must have minCount greater than zero: " + evidenceKey);
            }
            if (maxCount != null && minCount > maxCount) {
                throw new IllegalArgumentException(
                        "Evidence minCount must not exceed maxCount: " + evidenceKey);
            }

            String itemJson = write(item);
            String explanation = write(objectMapper.createObjectNode()
                    .put("kind", "FIXED")
                    .put("required", required)
                    .put("resolverVersion", EvidenceTemplateResolver.VERSION));
            requirements.add(new ResolvedEvidenceRequirement(
                    template.versionId(), template.assetKey(), template.semanticVersion(),
                    template.contentDigest(), evidenceKey, requiredText(item, "name"),
                    requiredText(item, "mediaType"), required, minCount, maxCount,
                    EMPTY_INPUT_DIGEST, explanation, itemJson, Sha256.digest(itemJson)));
        }
        return List.copyOf(requirements);
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Published EvidenceTemplate is not valid JSON", exception);
        }
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence resolution JSON cannot be serialized", exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("EvidenceTemplate " + field + " must not be blank");
        }
        return value.asText();
    }
}
