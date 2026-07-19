package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.configuration.api.RuleResolution;
import com.serviceos.configuration.api.RuleResolveCommand;
import com.serviceos.configuration.api.RuleRuntime;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 冻结 Bundle RULE 执行器。
 *
 * <p>评估全部命中规则后聚合决策：BLOCK &gt; REQUIRE_APPROVAL &gt; WARN &gt; defaultAction。
 * 不写领域副作用，只返回决策与解释。</p>
 */
@Service
public class DefaultRuleRuntime implements RuleRuntime {
    private static final Set<String> SEVERITIES = Set.of("BLOCK", "WARN", "REQUIRE_APPROVAL");
    private static final Set<String> DEFAULT_ACTIONS = Set.of("PASS", "REQUIRE_MANUAL");

    private final ConfigurationService configurations;
    private final ExpressionEvaluator expressions;
    private final ObjectMapper objectMapper;

    public DefaultRuleRuntime(
            ConfigurationService configurations,
            ExpressionEvaluator expressions,
            ObjectMapper objectMapper
    ) {
        this.configurations = configurations;
        this.expressions = expressions;
        this.objectMapper = objectMapper;
    }

    @Override
    public RuleResolution resolve(RuleResolveCommand command) {
        Objects.requireNonNull(command, "command");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                command.tenantId(), command.bundleId(), command.expectedManifestDigest(),
                ConfigurationAssetType.RULE);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> command.ruleKey().equals(readRuleKey(asset)))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "RULE ruleKey not found in frozen bundle: " + command.ruleKey());
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple RULE assets share ruleKey in frozen bundle: " + command.ruleKey());
        }
        ConfigurationAssetDefinition asset = matches.getFirst();
        RuleSetDefinition definition = parse(asset.definitionJson());
        if (!command.subjectType().equals(definition.subjectType())
                || !command.stage().equals(definition.stage())) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "RULE subjectType/stage mismatch: asset="
                            + definition.subjectType() + "/" + definition.stage()
                            + " command=" + command.subjectType() + "/" + command.stage());
        }

        List<String> explanations = new ArrayList<>();
        List<RuleResolution.RuleHit> hits = new ArrayList<>();
        for (RuleItem item : definition.rules()) {
            boolean matched = expressions.evaluate(
                    new ExpressionDefinition(item.whenLanguage(), item.whenSource()),
                    command.expressionContext()).result();
            explanations.add(item.ruleCode() + ": when=" + matched
                    + " severity=" + item.severity());
            if (!matched) {
                continue;
            }
            hits.add(new RuleResolution.RuleHit(
                    item.ruleCode(), item.name(), item.severity(),
                    item.rejectReasonCode(), item.message()));
        }

        String decision = aggregate(hits, definition.defaultAction(), explanations);
        return new RuleResolution(
                definition.ruleKey(),
                asset.versionId(),
                asset.contentDigest(),
                definition.subjectType(),
                definition.stage(),
                decision,
                definition.defaultAction(),
                hits,
                explanations);
    }

    private static String aggregate(
            List<RuleResolution.RuleHit> hits,
            String defaultAction,
            List<String> explanations
    ) {
        boolean block = hits.stream().anyMatch(hit -> "BLOCK".equals(hit.severity()));
        boolean requireApproval = hits.stream()
                .anyMatch(hit -> "REQUIRE_APPROVAL".equals(hit.severity()));
        boolean warn = hits.stream().anyMatch(hit -> "WARN".equals(hit.severity()));
        String decision;
        if (block) {
            decision = "BLOCK";
        } else if (requireApproval) {
            decision = "REQUIRE_APPROVAL";
        } else if (warn) {
            decision = "PASS_WITH_WARNINGS";
        } else {
            decision = defaultAction;
        }
        explanations.add("decision=" + decision + " hits=" + hits.size()
                + " defaultAction=" + defaultAction);
        return decision;
    }

    private String readRuleKey(ConfigurationAssetDefinition asset) {
        return parse(asset.definitionJson()).ruleKey();
    }

    private RuleSetDefinition parse(String definitionJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(definitionJson, new TypeReference<>() { });
            String ruleKey = text(root.get("ruleKey"), "ruleKey");
            String subjectType = text(root.get("subjectType"), "subjectType");
            String stage = text(root.get("stage"), "stage");
            String defaultAction = text(root.get("defaultAction"), "defaultAction");
            if (!DEFAULT_ACTIONS.contains(defaultAction)) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "RULE defaultAction invalid: " + defaultAction);
            }
            Object rulesRaw = root.get("rules");
            if (!(rulesRaw instanceof List<?> ruleList) || ruleList.isEmpty()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "RULE rules must be a non-empty array");
            }
            List<RuleItem> rules = new ArrayList<>();
            Set<String> codes = new java.util.LinkedHashSet<>();
            for (Object item : ruleList) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "RULE rule item must be an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rule = (Map<String, Object>) map;
                String ruleCode = text(rule.get("ruleCode"), "ruleCode");
                if (!codes.add(ruleCode)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "RULE ruleCode must be unique: " + ruleCode);
                }
                String severity = text(rule.get("severity"), "severity");
                if (!SEVERITIES.contains(severity)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "RULE severity invalid: " + severity);
                }
                Map<?, ?> whenMap = (Map<?, ?>) rule.get("when");
                if (whenMap == null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "RULE when must be an expression object");
                }
                String message = rule.get("message") == null
                        ? null : text(rule.get("message"), "message");
                rules.add(new RuleItem(
                        ruleCode,
                        text(rule.get("name"), "name"),
                        severity,
                        text(whenMap.get("language"), "when.language"),
                        text(whenMap.get("source"), "when.source"),
                        text(rule.get("rejectReasonCode"), "rejectReasonCode"),
                        message));
            }
            return new RuleSetDefinition(ruleKey, subjectType, stage, defaultAction, rules);
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "RULE definitionJson is invalid: " + exception.getMessage());
        }
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return text.trim();
    }

    private record RuleSetDefinition(
            String ruleKey,
            String subjectType,
            String stage,
            String defaultAction,
            List<RuleItem> rules
    ) {
    }

    private record RuleItem(
            String ruleCode,
            String name,
            String severity,
            String whenLanguage,
            String whenSource,
            String rejectReasonCode,
            String message
    ) {
    }
}
