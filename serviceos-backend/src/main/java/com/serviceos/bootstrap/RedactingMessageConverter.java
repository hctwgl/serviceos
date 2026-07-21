package com.serviceos.bootstrap;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.serviceos.shared.SystemRedactionPolicy;

/** 普通文本日志消息使用与结构化日志相同的全局脱敏策略。 */
public final class RedactingMessageConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return SystemRedactionPolicy.redactFreeText(event.getFormattedMessage());
    }
}
