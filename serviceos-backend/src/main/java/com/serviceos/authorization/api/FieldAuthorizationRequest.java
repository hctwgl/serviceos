package com.serviceos.authorization.api;

import java.util.Set;

/**
 * 字段权限请求先复用动作 capability 和数据范围判定，再对请求字段执行默认拒绝策略。
 */
public record FieldAuthorizationRequest(
        AuthorizationRequest authorization,
        Set<String> fieldCodes
) {
    public FieldAuthorizationRequest {
        if (authorization == null) {
            throw new IllegalArgumentException("authorization is required");
        }
        if (fieldCodes == null || fieldCodes.isEmpty()) {
            throw new IllegalArgumentException("fieldCodes must not be empty");
        }
        fieldCodes = Set.copyOf(fieldCodes);
        if (fieldCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("fieldCodes must not contain blank values");
        }
    }
}
