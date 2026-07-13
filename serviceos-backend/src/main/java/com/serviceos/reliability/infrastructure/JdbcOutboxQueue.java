package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.application.OutboxQueue;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PostgreSQL SKIP LOCKED Outbox 队列。claim、成功和失败各自使用独立短事务。
 */
@Repository
final class JdbcOutboxQueue implements OutboxQueue {
    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcOutboxQueue(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OutboxMessage> claimNext(String workerId, Duration leaseDuration) {
        Instant now = clock.instant();
        return jdbc.sql("""
                        WITH candidate AS (
                            SELECT outbox_id
                              FROM rel_outbox_event
                             WHERE (
                                      status IN ('PENDING', 'FAILED')
                                      AND available_at <= :now
                                   ) OR (
                                      status = 'CLAIMED'
                                      AND claim_until < :now
                                   )
                             ORDER BY available_at, created_at, outbox_id
                             FOR UPDATE SKIP LOCKED
                             LIMIT 1
                        )
                        UPDATE rel_outbox_event event
                           SET status = 'CLAIMED',
                               claim_owner = :workerId,
                               claim_until = :claimUntil,
                               attempt_count = event.attempt_count + 1
                          FROM candidate
                         WHERE event.outbox_id = candidate.outbox_id
                        RETURNING event.*
                        """)
                .params(Map.of(
                        "now", now,
                        "workerId", workerId,
                        "claimUntil", now.plus(leaseDuration)))
                .query(this::mapMessage)
                .optional();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(OutboxMessage message, String workerId, Instant attemptStartedAt) {
        int updated = jdbc.sql("""
                        UPDATE rel_outbox_event
                           SET status = 'PUBLISHED', published_at = :finishedAt,
                               claim_owner = NULL, claim_until = NULL, last_error_code = NULL
                         WHERE outbox_id = :outboxId
                           AND status = 'CLAIMED'
                           AND claim_owner = :workerId
                        """)
                .params(Map.of(
                        "finishedAt", clock.instant(),
                        "outboxId", message.outboxId(),
                        "workerId", workerId))
                .update();
        requireLease(updated);
        appendAttempt(message, workerId, attemptStartedAt, "PUBLISHED", null);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(
            OutboxMessage message,
            String workerId,
            Instant attemptStartedAt,
            String errorCode,
            int maxAttempts
    ) {
        boolean dead = message.attemptNo() >= maxAttempts;
        Instant now = clock.instant();
        Instant nextAttempt = now.plus(backoff(message.attemptNo()));
        int updated = jdbc.sql("""
                        UPDATE rel_outbox_event
                           SET status = :nextStatus,
                               available_at = :availableAt,
                               claim_owner = NULL,
                               claim_until = NULL,
                               last_error_code = :errorCode
                         WHERE outbox_id = :outboxId
                           AND status = 'CLAIMED'
                           AND claim_owner = :workerId
                        """)
                .params(Map.of(
                        "nextStatus", dead ? "DEAD" : "FAILED",
                        "availableAt", nextAttempt,
                        "errorCode", truncate(errorCode, 100),
                        "outboxId", message.outboxId(),
                        "workerId", workerId))
                .update();
        requireLease(updated);
        appendAttempt(message, workerId, attemptStartedAt, dead ? "DEAD" : "FAILED", errorCode);
    }

    private void appendAttempt(
            OutboxMessage message,
            String workerId,
            Instant startedAt,
            String result,
            String errorCode
    ) {
        jdbc.sql("""
                        INSERT INTO rel_outbox_publish_attempt (
                            attempt_id, outbox_id, attempt_no, worker_id,
                            started_at, finished_at, result_code, error_code
                        ) VALUES (
                            :attemptId, :outboxId, :attemptNo, :workerId,
                            :startedAt, :finishedAt, :result, :errorCode
                        )
                        """)
                .param("attemptId", UUID.randomUUID())
                .param("outboxId", message.outboxId())
                .param("attemptNo", message.attemptNo())
                .param("workerId", workerId)
                .param("startedAt", startedAt)
                .param("finishedAt", clock.instant())
                .param("result", result)
                .param("errorCode", errorCode == null ? null : truncate(errorCode, 100), java.sql.Types.VARCHAR)
                .update();
    }

    private OutboxMessage mapMessage(ResultSet rs, int rowNumber) throws SQLException {
        return new OutboxMessage(
                rs.getObject("outbox_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("module_name"),
                rs.getString("event_type"),
                rs.getInt("schema_version"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getLong("aggregate_version"),
                rs.getString("tenant_id"),
                rs.getString("correlation_id"),
                rs.getString("causation_id"),
                rs.getString("partition_key"),
                rs.getString("payload"),
                rs.getString("payload_digest"),
                rs.getTimestamp("occurred_at").toInstant(),
                rs.getInt("attempt_count"));
    }

    private static Duration backoff(int attemptNo) {
        long seconds = Math.min(300L, 5L << Math.min(Math.max(attemptNo - 1, 0), 6));
        return Duration.ofSeconds(seconds);
    }

    private static void requireLease(int updated) {
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.OUTBOX_LEASE_LOST, "The outbox lease is no longer owned");
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
