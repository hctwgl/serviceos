package com.serviceos.bootstrap;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * 对 ECS JSON 中所有字符串成员（含 message、MDC 和 stack trace）统一执行敏感值脱敏。
 */
public final class SensitiveLogJsonCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, SensitiveDataRedactor::redact));
    }
}
