package com.serviceos.reliability.spi;

/**
 * 跨 Outbox 持久化边界保存的 W3C Trace Context。
 *
 * <p>只保存标准追踪头，不保存 baggage、JWT、请求正文或事件 payload，避免把敏感信息复制到可靠队列。</p>
 */
public record OutboxTraceHeaders(String traceParent, String traceState) {
    public static OutboxTraceHeaders empty() {
        return new OutboxTraceHeaders(null, null);
    }
}
