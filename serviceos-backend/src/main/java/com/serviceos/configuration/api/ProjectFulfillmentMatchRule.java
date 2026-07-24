package com.serviceos.configuration.api;

import java.util.List;

/**
 * 履约方案结构化适用范围。
 *
 * <p>空集合表示该维度不设限制；第一阶段只开放建单链路已具备的品牌和省级行政区，
 * 后续维度继续按 AD-014 扩展，禁止在此引入任意脚本表达式。</p>
 */
public record ProjectFulfillmentMatchRule(
        List<String> brandCodes,
        List<String> provinceCodes
) {
    public ProjectFulfillmentMatchRule {
        brandCodes = normalized(brandCodes);
        provinceCodes = normalized(provinceCodes);
    }

    public static ProjectFulfillmentMatchRule unrestricted() {
        return new ProjectFulfillmentMatchRule(List.of(), List.of());
    }

    public int constrainedDimensionCount() {
        int count = 0;
        if (!brandCodes.isEmpty()) {
            count++;
        }
        if (!provinceCodes.isEmpty()) {
            count++;
        }
        return count;
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }
}
