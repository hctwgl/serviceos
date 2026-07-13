package com.serviceos.reliability.spi;

import java.util.function.Supplier;

/**
 * Outbox 的追踪与低基数指标端口。业务执行器不直接依赖具体 OTel SDK。
 */
public interface OutboxTelemetry {
    OutboxTelemetry NOOP = new OutboxTelemetry() {
        @Override
        public OutboxTraceHeaders capture() {
            return OutboxTraceHeaders.empty();
        }

        @Override
        public <T> T observePublish(OutboxMessage message, Supplier<T> action) {
            return action.get();
        }
    };

    /** 捕获当前 API/任务 Span 的 W3C 上下文，随领域事务写入 Outbox。 */
    OutboxTraceHeaders capture();

    /** 从持久化上下文恢复发布 Span，并记录固定结果集合的指标。 */
    <T> T observePublish(OutboxMessage message, Supplier<T> action);
}
