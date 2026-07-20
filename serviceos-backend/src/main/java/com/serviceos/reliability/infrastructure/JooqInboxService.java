package com.serviceos.reliability.infrastructure;

import com.serviceos.jooq.generated.tables.RelInboxRecord;
import com.serviceos.reliability.api.InboxDecision;
import com.serviceos.reliability.api.InboxService;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.RelInboxRecord.REL_INBOX_RECORD;

@Repository
final class JooqInboxService implements InboxService {
    private final DSLContext dsl;
    private final Clock clock;

    JooqInboxService(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
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
        RelInboxRecord inbox = REL_INBOX_RECORD;
        // 以唯一约束抢占消费键：插入成功即首次到达；冲突再按 FOR UPDATE 读取已有记录判定重放/在途。
        int inserted = dsl.insertInto(inbox)
                .set(inbox.TENANT_ID, tenantId)
                .set(inbox.CONSUMER_NAME, consumerName)
                .set(inbox.EVENT_ID, eventId)
                .set(inbox.SCHEMA_VERSION, schemaVersion)
                .set(inbox.PAYLOAD_DIGEST, payloadDigest)
                .set(inbox.STATUS, "PROCESSING")
                .set(inbox.STARTED_AT, clock.instant())
                .onConflict(inbox.TENANT_ID, inbox.CONSUMER_NAME, inbox.EVENT_ID)
                .doNothing()
                .execute();
        if (inserted == 1) {
            return InboxDecision.newEvent();
        }

        Record2<String, String> existing = dsl.select(inbox.PAYLOAD_DIGEST, inbox.STATUS)
                .from(inbox)
                .where(inbox.TENANT_ID.eq(tenantId))
                .and(inbox.CONSUMER_NAME.eq(consumerName))
                .and(inbox.EVENT_ID.eq(eventId))
                .forUpdate()
                .fetchSingle();
        if (!existing.value1().equals(payloadDigest)) {
            throw new BusinessProblem(
                    ProblemCode.EVENT_PAYLOAD_MISMATCH,
                    "The same event id was received with a different payload digest");
        }
        if ("SUCCEEDED".equals(existing.value2())) {
            return InboxDecision.replay();
        }
        throw new BusinessProblem(ProblemCode.INBOX_IN_PROGRESS, "The event is already being processed");
    }

    @Override
    public void complete(String tenantId, String consumerName, UUID eventId, String resultDigest) {
        RelInboxRecord inbox = REL_INBOX_RECORD;
        // 带状态条件的迁移：只允许 PROCESSING -> SUCCEEDED，影响行数不为 1 说明前置状态被破坏。
        int updated = dsl.update(inbox)
                .set(inbox.STATUS, "SUCCEEDED")
                .set(inbox.RESULT_DIGEST, resultDigest)
                .set(inbox.COMPLETED_AT, clock.instant())
                .where(inbox.TENANT_ID.eq(tenantId))
                .and(inbox.CONSUMER_NAME.eq(consumerName))
                .and(inbox.EVENT_ID.eq(eventId))
                .and(inbox.STATUS.eq("PROCESSING"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("Inbox record was not processing");
        }
    }
}
