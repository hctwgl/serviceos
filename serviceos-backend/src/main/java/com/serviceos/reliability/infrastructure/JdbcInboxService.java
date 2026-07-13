package com.serviceos.reliability.infrastructure;

import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Repository
final class JdbcInboxService implements InboxService {
    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcInboxService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public InboxDecision begin(
            String tenantId,
            String consumerName,
            UUID eventId,
            int schemaVersion,
            String payloadDigest
    ) {
        int inserted = jdbc.sql("""
                        INSERT INTO rel_inbox_record (
                            tenant_id, consumer_name, event_id, schema_version,
                            payload_digest, status, started_at
                        ) VALUES (
                            :tenantId, :consumerName, :eventId, :schemaVersion,
                            :payloadDigest, 'PROCESSING', :startedAt
                        )
                        ON CONFLICT (tenant_id, consumer_name, event_id) DO NOTHING
                        """)
                .params(Map.of(
                        "tenantId", tenantId,
                        "consumerName", consumerName,
                        "eventId", eventId,
                        "schemaVersion", schemaVersion,
                        "payloadDigest", payloadDigest,
                        "startedAt", clock.instant()))
                .update();
        if (inserted == 1) {
            return InboxDecision.newEvent();
        }

        ExistingInbox existing = jdbc.sql("""
                        SELECT payload_digest, status
                          FROM rel_inbox_record
                         WHERE tenant_id = :tenantId
                           AND consumer_name = :consumerName
                           AND event_id = :eventId
                         FOR UPDATE
                        """)
                .params(Map.of(
                        "tenantId", tenantId,
                        "consumerName", consumerName,
                        "eventId", eventId))
                .query(ExistingInbox.class)
                .single();
        if (!existing.payloadDigest().equals(payloadDigest)) {
            throw new BusinessProblem(
                    ProblemCode.EVENT_PAYLOAD_MISMATCH,
                    "The same event id was received with a different payload digest");
        }
        if ("SUCCEEDED".equals(existing.status())) {
            return InboxDecision.replay();
        }
        throw new BusinessProblem(ProblemCode.INBOX_IN_PROGRESS, "The event is already being processed");
    }

    @Override
    public void complete(String tenantId, String consumerName, UUID eventId, String resultDigest) {
        int updated = jdbc.sql("""
                        UPDATE rel_inbox_record
                           SET status = 'SUCCEEDED', result_digest = :resultDigest,
                               completed_at = :completedAt
                         WHERE tenant_id = :tenantId
                           AND consumer_name = :consumerName
                           AND event_id = :eventId
                           AND status = 'PROCESSING'
                        """)
                .params(Map.of(
                        "resultDigest", resultDigest,
                        "completedAt", clock.instant(),
                        "tenantId", tenantId,
                        "consumerName", consumerName,
                        "eventId", eventId))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Inbox record was not processing");
        }
    }

    private record ExistingInbox(String payloadDigest, String status) {
    }
}
