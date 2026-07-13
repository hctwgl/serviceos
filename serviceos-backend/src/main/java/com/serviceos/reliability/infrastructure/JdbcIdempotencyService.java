package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.api.IdempotencyDecision;
import com.serviceos.reliability.api.IdempotencyService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.CommandContext;
import com.serviceos.shared.ProblemCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * PostgreSQL 幂等实现。
 *
 * <p>先用唯一约束抢占业务键，再以 {@code FOR UPDATE} 读取结果。并发请求会在同一业务键上串行，
 * 因此不会出现两个调用都认为自己是首次执行。该实现必须和领域写入处于同一 Spring 事务。</p>
 */
@Repository
final class JdbcIdempotencyService implements IdempotencyService {
    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcIdempotencyService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public IdempotencyDecision begin(CommandContext context, String operationType, String requestDigest) {
        Instant now = clock.instant();
        int inserted = jdbc.sql("""
                        INSERT INTO rel_idempotency_record (
                            tenant_id, operation_type, idempotency_key, request_digest,
                            actor_id, status, started_at, expires_at
                        ) VALUES (:tenantId, :operationType, :idempotencyKey, :requestDigest,
                                  :actorId, 'IN_PROGRESS', :startedAt, :expiresAt)
                        ON CONFLICT (tenant_id, operation_type, idempotency_key) DO NOTHING
                        """)
                .params(Map.of(
                        "tenantId", context.tenantId(),
                        "operationType", operationType,
                        "idempotencyKey", context.idempotencyKey(),
                        "requestDigest", requestDigest,
                        "actorId", context.actorId(),
                        "startedAt", now,
                        "expiresAt", now.plus(30, ChronoUnit.DAYS)))
                .update();

        if (inserted == 1) {
            return IdempotencyDecision.newCommand();
        }

        ExistingRecord existing = jdbc.sql("""
                        SELECT request_digest, status, resource_id
                          FROM rel_idempotency_record
                         WHERE tenant_id = :tenantId
                           AND operation_type = :operationType
                           AND idempotency_key = :idempotencyKey
                         FOR UPDATE
                        """)
                .params(Map.of(
                        "tenantId", context.tenantId(),
                        "operationType", operationType,
                        "idempotencyKey", context.idempotencyKey()))
                .query(ExistingRecord.class)
                .single();

        if (!existing.requestDigest().equals(requestDigest)) {
            throw new BusinessProblem(
                    ProblemCode.IDEMPOTENCY_KEY_REUSED,
                    "The idempotency key was already used with a different request");
        }
        if ("SUCCEEDED".equals(existing.status()) && existing.resourceId() != null) {
            return IdempotencyDecision.replay(existing.resourceId());
        }
        throw new BusinessProblem(
                ProblemCode.IDEMPOTENCY_IN_PROGRESS,
                "An operation with the same idempotency key is still in progress");
    }

    @Override
    public void complete(CommandContext context, String operationType, String resourceId, String responseDigest) {
        int updated = jdbc.sql("""
                        UPDATE rel_idempotency_record
                           SET status = 'SUCCEEDED', resource_id = :resourceId,
                               response_digest = :responseDigest, completed_at = :completedAt
                         WHERE tenant_id = :tenantId
                           AND operation_type = :operationType
                           AND idempotency_key = :idempotencyKey
                           AND status = 'IN_PROGRESS'
                        """)
                .params(Map.of(
                        "resourceId", resourceId,
                        "responseDigest", responseDigest,
                        "completedAt", clock.instant(),
                        "tenantId", context.tenantId(),
                        "operationType", operationType,
                        "idempotencyKey", context.idempotencyKey()))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Idempotency record was not in progress");
        }
    }

    private record ExistingRecord(String requestDigest, String status, String resourceId) {
    }
}
