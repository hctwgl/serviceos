package com.serviceos.evidence.infrastructure;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluation;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.evidence.application.EvidenceTemplateResolver;
import com.serviceos.evidence.application.ResolvedEvidenceRequirement;
import com.serviceos.evidence.application.ResolvedEvidenceCondition;
import com.serviceos.evidence.application.ResolvedEvidenceTemplate;
import com.serviceos.shared.Sha256;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** M52 资料解释器：固定与 requiredWhen 条件槽位；非法表达式失败关闭。 */
@Component
final class JsonEvidenceTemplateResolver implements EvidenceTemplateResolver {
    private final ObjectMapper objectMapper;
    private final ExpressionEvaluator expressions;

    JsonEvidenceTemplateResolver(ObjectMapper objectMapper, ExpressionEvaluator expressions) {
        this.objectMapper = objectMapper;
        this.expressions = expressions;
    }

    @Override
    public ResolvedEvidenceTemplate resolve(
            ConfigurationAssetDefinition template,
            String stageCode,
            Supplier<ExpressionContext> expressionContext
    ) {
        JsonNode definition = read(template.definitionJson());
        String configuredStage = requiredText(definition, "stage");
        if (!configuredStage.equals(stageCode)) {
            return ResolvedEvidenceTemplate.empty();
        }
        List<ResolvedEvidenceRequirement> requirements = new ArrayList<>();
        List<ResolvedEvidenceCondition> conditions = new ArrayList<>();
        for (JsonNode item : definition.path("items")) {
            String evidenceKey = requiredText(item, "evidenceKey");
            JsonNode requiredWhen = item.path("requiredWhen");
            boolean conditional = item.has("requiredWhen") && !requiredWhen.isNull();
            boolean required;
            String explanation;
            if (conditional) {
                ExpressionDefinition expression = new ExpressionDefinition(
                        requiredText(requiredWhen, "language"),
                        requiredText(requiredWhen, "source"));
                ExpressionContext conditionInput = expressionContext.get();
                ExpressionEvaluation evaluation = expressions.evaluate(expression, conditionInput);
                // false 也必须固化为解析级事实，否则省略槽位后将无法解释为何没有创建要求。
                conditions.add(new ResolvedEvidenceCondition(
                        template.versionId(), template.assetKey(), template.semanticVersion(), template.contentDigest(),
                        evidenceKey, expression, evaluation.result(), evaluation.bindings()));
                if (!evaluation.result()) {
                    continue;
                }
                required = true;
                explanation = write(conditionalExplanation(item, expression, evaluation));
            } else {
                required = item.path("required").asBoolean();
                explanation = write(fixedExplanation(required));
            }

            JsonNode capture = item.path("capture");
            int minCount = capture.has("minCount")
                    ? capture.path("minCount").asInt() : required ? 1 : 0;
            Integer maxCount = capture.has("maxCount")
                    ? capture.path("maxCount").asInt() : null;
            if (required && minCount == 0) {
                throw new IllegalArgumentException(
                        "必填资料的 minCount 必须大于零: " + evidenceKey);
            }
            if (maxCount != null && minCount > maxCount) {
                throw new IllegalArgumentException(
                        "资料 minCount 不能大于 maxCount: " + evidenceKey);
            }

            String itemJson = write(item);
            String conditionInputDigest = conditional
                    ? digestContext(expressionContext.get()) : Sha256.digest("{}");
            requirements.add(new ResolvedEvidenceRequirement(
                    template.versionId(), template.assetKey(), template.semanticVersion(),
                    template.contentDigest(), evidenceKey, requiredText(item, "name"),
                    requiredText(item, "mediaType"), required, minCount, maxCount,
                    conditionInputDigest, explanation, itemJson, Sha256.digest(itemJson)));
        }
        return new ResolvedEvidenceTemplate(requirements, conditions);
    }

    private ObjectNode fixedExplanation(boolean required) {
        return objectMapper.createObjectNode()
                .put("kind", "FIXED")
                .put("required", required)
                .put("resolverVersion", EvidenceTemplateResolver.VERSION);
    }

    private ObjectNode conditionalExplanation(
            JsonNode item, ExpressionDefinition expression, ExpressionEvaluation evaluation
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("kind", "CONDITIONAL");
        node.put("required", true);
        node.put("result", evaluation.result());
        node.put("resolverVersion", EvidenceTemplateResolver.VERSION);
        node.set("expression", objectMapper.valueToTree(expression));
        node.set("bindings", objectMapper.valueToTree(evaluation.bindings()));
        node.put("evidenceKey", requiredText(item, "evidenceKey"));
        return node;
    }

    private String digestContext(ExpressionContext context) {
        return Sha256.digest(write(context));
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("已发布 EvidenceTemplate 不是合法 JSON", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Evidence 解析事实无法序列化为 JSON", exception);
        }
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("EvidenceTemplate 字段不能为空: " + field);
        }
        return value.asText();
    }
}
