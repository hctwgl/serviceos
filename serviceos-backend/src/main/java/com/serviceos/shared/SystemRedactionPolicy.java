package com.serviceos.shared;

import java.util.List;
import java.util.regex.Pattern;

/**
 * ServiceOS 全局数据脱敏策略。
 *
 * <p>业务数据脱敏由 {@code SERVICEOS_REDACTION_ENABLED}（或 JVM 系统属性
 * {@code serviceos.redaction.enabled}）统一控制，默认关闭。凭据、令牌和签名属于安全边界，
 * 无论业务脱敏开关是否开启都必须保护。</p>
 *
 * <p>该策略只作用于日志、API 展示、导出、通知和外部交付等输出边界；数据库、领域对象、
 * 事务事件和审计事实仍保存权威原值，避免不可逆修改业务数据。</p>
 */
public final class SystemRedactionPolicy {
    public static final String PROPERTY_NAME = "serviceos.redaction.enabled";
    public static final String ENVIRONMENT_VARIABLE = "SERVICEOS_REDACTION_ENABLED";
    private static final String MASK = "[REDACTED]";

    private static final List<Replacement> ALWAYS_PROTECTED = List.of(
            replacement("(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/=]{8,}", "Bearer " + MASK),
            replacement("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])", MASK),
            replacement("(?i)(\\b(?:authorization|access[_-]?token|refresh[_-]?token|id[_-]?token|password|client[_-]?secret|signature)\\b\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|[^,;\\s}]+)", "$1" + MASK)
    );

    private static final List<Replacement> BUSINESS_DATA = List.of(
            replacement("(?<!\\d)1[3-9]\\d{9}(?!\\d)", MASK),
            replacement("(?i)(?<![A-HJ-NPR-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-HJ-NPR-Z0-9])", MASK),
            replacement("(?i)((?:address|customerAddress|installationAddress|用户地址|安装地址)\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|[^,;}]+)", "$1" + MASK),
            replacement("(?i)((?:price|amount|unitPrice|totalAmount|对上金额|对下金额|结算金额)\\s*[:=]\\s*)(?:-?\\d+(?:\\.\\d+)?|\\\"[^\\\"]*\\\")", "$1" + MASK)
    );

    private SystemRedactionPolicy() {
    }

    /** 是否启用业务数据脱敏。系统属性优先于环境变量，未配置时默认关闭。 */
    public static boolean businessDataRedactionEnabled() {
        String configured = System.getProperty(PROPERTY_NAME);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(ENVIRONMENT_VARIABLE);
        }
        if (configured == null || configured.isBlank()) {
            return false;
        }
        if ("true".equalsIgnoreCase(configured)) {
            return true;
        }
        if ("false".equalsIgnoreCase(configured)) {
            return false;
        }
        throw new IllegalStateException(
                PROPERTY_NAME + " / " + ENVIRONMENT_VARIABLE + " must be true or false");
    }

    /**
     * 对自由文本执行统一输出策略：安全凭据始终保护，业务数据仅在总开关开启时脱敏。
     */
    public static String redactFreeText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = apply(value, ALWAYS_PROTECTED);
        return businessDataRedactionEnabled() ? apply(redacted, BUSINESS_DATA) : redacted;
    }

    public static String personName(String value) {
        if (!businessDataRedactionEnabled() || value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() == 1) {
            return "*";
        }
        return trimmed.charAt(0) + "*".repeat(Math.min(trimmed.length() - 1, 3));
    }

    public static String phone(String value) {
        if (!businessDataRedactionEnabled() || value == null || value.isBlank()) {
            return value;
        }
        String digits = value.trim();
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(Math.min(digits.length() - 4, 7)) + digits.substring(digits.length() - 4);
    }

    public static String address(String value) {
        if (!businessDataRedactionEnabled() || value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 6) {
            return trimmed.charAt(0) + "***";
        }
        return trimmed.substring(0, 6) + "***";
    }

    private static String apply(String value, List<Replacement> replacements) {
        String result = value;
        for (Replacement replacement : replacements) {
            result = replacement.pattern().matcher(result).replaceAll(replacement.replacement());
        }
        return result;
    }

    private static Replacement replacement(String pattern, String replacement) {
        return new Replacement(Pattern.compile(pattern), replacement);
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
