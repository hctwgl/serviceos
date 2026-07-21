package com.serviceos.integration.byd.infrastructure;

import com.serviceos.jooq.generated.tables.IntInboundReplayGuard;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IntInboundReplayGuard.INT_INBOUND_REPLAY_GUARD;

/**
 * PostgreSQL 反重放存储（jOOQ 实现）。
 *
 * <p>首次请求写入记录；相同摘要视为安全重放；不同摘要视为协议冲突。</p>
 */
@Component
public class JooqBydCpimReplayGuard {
    private final DSLContext dsl;
    private final Clock clock;
    private final Duration retention;

    @Autowired
    public JooqBydCpimReplayGuard(DSLContext dsl) {
        this(dsl, Clock.systemUTC(), Duration.ofHours(48));
    }

    JooqBydCpimReplayGuard(DSLContext dsl, Clock clock, Duration retention) {
        this.dsl = dsl;
        this.clock = clock;
        this.retention = retention;
    }

    @Transactional(noRollbackFor = BydCpimReplayConflictException.class)
    public BydCpimReplayDecision register(
            String appKey,
            String nonce,
            long requestDateEpochDay,
            String payloadDigest,
            UUID inboundEnvelopeId) {
        IntInboundReplayGuard guard = INT_INBOUND_REPLAY_GUARD;
        Instant now = clock.instant();
        // ON CONFLICT DO NOTHING：并发首请求由唯一约束裁决，只有真正插入的一方才视为新请求。
        int inserted = dsl.insertInto(guard)
                .set(guard.APP_KEY, appKey)
                .set(guard.NONCE, nonce)
                .set(guard.REQUEST_DATE_EPOCH_DAY, requestDateEpochDay)
                .set(guard.PAYLOAD_DIGEST, payloadDigest)
                .set(guard.FIRST_SEEN_AT, now)
                .set(guard.EXPIRES_AT, now.plus(retention))
                .set(guard.INBOUND_ENVELOPE_ID, inboundEnvelopeId)
                .onConflict(guard.APP_KEY, guard.NONCE, guard.REQUEST_DATE_EPOCH_DAY)
                .doNothing()
                .execute();

        if (inserted == 1) {
            return BydCpimReplayDecision.newRequest(inboundEnvelopeId);
        }

        Existing existing = dsl.select(guard.PAYLOAD_DIGEST, guard.INBOUND_ENVELOPE_ID, guard.RESULT_DIGEST)
                .from(guard)
                .where(guard.APP_KEY.eq(appKey))
                .and(guard.NONCE.eq(nonce))
                .and(guard.REQUEST_DATE_EPOCH_DAY.eq(requestDateEpochDay))
                .fetchSingle(JooqBydCpimReplayGuard::existing);

        if (!existing.payloadDigest().equals(payloadDigest)) {
            throw new BydCpimReplayConflictException();
        }
        return BydCpimReplayDecision.replay(existing.inboundEnvelopeId(), existing.resultDigest());
    }

    @Transactional
    public void complete(String appKey, String nonce, long requestDateEpochDay, String resultDigest) {
        IntInboundReplayGuard guard = INT_INBOUND_REPLAY_GUARD;
        dsl.update(guard)
                .set(guard.RESULT_DIGEST, resultDigest)
                .where(guard.APP_KEY.eq(appKey))
                .and(guard.NONCE.eq(nonce))
                .and(guard.REQUEST_DATE_EPOCH_DAY.eq(requestDateEpochDay))
                .execute();
    }

    private static Existing existing(Record record) {
        IntInboundReplayGuard guard = INT_INBOUND_REPLAY_GUARD;
        return new Existing(
                record.get(guard.PAYLOAD_DIGEST),
                record.get(guard.INBOUND_ENVELOPE_ID),
                record.get(guard.RESULT_DIGEST));
    }

    private record Existing(String payloadDigest, UUID inboundEnvelopeId, String resultDigest) {
    }
}
