package com.serviceos.bootstrap;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 结构化日志字符串值的保守脱敏器。
 *
 * <p>平台代码仍必须遵守“不记录正文”的原则；本类是最后一道防线，不是允许记录 payload 的理由。</p>
 */
final class SensitiveDataRedactor {
    private static final String MASK = "[REDACTED]";
    private static final List<Replacement> REPLACEMENTS = List.of(
            replacement("(?i)\\bBearer\\s+[A-Za-z0-9._~+\\-/=]{8,}", "Bearer " + MASK),
            replacement("(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])", MASK),
            replacement("(?i)(\\b(?:authorization|access[_-]?token|refresh[_-]?token|id[_-]?token|password|client[_-]?secret|signature)\\b\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|[^,;\\s}]+)", "$1" + MASK),
            replacement("(?<!\\d)1[3-9]\\d{9}(?!\\d)", MASK),
            replacement("(?i)(?<![A-HJ-NPR-Z0-9])[A-HJ-NPR-Z0-9]{17}(?![A-HJ-NPR-Z0-9])", MASK),
            replacement("(?i)((?:address|customerAddress|installationAddress|用户地址|安装地址)\\s*[:=]\\s*)(?:\\\"[^\\\"]*\\\"|[^,;}]+)", "$1" + MASK),
            replacement("(?i)((?:price|amount|unitPrice|totalAmount|对上金额|对下金额|结算金额)\\s*[:=]\\s*)(?:-?\\d+(?:\\.\\d+)?|\\\"[^\\\"]*\\\")", "$1" + MASK)
    );

    private SensitiveDataRedactor() {
    }

    static String redact(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String redacted = value;
        for (Replacement replacement : REPLACEMENTS) {
            redacted = replacement.pattern().matcher(redacted).replaceAll(replacement.replacement());
        }
        return redacted;
    }

    private static Replacement replacement(String pattern, String replacement) {
        return new Replacement(Pattern.compile(pattern), replacement);
    }

    private record Replacement(Pattern pattern, String replacement) {
    }
}
