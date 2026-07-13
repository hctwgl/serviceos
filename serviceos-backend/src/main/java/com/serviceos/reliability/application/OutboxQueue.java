package com.serviceos.reliability.application;

import com.serviceos.reliability.spi.OutboxMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * reliability 模块内部队列端口；每个方法拥有独立短事务。
 */
public interface OutboxQueue {
    Optional<OutboxMessage> claimNext(String workerId, Duration leaseDuration);

    void markPublished(OutboxMessage message, String workerId, Instant attemptStartedAt);

    void markFailed(
            OutboxMessage message,
            String workerId,
            Instant attemptStartedAt,
            String errorCode,
            int maxAttempts
    );
}
