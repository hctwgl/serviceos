package com.serviceos.bootstrap;

import com.serviceos.shared.SystemRedactionPolicy;

/**
 * 日志输出脱敏适配器。
 *
 * <p>规则和总开关由 {@link SystemRedactionPolicy} 统一管理；本类保留为结构化日志适配层，
 * 避免日志实现承载系统级业务策略。</p>
 */
final class SensitiveDataRedactor {
    private SensitiveDataRedactor() {
    }

    static String redact(String value) {
        return SystemRedactionPolicy.redactFreeText(value);
    }
}
