package com.serviceos.integration.byd.infrastructure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * PostgreSQL 反重放存储。
 *
 * <p>首次请求写入记录；相同摘要视为安全重放；不同摘要视为协议冲突。</p>
 */
@Component
public class JdbcBydCpimReplayGuard {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final Duration retention;

    @Autowired
    public JdbcBydCpimReplayGuard(JdbcClient jdbc) {
        this(jdbc, Clock.systemUTC(), Duration.ofHours(48));
    }

    JdbcBydCpimReplayGuard(JdbcClient jdbc, Clock clock, Duration retention) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retention = retention;
    }

    @Transactional(noRollbackFor = BydCpimReplayConflictException.class)
    public BydCpimReplayDecision register(
            String appKey,
            String nonce,
            long currentTime,
            String payloadDigest) {
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        int inserted = jdbc.sql("""
                        INSERT INTO int_inbound_replay_guard (
                            app_key, nonce, request_time_epoch, payload_digest, first_seen_at, expires_at
                        ) VALUES (
                            :appKey, :nonce, :currentTime, :payloadDigest, :firstSeenAt, :expiresAt
                        )
                        ON CONFLICT (app_key, nonce, request_time_epoch) DO NOTHING
                        """)
                .param("appKey", appKey)
                .param("nonce", nonce)
                .param("currentTime", currentTime)
                .param("payloadDigest", payloadDigest)
                .param("firstSeenAt", now)
                .param("expiresAt", now.plus(retention))
                .update();

        if (inserted == 1) {
            return BydCpimReplayDecision.newRequest();
        }

        Existing existing = jdbc.sql("""
                        SELECT payload_digest, result_digest
                          FROM int_inbound_replay_guard
                         WHERE app_key = :appKey
                           AND nonce = :nonce
                           AND request_time_epoch = :currentTime
                        """)
                .param("appKey", appKey)
                .param("nonce", nonce)
                .param("currentTime", currentTime)
                .query((rs, rowNum) -> new Existing(
                        rs.getString("payload_digest"),
                        rs.getString("result_digest")))
                .single();

        if (!existing.payloadDigest().equals(payloadDigest)) {
            throw new BydCpimReplayConflictException();
        }
        return BydCpimReplayDecision.replay(existing.resultDigest());
    }

    @Transactional
    public void complete(String appKey, String nonce, long currentTime, String resultDigest) {
        jdbc.sql("""
                        UPDATE int_inbound_replay_guard
                           SET result_digest = :resultDigest
                         WHERE app_key = :appKey
                           AND nonce = :nonce
                           AND request_time_epoch = :currentTime
                        """)
                .param("resultDigest", resultDigest)
                .param("appKey", appKey)
                .param("nonce", nonce)
                .param("currentTime", currentTime)
                .update();
    }

    private record Existing(String payloadDigest, String resultDigest) {
    }
}
