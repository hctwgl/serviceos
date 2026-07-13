package com.serviceos.authorization.api;

import java.util.List;
import java.util.Map;

/**
 * 字段权限批量结果。fields 必须覆盖请求的每个字段，缺少规则时明确返回 HIDDEN。
 */
public record FieldAuthorizationDecision(
        Map<String, FieldAccessDecision> fields,
        List<String> matchedGrantIds,
        String policyVersion
) {
    public FieldAuthorizationDecision {
        fields = Map.copyOf(fields);
        matchedGrantIds = List.copyOf(matchedGrantIds);
    }
}
