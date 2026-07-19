package com.serviceos.configuration.application;

import com.serviceos.configuration.api.AssigneePolicyResolution;
import com.serviceos.configuration.api.AssigneePolicyResolveCommand;
import com.serviceos.configuration.api.AssigneePolicyRuntime;
import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 冻结 Bundle ASSIGNEE_POLICY 执行器。
 *
 * <p>策略按 priority 升序求值；首个 when=true 的策略负责产生候选。
 * USER/ROLE 通过调用方提供的 principalsByRoleCode 解析；ORGANIZATION/NETWORK
 * 本切片仅产出解释并触发 Fallback（不发明主体 ID）。</p>
 */
@Service
public class DefaultAssigneePolicyRuntime implements AssigneePolicyRuntime {
    private final ConfigurationService configurations;
    private final ExpressionEvaluator expressions;
    private final ObjectMapper objectMapper;

    public DefaultAssigneePolicyRuntime(
            ConfigurationService configurations,
            ExpressionEvaluator expressions,
            ObjectMapper objectMapper
    ) {
        this.configurations = configurations;
        this.expressions = expressions;
        this.objectMapper = objectMapper;
    }

    @Override
    public AssigneePolicyResolution resolve(AssigneePolicyResolveCommand command) {
        Objects.requireNonNull(command, "command");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                command.tenantId(), command.bundleId(), command.expectedManifestDigest(),
                ConfigurationAssetType.ASSIGNEE_POLICY);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> command.policyKey().equals(readPolicyKey(asset)))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "ASSIGNEE_POLICY policyKey not found in frozen bundle: " + command.policyKey());
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple ASSIGNEE_POLICY assets share policyKey in frozen bundle: "
                            + command.policyKey());
        }
        ConfigurationAssetDefinition asset = matches.getFirst();
        PolicyDefinition policy = parse(asset.definitionJson());

        List<String> explanations = new ArrayList<>();
        List<AssigneePolicyResolution.MatchedStrategy> matched = new ArrayList<>();
        List<String> resolvedUsers = new ArrayList<>();

        List<StrategyDefinition> ordered = policy.strategies().stream()
                .sorted(Comparator.comparingInt(StrategyDefinition::priority))
                .toList();
        StrategyDefinition selected = null;
        for (StrategyDefinition strategy : ordered) {
            boolean when = expressions.evaluate(
                    new ExpressionDefinition(strategy.whenLanguage(), strategy.whenSource()),
                    command.expressionContext()).result();
            explanations.add(strategy.strategyKey() + ": when=" + when
                    + " priority=" + strategy.priority());
            if (when) {
                selected = strategy;
                break;
            }
        }

        boolean requiresManual = false;
        AssigneePolicyResolution.FallbackDecision fallbackDecision =
                new AssigneePolicyResolution.FallbackDecision(
                        policy.fallbackMode(), policy.fallbackRoleCode(), false);

        if (selected == null) {
            explanations.add("no strategy matched; applying fallback " + policy.fallbackMode());
            fallbackDecision = new AssigneePolicyResolution.FallbackDecision(
                    policy.fallbackMode(), policy.fallbackRoleCode(), true);
            requiresManual = "MANUAL_INTERVENTION".equals(policy.fallbackMode());
            if ("ROLE_POOL".equals(policy.fallbackMode())) {
                resolvedUsers.addAll(limit(
                        command.principalsByRoleCode().getOrDefault(policy.fallbackRoleCode(), List.of()),
                        100));
                if (resolvedUsers.isEmpty()) {
                    requiresManual = true;
                    explanations.add("fallback ROLE_POOL empty for role=" + policy.fallbackRoleCode());
                }
            }
        } else {
            List<String> users = resolveStrategyUsers(selected, command.principalsByRoleCode(), explanations);
            matched.add(new AssigneePolicyResolution.MatchedStrategy(
                    selected.strategyKey(), selected.candidateType(), selected.priority(),
                    selected.roleCode(), users.size()));
            resolvedUsers.addAll(users);
            if (resolvedUsers.isEmpty()) {
                explanations.add(selected.strategyKey()
                        + ": no USER candidates; applying fallback " + policy.fallbackMode());
                fallbackDecision = new AssigneePolicyResolution.FallbackDecision(
                        policy.fallbackMode(), policy.fallbackRoleCode(), true);
                requiresManual = "MANUAL_INTERVENTION".equals(policy.fallbackMode());
                if ("ROLE_POOL".equals(policy.fallbackMode())) {
                    List<String> fallbackUsers = limit(
                            command.principalsByRoleCode().getOrDefault(policy.fallbackRoleCode(), List.of()),
                            100);
                    resolvedUsers.addAll(fallbackUsers);
                    if (resolvedUsers.isEmpty()) {
                        requiresManual = true;
                        explanations.add("fallback ROLE_POOL empty for role=" + policy.fallbackRoleCode());
                    }
                }
            }
        }

        // 去重并保序
        resolvedUsers = List.copyOf(new LinkedHashSet<>(resolvedUsers));
        return new AssigneePolicyResolution(
                policy.policyKey(),
                asset.versionId(),
                asset.contentDigest(),
                matched,
                resolvedUsers,
                fallbackDecision,
                requiresManual,
                explanations);
    }

    private List<String> resolveStrategyUsers(
            StrategyDefinition strategy,
            Map<String, List<String>> principalsByRoleCode,
            List<String> explanations
    ) {
        return switch (strategy.candidateType()) {
            case "USER", "ROLE" -> {
                if (strategy.roleCode() == null || strategy.roleCode().isBlank()) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "ASSIGNEE_POLICY " + strategy.strategyKey()
                                    + " requires roleCode for " + strategy.candidateType());
                }
                List<String> pool = principalsByRoleCode.getOrDefault(strategy.roleCode(), List.of());
                List<String> selected = limit(pool, strategy.maxCandidates());
                explanations.add(strategy.strategyKey() + ": " + strategy.candidateType()
                        + " role=" + strategy.roleCode() + " selected=" + selected.size()
                        + "/" + pool.size());
                yield selected;
            }
            case "ORGANIZATION", "NETWORK" -> {
                // 本切片不发明主体 ID；仅解释并由 Fallback/人工接管。
                explanations.add(strategy.strategyKey() + ": candidateType="
                        + strategy.candidateType() + " deferred to fallback/manual");
                yield List.of();
            }
            default -> throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported ASSIGNEE_POLICY candidateType: " + strategy.candidateType());
        };
    }

    private static List<String> limit(List<String> principals, int max) {
        if (principals.isEmpty() || max <= 0) {
            return List.of();
        }
        return principals.stream().limit(max).toList();
    }

    private String readPolicyKey(ConfigurationAssetDefinition asset) {
        return parse(asset.definitionJson()).policyKey();
    }

    private PolicyDefinition parse(String definitionJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(definitionJson, new TypeReference<>() { });
            String policyKey = text(root.get("policyKey"), "policyKey");
            Object strategiesRaw = root.get("strategies");
            if (!(strategiesRaw instanceof List<?> strategyList) || strategyList.isEmpty()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "ASSIGNEE_POLICY strategies must be a non-empty array");
            }
            List<StrategyDefinition> strategies = new ArrayList<>();
            Set<String> keys = new LinkedHashSet<>();
            for (Object item : strategyList) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "ASSIGNEE_POLICY strategy must be an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> strategy = (Map<String, Object>) map;
                String strategyKey = text(strategy.get("strategyKey"), "strategyKey");
                if (!keys.add(strategyKey)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "ASSIGNEE_POLICY strategyKey must be unique: " + strategyKey);
                }
                String candidateType = text(strategy.get("candidateType"), "candidateType");
                int priority = number(strategy.get("priority"), "priority");
                Object whenRaw = strategy.get("when");
                if (!(whenRaw instanceof Map<?, ?> whenMap)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "ASSIGNEE_POLICY when must be an expression object");
                }
                String whenLanguage = text(whenMap.get("language"), "when.language");
                String whenSource = text(whenMap.get("source"), "when.source");
                String roleCode = strategy.get("roleCode") == null
                        ? null : text(strategy.get("roleCode"), "roleCode");
                int maxCandidates = strategy.get("maxCandidates") == null
                        ? 100 : number(strategy.get("maxCandidates"), "maxCandidates");
                strategies.add(new StrategyDefinition(
                        strategyKey, candidateType, priority, whenLanguage, whenSource,
                        roleCode, maxCandidates));
            }
            Object fallbackRaw = root.get("fallback");
            if (!(fallbackRaw instanceof Map<?, ?> fallbackMap)) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "ASSIGNEE_POLICY fallback must be an object");
            }
            String fallbackMode = text(fallbackMap.get("mode"), "fallback.mode");
            String fallbackRole = text(fallbackMap.get("roleCode"), "fallback.roleCode");
            return new PolicyDefinition(policyKey, strategies, fallbackMode, fallbackRole);
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "ASSIGNEE_POLICY definitionJson is invalid: " + exception.getMessage());
        }
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return text.trim();
    }

    private static int number(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return number.intValue();
    }

    private record PolicyDefinition(
            String policyKey,
            List<StrategyDefinition> strategies,
            String fallbackMode,
            String fallbackRoleCode
    ) {
    }

    private record StrategyDefinition(
            String strategyKey,
            String candidateType,
            int priority,
            String whenLanguage,
            String whenSource,
            String roleCode,
            int maxCandidates
    ) {
    }
}
