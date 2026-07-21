package com.serviceos.bootstrap;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * 对 ECS JSON 中所有字符串成员（含 message、MDC 和 stack trace）执行系统级输出策略：
 * 安全凭据始终保护，业务数据由全局脱敏开关控制。
 */
public final class SensitiveLogJsonCustomizer implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {
    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, SensitiveDataRedactor::redact));
    }
}
