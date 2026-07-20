package com.serviceos.reliability.infrastructure;

import com.serviceos.jooq.generated.tables.RelOutboxEvent;
import com.serviceos.jooq.generated.tables.records.RelOutboxEventRecord;
import com.serviceos.reliability.application.OutboxQueue;
import com.serviceos.reliability.spi.OutboxMessage;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.jooq.Name;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.RelOutboxEvent.REL_OUTBOX_EVENT;
import static com.serviceos.jooq.generated.tables.RelOutboxPublishAttempt.REL_OUTBOX_PUBLISH_ATTEMPT;

/**
 * PostgreSQL SKIP LOCKED Outbox 队列（jOOQ 实现）。claim、成功和失败各自使用独立短事务。
 */
@Repository
final class JooqOutboxQueue implements OutboxQueue {
    private static final Name CANDIDATE = DSL.name("candidate");

    private final DSLContext dsl;
    private final Clock clock;

    JooqOutboxQueue(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OutboxMessage> claimNext(String workerId, Duration leaseDuration) {
        Instant now = clock.instant();
        RelOutboxEvent event = REL_OUTBOX_EVENT;
        // 单语句完成"选候选 + 认领"：CTE 内 FOR UPDATE SKIP LOCKED 让并发 worker 互相跳过已锁行，
        // UPDATE ... FROM candidate ... RETURNING 在同事务同一语句内完成状态迁移并返回完整消息。
        return dsl.with(CANDIDATE)
                .as(dsl.select(event.OUTBOX_ID)
                        .from(event)
                        .where(event.STATUS.in("PENDING", "FAILED")
                                        .and(event.AVAILABLE_AT.le(now))
                                .or(event.STATUS.eq("CLAIMED")
                                        .and(event.CLAIM_UNTIL.lt(now))))
                        .orderBy(event.AVAILABLE_AT, event.CREATED_AT, event.OUTBOX_ID)
                        .limit(1)
                        .forUpdate()
                        .skipLocked())
                .update(event)
                .set(event.STATUS, "CLAIMED")
                .set(event.CLAIM_OWNER, workerId)
                .set(event.CLAIM_UNTIL, now.plus(leaseDuration))
                .set(event.ATTEMPT_COUNT, event.ATTEMPT_COUNT.plus(1))
                .from(DSL.table(CANDIDATE))
                .where(event.OUTBOX_ID.eq(DSL.field(CANDIDATE.append("outbox_id"), UUID.class)))
                .returning()
                .fetchOptional()
                .map(JooqOutboxQueue::mapMessage);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(OutboxMessage message, String workerId, Instant attemptStartedAt) {
        RelOutboxEvent event = REL_OUTBOX_EVENT;
        // 条件更新同时校验租约归属（status=CLAIMED 且 claim_owner=workerId），影响行数不为 1 即租约丢失。
        int updated = dsl.update(event)
                .set(event.STATUS, "PUBLISHED")
                .set(event.PUBLISHED_AT, clock.instant())
                .setNull(event.CLAIM_OWNER)
                .setNull(event.CLAIM_UNTIL)
                .setNull(event.LAST_ERROR_CODE)
                .where(event.OUTBOX_ID.eq(message.outboxId()))
                .and(event.STATUS.eq("CLAIMED"))
                .and(event.CLAIM_OWNER.eq(workerId))
                .execute();
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
        RelOutboxEvent event = REL_OUTBOX_EVENT;
        int updated = dsl.update(event)
                .set(event.STATUS, dead ? "DEAD" : "FAILED")
                .set(event.AVAILABLE_AT, nextAttempt)
                .setNull(event.CLAIM_OWNER)
                .setNull(event.CLAIM_UNTIL)
                .set(event.LAST_ERROR_CODE, truncate(errorCode, 100))
                .where(event.OUTBOX_ID.eq(message.outboxId()))
                .and(event.STATUS.eq("CLAIMED"))
                .and(event.CLAIM_OWNER.eq(workerId))
                .execute();
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
        dsl.insertInto(REL_OUTBOX_PUBLISH_ATTEMPT)
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.ATTEMPT_ID, UUID.randomUUID())
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.OUTBOX_ID, message.outboxId())
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.ATTEMPT_NO, message.attemptNo())
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.WORKER_ID, workerId)
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.STARTED_AT, startedAt)
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.FINISHED_AT, clock.instant())
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.RESULT_CODE, result)
                .set(REL_OUTBOX_PUBLISH_ATTEMPT.ERROR_CODE, errorCode == null ? null : truncate(errorCode, 100))
                .execute();
    }

    private static OutboxMessage mapMessage(RelOutboxEventRecord record) {
        return new OutboxMessage(
                record.getOutboxId(),
                record.getEventId(),
                record.getModuleName(),
                record.getEventType(),
                record.getSchemaVersion(),
                record.getAggregateType(),
                record.getAggregateId(),
                record.getAggregateVersion(),
                record.getTenantId(),
                record.getCorrelationId(),
                record.getCausationId(),
                record.getPartitionKey(),
                record.getPayload(),
                record.getPayloadDigest(),
                record.getOccurredAt(),
                record.getAttemptCount(),
                record.getTraceParent(),
                record.getTraceState());
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
