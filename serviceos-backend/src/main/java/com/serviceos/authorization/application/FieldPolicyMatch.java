package com.serviceos.authorization.application;

import com.serviceos.authorization.api.FieldAccessDecision;

import java.util.Map;

/**
 * 已发布 FieldPolicy 对请求字段的合并结果；未出现在 map 中的字段由上层按 HIDDEN 处理。
 */
public record FieldPolicyMatch(Map<String, FieldAccessDecision> decisions, String policyVersion) {
    public FieldPolicyMatch {
        decisions = Map.copyOf(decisions);
    }
}
