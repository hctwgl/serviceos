package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.DispatchCandidate;
import com.serviceos.configuration.api.DispatchResolution;
import com.serviceos.configuration.api.DispatchResolveCommand;
import com.serviceos.configuration.api.DispatchRuntime;
import com.serviceos.configuration.api.ExpressionContext;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 冻结 Bundle DISPATCH 执行器。
 *
 * <p>硬过滤：order 升序；结构化 filterKey 对候选求值，CUSTOM 仅用工单上下文表达式。
 * M337：先校验 policy.scope（brand/business/region）与工单是否相交，再求值 REGION_SCOPE
 * （候选 regionCodes 命中省/市/区任一码）。M338：ALLOCATION_RATIO_GAP 仅在
 * allocationRatio.enabled 且 measure=ORDER_COUNT 时取候选缺口；禁用则贡献 0。
 * 评分：结构化 factorKey × weight 求和；同分按 candidateId 字典序。
 * 无候选：按 fallback.onNoCandidate 降级并标记人工接管。</p>
 */
@Service
public class DefaultDispatchRuntime implements DispatchRuntime {
    private final ConfigurationService configurations;
    private final ExpressionEvaluator expressions;
    private final ObjectMapper objectMapper;

    public DefaultDispatchRuntime(
            ConfigurationService configurations,
            ExpressionEvaluator expressions,
            ObjectMapper objectMapper
    ) {
        this.configurations = configurations;
        this.expressions = expressions;
        this.objectMapper = objectMapper;
    }

    @Override
    public DispatchResolution resolve(DispatchResolveCommand command) {
        Objects.requireNonNull(command, "command");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                command.tenantId(), command.bundleId(), command.expectedManifestDigest(),
                ConfigurationAssetType.DISPATCH);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> command.policyKey().equals(readPolicyKey(asset)))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "DISPATCH policyKey not found in frozen bundle: " + command.policyKey());
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple DISPATCH assets share policyKey in frozen bundle: "
                            + command.policyKey());
        }
        ConfigurationAssetDefinition asset = matches.getFirst();
        PolicyDefinition policy = parse(asset.definitionJson());

        List<String> explanations = new ArrayList<>();
        ExpressionContext woContext = command.expressionContext();

        if (!policyScopeMatches(policy, woContext)) {
            List<DispatchResolution.RejectedCandidate> rejected = command.candidates().stream()
                    .map(c -> new DispatchResolution.RejectedCandidate(
                            c.candidateId(), "POLICY_SCOPE_MISMATCH", "SCOPE"))
                    .toList();
            return emptyWithFallback(policy, asset, rejected, explanations, true,
                    "policy scope mismatch for work order brand/business/region");
        }

        // 工单级 CUSTOM 硬过滤：任一失败则全体拒绝。
        for (FilterDefinition filter : policy.hardFilters()) {
            if (!"CUSTOM".equals(filter.filterKey())) {
                continue;
            }
            boolean ok = expressions.evaluate(
                    new ExpressionDefinition(filter.whenLanguage(), filter.whenSource()),
                    woContext).result();
            explanations.add("hardFilter " + filter.filterKey() + "/" + filter.order()
                    + " (CUSTOM wo-scoped)=" + ok);
            if (!ok) {
                List<DispatchResolution.RejectedCandidate> rejected = command.candidates().stream()
                        .map(c -> new DispatchResolution.RejectedCandidate(
                                c.candidateId(), filter.failureCode(), filter.filterKey()))
                        .toList();
                return emptyWithFallback(policy, asset, rejected, explanations, true,
                        "CUSTOM hardFilter failed: " + filter.failureCode());
            }
        }

        List<DispatchCandidate> surviving = new ArrayList<>();
        List<DispatchResolution.RejectedCandidate> rejected = new ArrayList<>();
        List<FilterDefinition> structuredFilters = policy.hardFilters().stream()
                .filter(f -> !"CUSTOM".equals(f.filterKey()))
                .sorted(Comparator.comparingInt(FilterDefinition::order))
                .toList();

        for (DispatchCandidate candidate : command.candidates()) {
            String failure = null;
            String failedFilter = null;
            for (FilterDefinition filter : structuredFilters) {
                boolean pass = applyStructuredFilter(filter.filterKey(), candidate, woContext);
                explanations.add(candidate.candidateId() + ": filter "
                        + filter.filterKey() + "/" + filter.order() + "=" + pass);
                if (!pass) {
                    failure = filter.failureCode();
                    failedFilter = filter.filterKey();
                    break;
                }
            }
            if (policy.capacityReservationRequired() && candidate.remainingCapacity() <= 0) {
                failure = failure == null ? "CAPACITY_EXHAUSTED" : failure;
                failedFilter = failedFilter == null ? "CAPACITY" : failedFilter;
                explanations.add(candidate.candidateId() + ": capacity reservation gate failed");
            }
            if (failure != null) {
                rejected.add(new DispatchResolution.RejectedCandidate(
                        candidate.candidateId(), failure, failedFilter));
            } else {
                surviving.add(candidate);
            }
        }

        List<DispatchResolution.RankedCandidate> ranked = new ArrayList<>();
        for (DispatchCandidate candidate : surviving) {
            double score = 0.0;
            List<String> breakdown = new ArrayList<>();
            for (ScoreFactor factor : policy.scoring()) {
                double factorValue = structuredFactorValue(factor.factorKey(), candidate, policy);
                double contribution = factorValue * factor.weight();
                score += contribution;
                breakdown.add(factor.factorKey() + "=" + factorValue
                        + " * " + factor.weight() + " -> " + contribution);
            }
            ranked.add(new DispatchResolution.RankedCandidate(
                    candidate.candidateId(), score, 0, breakdown));
        }
        ranked.sort(Comparator
                .comparingDouble(DispatchResolution.RankedCandidate::score).reversed()
                .thenComparing(DispatchResolution.RankedCandidate::candidateId));
        List<DispatchResolution.RankedCandidate> rankedWithOrder = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            DispatchResolution.RankedCandidate item = ranked.get(i);
            rankedWithOrder.add(new DispatchResolution.RankedCandidate(
                    item.candidateId(), item.score(), i + 1, item.scoreBreakdown()));
        }

        boolean requiresManual = false;
        DispatchResolution.FallbackDecision fallback = new DispatchResolution.FallbackDecision(
                policy.fallbackOnNoCandidate(), policy.fallbackManualRole(),
                policy.fallbackResolutionHours(), false);
        if (rankedWithOrder.isEmpty()) {
            explanations.add("no candidate survived filters; applying fallback "
                    + policy.fallbackOnNoCandidate());
            fallback = new DispatchResolution.FallbackDecision(
                    policy.fallbackOnNoCandidate(), policy.fallbackManualRole(),
                    policy.fallbackResolutionHours(), true);
            requiresManual = "MANUAL_INTERVENTION".equals(policy.fallbackOnNoCandidate());
        } else {
            explanations.add("ranked " + rankedWithOrder.size()
                    + " candidates; top=" + rankedWithOrder.getFirst().candidateId()
                    + " score=" + rankedWithOrder.getFirst().score());
        }

        return new DispatchResolution(
                policy.policyKey(),
                asset.versionId(),
                asset.contentDigest(),
                rankedWithOrder,
                rejected,
                fallback,
                requiresManual,
                explanations);
    }

    private DispatchResolution emptyWithFallback(
            PolicyDefinition policy,
            ConfigurationAssetDefinition asset,
            List<DispatchResolution.RejectedCandidate> rejected,
            List<String> explanations,
            boolean requiresManual,
            String reason
    ) {
        explanations.add(reason);
        return new DispatchResolution(
                policy.policyKey(),
                asset.versionId(),
                asset.contentDigest(),
                List.of(),
                rejected,
                new DispatchResolution.FallbackDecision(
                        policy.fallbackOnNoCandidate(), policy.fallbackManualRole(),
                        policy.fallbackResolutionHours(), true),
                requiresManual || "MANUAL_INTERVENTION".equals(policy.fallbackOnNoCandidate()),
                explanations);
    }

    private boolean applyStructuredFilter(
            String filterKey,
            DispatchCandidate candidate,
            ExpressionContext context
    ) {
        String brand = context.workOrder().brandCode();
        String product = context.workOrder().serviceProductCode();
        return switch (filterKey) {
            case "BLACKLIST" -> !candidate.blacklisted();
            case "ENABLED" -> candidate.enabled();
            case "BRAND_SCOPE" -> candidate.brandCodes().contains(brand)
                    || candidate.brandCodes().contains("*");
            case "REGION_SCOPE" -> regionMatches(candidate.regionCodes(), context.region());
            case "BUSINESS_CAPABILITY" -> candidate.businessTypes().contains(product)
                    || candidate.businessTypes().contains("*");
            case "QUALIFICATION" -> candidate.qualified();
            case "CAPACITY" -> candidate.remainingCapacity() > 0;
            default -> throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported DISPATCH hardFilter key for structured eval: " + filterKey);
        };
    }

    private static boolean policyScopeMatches(PolicyDefinition policy, ExpressionContext context) {
        String brand = context.workOrder().brandCode();
        String product = context.workOrder().serviceProductCode();
        if (!policy.scopeBrandCodes().contains(brand) && !policy.scopeBrandCodes().contains("*")) {
            return false;
        }
        if (!policy.scopeBusinessTypes().contains(product)
                && !policy.scopeBusinessTypes().contains("*")) {
            return false;
        }
        return regionMatches(policy.scopeRegionCodes(), context.region());
    }

    private static boolean regionMatches(Set<String> allowed, ExpressionContext.RegionContext region) {
        if (allowed.contains("*")) {
            return true;
        }
        return allowed.contains(region.provinceCode())
                || allowed.contains(region.cityCode())
                || allowed.contains(region.districtCode());
    }

    private static double structuredFactorValue(
            String factorKey,
            DispatchCandidate candidate,
            PolicyDefinition policy
    ) {
        return switch (factorKey) {
            case "REMAINING_CAPACITY" -> candidate.remainingCapacity();
            case "FULFILLMENT_RATE" -> candidate.fulfillmentRate();
            case "NETWORK_SCORE" -> candidate.networkScore();
            case "ALLOCATION_RATIO_GAP" -> policy.allocationRatioEnabled()
                    ? candidate.allocationRatioGap()
                    : 0.0;
            case "CURRENT_LOAD" -> candidate.currentLoad();
            case "CUSTOM" -> 0.0;
            default -> throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "Unsupported DISPATCH scoring factorKey: " + factorKey);
        };
    }

    private String readPolicyKey(ConfigurationAssetDefinition asset) {
        return parse(asset.definitionJson()).policyKey();
    }

    private PolicyDefinition parse(String definitionJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(definitionJson, new TypeReference<>() { });
            String policyKey = text(root.get("policyKey"), "policyKey");
            Object filtersRaw = root.get("hardFilters");
            if (!(filtersRaw instanceof List<?> filterList) || filterList.isEmpty()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "DISPATCH hardFilters must be a non-empty array");
            }
            List<FilterDefinition> filters = new ArrayList<>();
            Set<String> filterKeys = new java.util.LinkedHashSet<>();
            Set<Integer> orders = new java.util.LinkedHashSet<>();
            for (Object item : filterList) {
                Map<String, Object> filter = asObject(item, "hardFilter");
                String filterKey = text(filter.get("filterKey"), "filterKey");
                int order = number(filter.get("order"), "order");
                if (!filterKeys.add(filterKey) || !orders.add(order)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "DISPATCH hardFilters order/filterKey must be unique");
                }
                Map<String, Object> expression = asObject(filter.get("expression"), "expression");
                filters.add(new FilterDefinition(
                        filterKey,
                        order,
                        text(expression.get("language"), "expression.language"),
                        text(expression.get("source"), "expression.source"),
                        text(filter.get("failureCode"), "failureCode")));
            }
            List<ScoreFactor> scoring = new ArrayList<>();
            Object scoringRaw = root.get("scoring");
            if (scoringRaw instanceof List<?> scoreList) {
                Set<String> factorKeys = new java.util.LinkedHashSet<>();
                for (Object item : scoreList) {
                    Map<String, Object> factor = asObject(item, "scoring");
                    String factorKey = text(factor.get("factorKey"), "factorKey");
                    if (!factorKeys.add(factorKey)) {
                        throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                                "DISPATCH scoring factorKey must be unique: " + factorKey);
                    }
                    Map<String, Object> expression = asObject(factor.get("expression"), "expression");
                    scoring.add(new ScoreFactor(
                            factorKey,
                            numberDouble(factor.get("weight"), "weight"),
                            text(expression.get("language"), "expression.language"),
                            text(expression.get("source"), "expression.source")));
                }
            }
            Map<String, Object> fallback = asObject(root.get("fallback"), "fallback");
            boolean capacityReservationRequired = true;
            Object capacityRaw = root.get("capacity");
            if (capacityRaw instanceof Map<?, ?> capacityMap) {
                Object reservation = capacityMap.get("reservationRequired");
                if (reservation instanceof Boolean bool) {
                    capacityReservationRequired = bool;
                }
            }
            Map<String, Object> scope = asObject(root.get("scope"), "scope");
            boolean allocationRatioEnabled = parseAllocationRatioEnabled(root.get("allocationRatio"));
            return new PolicyDefinition(
                    policyKey,
                    stringSet(scope.get("brandCodes"), "scope.brandCodes"),
                    stringSet(scope.get("businessTypes"), "scope.businessTypes"),
                    stringSet(scope.get("regionCodes"), "scope.regionCodes"),
                    filters,
                    scoring,
                    capacityReservationRequired,
                    allocationRatioEnabled,
                    text(fallback.get("onNoCandidate"), "fallback.onNoCandidate"),
                    text(fallback.get("manualRole"), "fallback.manualRole"),
                    number(fallback.get("resolutionHours"), "fallback.resolutionHours"));
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "DISPATCH definitionJson is invalid: " + exception.getMessage());
        }
    }

    /**
     * allocationRatio 缺省或 enabled=false → 关闭比例评分贡献。
     * 启用时仅接受 period=MONTH + measure=ORDER_COUNT；其它口径失败关闭（不静默猜测）。
     */
    private static boolean parseAllocationRatioEnabled(Object raw) {
        if (raw == null) {
            return false;
        }
        Map<String, Object> ratio = asObject(raw, "allocationRatio");
        Object enabledRaw = ratio.get("enabled");
        boolean enabled = !(enabledRaw instanceof Boolean bool) || bool;
        if (!enabled) {
            return false;
        }
        Object period = ratio.get("period");
        if (period != null && !"MONTH".equals(text(period, "allocationRatio.period"))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "allocationRatio.period only MONTH is supported");
        }
        Object measure = ratio.get("measure");
        if (measure != null && !"ORDER_COUNT".equals(text(measure, "allocationRatio.measure"))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "allocationRatio.measure only ORDER_COUNT is supported in M338");
        }
        return true;
    }

    private static Set<String> stringSet(Object value, String field) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    field + " must be a non-empty array");
        }
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        for (Object item : list) {
            result.add(text(item, field));
        }
        return Set.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " must be an object");
        }
        return (Map<String, Object>) map;
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

    private static double numberDouble(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return number.doubleValue();
    }

    private record PolicyDefinition(
            String policyKey,
            Set<String> scopeBrandCodes,
            Set<String> scopeBusinessTypes,
            Set<String> scopeRegionCodes,
            List<FilterDefinition> hardFilters,
            List<ScoreFactor> scoring,
            boolean capacityReservationRequired,
            boolean allocationRatioEnabled,
            String fallbackOnNoCandidate,
            String fallbackManualRole,
            int fallbackResolutionHours
    ) {
    }

    private record FilterDefinition(
            String filterKey,
            int order,
            String whenLanguage,
            String whenSource,
            String failureCode
    ) {
    }

    private record ScoreFactor(
            String factorKey,
            double weight,
            String whenLanguage,
            String whenSource
    ) {
    }
}
