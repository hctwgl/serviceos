package com.serviceos.reliability.application;

import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.reliability.spi.OutboxPublisher;
import com.serviceos.reliability.spi.OutboxTelemetry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Outbox 单步执行器：短事务认领，事务外发布，再用短事务保存结果。
 *
 * <p>如果进程在 publish 成功后、markPublished 前崩溃，租约到期后会按相同 eventId 重发；
 * 下游必须用 Inbox 或等价业务唯一约束去重。</p>
 */
public final class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    public enum RunResult { EMPTY, PUBLISHED, FAILED }

    private final OutboxQueue queue;
    private final OutboxPublisher publisher;
    private final Clock clock;
    private final String workerId;
    private final Duration leaseDuration;
    private final int maxAttempts;
    private final OutboxTelemetry telemetry;

    public OutboxWorker(
            OutboxQueue queue,
            OutboxPublisher publisher,
            Clock clock,
            String workerId,
            Duration leaseDuration,
            int maxAttempts
    ) {
        this(queue, publisher, clock, workerId, leaseDuration, maxAttempts, OutboxTelemetry.NOOP);
    }

    public OutboxWorker(
            OutboxQueue queue,
            OutboxPublisher publisher,
            Clock clock,
            String workerId,
            Duration leaseDuration,
            int maxAttempts,
            OutboxTelemetry telemetry
    ) {
        this.queue = Objects.requireNonNull(queue);
        this.publisher = Objects.requireNonNull(publisher);
        this.clock = Objects.requireNonNull(clock);
        this.workerId = Objects.requireNonNull(workerId);
        this.leaseDuration = Objects.requireNonNull(leaseDuration);
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.maxAttempts = maxAttempts;
        this.telemetry = Objects.requireNonNull(telemetry);
    }

    public RunResult runOnce() {
        return queue.claimNext(workerId, leaseDuration)
                .map(this::publishClaimed)
                .orElse(RunResult.EMPTY);
    }

    private RunResult publishClaimed(OutboxMessage message) {
        return telemetry.observePublish(message, () -> publishWithinTrace(message));
    }

    private RunResult publishWithinTrace(OutboxMessage message) {
        Instant startedAt = clock.instant();
        try {
            publisher.publish(message);
        } catch (Exception exception) {
            String errorCode = exception.getClass().getSimpleName();
            log.error(
                    "Outbox 事件发布失败：eventType={}, eventId={}, aggregateType={}, aggregateId={}",
                    message.eventType(), message.eventId(), message.aggregateType(), message.aggregateId(),
                    exception);
            queue.markFailed(message, workerId, startedAt, errorCode, maxAttempts);
            return RunResult.FAILED;
        }

        // 发布已经成功或至少外部结果可能成功；若此处数据库写失败，必须保留租约等待恢复，
        // 不能再把记录改成普通发布失败，否则会掩盖 TX-004 的 UNKNOWN 窗口。
        queue.markPublished(message, workerId, startedAt);
        return RunResult.PUBLISHED;
    }
}
