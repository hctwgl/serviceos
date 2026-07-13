package com.serviceos.authorization.api;

/**
 * 单字段授权结果。maskCode 仅在 MASKED 时有值，调用方必须使用注册的脱敏算法，不能自行解释脚本。
 */
public record FieldAccessDecision(FieldPermission permission, String maskCode) {
    public static FieldAccessDecision hidden() {
        return new FieldAccessDecision(FieldPermission.HIDDEN, null);
    }
}
