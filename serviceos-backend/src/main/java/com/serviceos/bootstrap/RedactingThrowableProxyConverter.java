package com.serviceos.bootstrap;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.serviceos.shared.SystemRedactionPolicy;

/** 普通文本异常栈使用与结构化日志相同的全局脱敏策略。 */
public final class RedactingThrowableProxyConverter extends ThrowableProxyConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return SystemRedactionPolicy.redactFreeText(super.convert(event));
    }
}
