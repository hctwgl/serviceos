package com.serviceos.reliability.infrastructure;

import com.serviceos.jooq.generated.tables.RelIdempotencyRecord;
import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.serviceos.jooq.generated.tables.RelIdempotencyRecord.REL_IDEMPOTENCY_RECORD;

/**
 * PostgreSQL 幂等实现（jOOQ）。
 *
 * <p>先用唯一约束抢占业务键，再以 {@code FOR UPDATE} 读取结果。并发请求会在同一业务键上串行，
 * 因此不会出现两个调用都认为自己是首次执行。该实现必须和领域写入处于同一 Spring 事务。</p>
 */
@Repository
final class JooqIdempotencyService implements IdempotencyService {
    private final DSLContext dsl;
    private final Clock clock;

    JooqIdempotencyService(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.clock = clock;
    }

    @Override
    public IdempotencyDecision begin(CommandContext context, String operationType, String requestDigest) {
        Instant now = clock.instant();
        RelIdempotencyRecord record = REL_IDEMPOTENCY_RECORD;
        int inserted = dsl.insertInto(record)
                .set(record.TENANT_ID, context.tenantId())
                .set(record.OPERATION_TYPE, operationType)
                .set(record.IDEMPOTENCY_KEY, context.idempotencyKey())
                .set(record.REQUEST_DIGEST, requestDigest)
                .set(record.ACTOR_ID, context.actorId())
                .set(record.STATUS, "IN_PROGRESS")
                .set(record.STARTED_AT, now)
                .set(record.EXPIRES_AT, now.plus(30, ChronoUnit.DAYS))
                .onConflict(record.TENANT_ID, record.OPERATION_TYPE, record.IDEMPOTENCY_KEY)
                .doNothing()
                .execute();

        if (inserted == 1) {
            return IdempotencyDecision.newCommand();
        }

        Record3<String, String, String> existing = dsl
                .select(record.REQUEST_DIGEST, record.STATUS, record.RESOURCE_ID)
                .from(record)
                .where(record.TENANT_ID.eq(context.tenantId()))
                .and(record.OPERATION_TYPE.eq(operationType))
                .and(record.IDEMPOTENCY_KEY.eq(context.idempotencyKey()))
                .forUpdate()
                .fetchSingle();

        if (!existing.value1().equals(requestDigest)) {
            throw new BusinessProblem(
                    ProblemCode.IDEMPOTENCY_KEY_REUSED,
                    "The idempotency key was already used with a different request");
        }
        if ("SUCCEEDED".equals(existing.value2()) && existing.value3() != null) {
            return IdempotencyDecision.replay(existing.value3());
        }
        throw new BusinessProblem(
                ProblemCode.IDEMPOTENCY_IN_PROGRESS,
                "An operation with the same idempotency key is still in progress");
    }

    @Override
    public void complete(CommandContext context, String operationType, String resourceId, String responseDigest) {
        RelIdempotencyRecord record = REL_IDEMPOTENCY_RECORD;
        // 带状态条件的迁移：只允许 IN_PROGRESS -> SUCCEEDED，影响行数不为 1 说明前置状态被破坏。
        int updated = dsl.update(record)
                .set(record.STATUS, "SUCCEEDED")
                .set(record.RESOURCE_ID, resourceId)
                .set(record.RESPONSE_DIGEST, responseDigest)
                .set(record.COMPLETED_AT, clock.instant())
                .where(record.TENANT_ID.eq(context.tenantId()))
                .and(record.OPERATION_TYPE.eq(operationType))
                .and(record.IDEMPOTENCY_KEY.eq(context.idempotencyKey()))
                .and(record.STATUS.eq("IN_PROGRESS"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("Idempotency record was not in progress");
        }
    }
}
