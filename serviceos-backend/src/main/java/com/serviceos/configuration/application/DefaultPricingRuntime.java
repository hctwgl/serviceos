package com.serviceos.configuration.application;

import com.serviceos.configuration.api.ConfigurationAssetDefinition;
import com.serviceos.configuration.api.ConfigurationAssetType;
import com.serviceos.configuration.api.ConfigurationService;
import com.serviceos.configuration.api.ExpressionDefinition;
import com.serviceos.configuration.api.ExpressionEvaluator;
import com.serviceos.configuration.api.PricingResolution;
import com.serviceos.configuration.api.PricingResolveCommand;
import com.serviceos.configuration.api.PricingRuntime;
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
 * 冻结 Bundle PRICING 试算器。
 *
 * <p>按 when 匹配计费行并累加 amountMinor；失败关闭；不创建结算事实。</p>
 */
@Service
public class DefaultPricingRuntime implements PricingRuntime {
    private static final Set<String> BILLABLE_TO =
            Set.of("OEM", "NETWORK", "PLATFORM", "CUSTOMER");

    private final ConfigurationService configurations;
    private final ExpressionEvaluator expressions;
    private final ObjectMapper objectMapper;

    public DefaultPricingRuntime(
            ConfigurationService configurations,
            ExpressionEvaluator expressions,
            ObjectMapper objectMapper
    ) {
        this.configurations = configurations;
        this.expressions = expressions;
        this.objectMapper = objectMapper;
    }

    @Override
    public PricingResolution resolve(PricingResolveCommand command) {
        Objects.requireNonNull(command, "command");
        List<ConfigurationAssetDefinition> assets = configurations.listBundleAssets(
                command.tenantId(), command.bundleId(), command.expectedManifestDigest(),
                ConfigurationAssetType.PRICING);
        List<ConfigurationAssetDefinition> matches = assets.stream()
                .filter(asset -> command.pricingKey().equals(readPricingKey(asset)))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessProblem(ProblemCode.RESOURCE_NOT_FOUND,
                    "PRICING pricingKey not found in frozen bundle: " + command.pricingKey());
        }
        if (matches.size() > 1) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Multiple PRICING assets share pricingKey in frozen bundle: "
                            + command.pricingKey());
        }
        ConfigurationAssetDefinition asset = matches.getFirst();
        PricingDefinition definition = parse(asset.definitionJson());

        List<String> explanations = new ArrayList<>();
        List<PricingResolution.MatchedLine> matched = new ArrayList<>();
        long total = 0L;
        for (PricingLine line : definition.lines()) {
            boolean when = expressions.evaluate(
                    new ExpressionDefinition(line.whenLanguage(), line.whenSource()),
                    command.expressionContext()).result();
            explanations.add(line.lineKey() + ": when=" + when
                    + " amountMinor=" + line.amountMinor());
            if (!when) {
                continue;
            }
            matched.add(new PricingResolution.MatchedLine(
                    line.lineKey(), line.chargeCode(), line.amountMinor(), line.billableTo()));
            total = Math.addExact(total, line.amountMinor());
        }
        explanations.add("currency=" + definition.currency()
                + " matched=" + matched.size() + " totalAmountMinor=" + total);

        return new PricingResolution(
                definition.pricingKey(),
                asset.versionId(),
                asset.contentDigest(),
                definition.currency(),
                total,
                matched,
                explanations);
    }

    private String readPricingKey(ConfigurationAssetDefinition asset) {
        return parse(asset.definitionJson()).pricingKey();
    }

    private PricingDefinition parse(String definitionJson) {
        try {
            Map<String, Object> root = objectMapper.readValue(definitionJson, new TypeReference<>() { });
            String pricingKey = text(root.get("pricingKey"), "pricingKey");
            String currency = text(root.get("currency"), "currency");
            if (!currency.matches("[A-Z]{3}")) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "PRICING currency must be ISO-4217 alpha-3");
            }
            Object linesRaw = root.get("lines");
            if (!(linesRaw instanceof List<?> lineList) || lineList.isEmpty()) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "PRICING lines must be a non-empty array");
            }
            List<PricingLine> lines = new ArrayList<>();
            Set<String> keys = new java.util.LinkedHashSet<>();
            for (Object item : lineList) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "PRICING line must be an object");
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> line = (Map<String, Object>) map;
                String lineKey = text(line.get("lineKey"), "lineKey");
                if (!keys.add(lineKey)) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "PRICING lineKey must be unique: " + lineKey);
                }
                long amountMinor = number(line.get("amountMinor"), "amountMinor");
                if (amountMinor < 0) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "PRICING amountMinor must not be negative: " + lineKey);
                }
                Map<?, ?> whenMap = (Map<?, ?>) line.get("when");
                if (whenMap == null) {
                    throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                            "PRICING when must be an expression object");
                }
                String billableTo = null;
                if (line.get("billableTo") != null) {
                    billableTo = text(line.get("billableTo"), "billableTo");
                    if (!BILLABLE_TO.contains(billableTo)) {
                        throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                                "PRICING billableTo invalid: " + billableTo);
                    }
                }
                lines.add(new PricingLine(
                        lineKey,
                        text(line.get("chargeCode"), "chargeCode"),
                        amountMinor,
                        text(whenMap.get("language"), "when.language"),
                        text(whenMap.get("source"), "when.source"),
                        billableTo));
            }
            return new PricingDefinition(pricingKey, currency, lines);
        } catch (BusinessProblem problem) {
            throw problem;
        } catch (ArithmeticException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "PRICING amountMinor overflow: " + exception.getMessage());
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "PRICING definitionJson is invalid: " + exception.getMessage());
        }
    }

    private static String text(Object value, String field) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        return text.trim();
    }

    private static long number(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException exception) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
            }
        }
        throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
    }

    private record PricingDefinition(
            String pricingKey,
            String currency,
            List<PricingLine> lines
    ) {
    }

    private record PricingLine(
            String lineKey,
            String chargeCode,
            long amountMinor,
            String whenLanguage,
            String whenSource,
            String billableTo
    ) {
    }
}
