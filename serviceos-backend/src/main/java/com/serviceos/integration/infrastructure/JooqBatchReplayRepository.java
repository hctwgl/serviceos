package com.serviceos.integration.infrastructure;

import com.serviceos.integration.api.BatchReplayRequestView;
import com.serviceos.integration.application.BatchReplayRepository;
import com.serviceos.jooq.generated.tables.IntBatchReplayItem;
import com.serviceos.jooq.generated.tables.IntBatchReplayRequest;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IntBatchReplayItem.INT_BATCH_REPLAY_ITEM;
import static com.serviceos.jooq.generated.tables.IntBatchReplayRequest.INT_BATCH_REPLAY_REQUEST;

@Repository
final class JooqBatchReplayRepository implements BatchReplayRepository {
    private final DSLContext dsl;

    JooqBatchReplayRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void insert(NewBatch batch, List<NewItem> items) {
        IntBatchReplayRequest request = INT_BATCH_REPLAY_REQUEST;
        int inserted = dsl.insertInto(request)
                .set(request.BATCH_ID, batch.batchId())
                .set(request.TENANT_ID, batch.tenantId())
                .set(request.MODE, batch.mode())
                .set(request.STATUS, batch.status())
                .set(request.REASON, batch.reason())
                .set(request.APPROVAL_REF, batch.approvalRef())
                .set(request.REQUESTED_BY, batch.requestedBy())
                .set(request.MAX_ITEMS, batch.maxItems())
                .set(request.CREATED_AT, batch.createdAt())
                .execute();
        if (inserted != 1) {
            throw new IllegalStateException("Failed to insert batch replay request");
        }
        IntBatchReplayItem item = INT_BATCH_REPLAY_ITEM;
        for (NewItem newItem : items) {
            dsl.insertInto(item)
                    .set(item.BATCH_ID, newItem.batchId())
                    .set(item.DELIVERY_ID, newItem.deliveryId())
                    .set(item.TENANT_ID, newItem.tenantId())
                    .set(item.PROJECT_ID, newItem.projectId())
                    .set(item.ELIGIBILITY, newItem.eligibility())
                    .set(item.INELIGIBILITY_CODE, newItem.ineligibilityCode())
                    .set(item.EXPECTED_DELIVERY_VERSION, newItem.expectedDeliveryVersion())
                    .set(item.ITEM_STATUS, newItem.itemStatus())
                    .execute();
        }
    }

    @Override
    public Optional<BatchReplayRequestView> find(String tenantId, UUID batchId) {
        IntBatchReplayRequest request = INT_BATCH_REPLAY_REQUEST;
        Optional<BatchReplayRequestView> header = dsl.select(
                        request.BATCH_ID, request.MODE, request.STATUS, request.REASON,
                        request.APPROVAL_REF, request.REQUESTED_BY, request.DECIDED_BY,
                        request.DECISION, request.DECISION_NOTE, request.MAX_ITEMS,
                        request.CREATED_AT, request.DECIDED_AT)
                .from(request)
                .where(request.TENANT_ID.eq(tenantId))
                .and(request.BATCH_ID.eq(batchId))
                .fetchOptional(this::header);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        IntBatchReplayItem item = INT_BATCH_REPLAY_ITEM;
        List<BatchReplayRequestView.BatchReplayItemView> items = dsl.select(
                        item.DELIVERY_ID, item.PROJECT_ID, item.ELIGIBILITY, item.INELIGIBILITY_CODE,
                        item.EXPECTED_DELIVERY_VERSION, item.ITEM_STATUS,
                        item.SINGLE_REPLAY_REQUEST_ID, item.ERROR_CODE)
                .from(item)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.BATCH_ID.eq(batchId))
                .orderBy(item.DELIVERY_ID)
                .fetch(this::item);
        BatchReplayRequestView h = header.get();
        return Optional.of(new BatchReplayRequestView(
                h.batchId(), h.mode(), h.status(), h.reason(), h.approvalRef(), h.requestedBy(),
                h.decidedBy(), h.decision(), h.decisionNote(), h.maxItems(), h.createdAt(),
                h.decidedAt(), items));
    }

    @Override
    public void markDecision(
            String tenantId, UUID batchId, String status, String decision,
            String decidedBy, String decisionNote, Instant decidedAt
    ) {
        IntBatchReplayRequest request = INT_BATCH_REPLAY_REQUEST;
        // 条件更新带原状态：只有 PENDING_APPROVAL 才允许决议，影响行数不为 1 即并发冲突。
        int updated = dsl.update(request)
                .set(request.STATUS, status)
                .set(request.DECISION, decision)
                .set(request.DECIDED_BY, decidedBy)
                .set(request.DECISION_NOTE, decisionNote)
                .set(request.DECIDED_AT, decidedAt)
                .where(request.TENANT_ID.eq(tenantId))
                .and(request.BATCH_ID.eq(batchId))
                .and(request.STATUS.eq("PENDING_APPROVAL"))
                .execute();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Batch replay request is not pending approval");
        }
    }

    @Override
    public void markItemScheduled(
            String tenantId, UUID batchId, UUID deliveryId, UUID singleReplayRequestId
    ) {
        IntBatchReplayItem item = INT_BATCH_REPLAY_ITEM;
        int updated = dsl.update(item)
                .set(item.ITEM_STATUS, "SCHEDULED")
                .set(item.SINGLE_REPLAY_REQUEST_ID, singleReplayRequestId)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.BATCH_ID.eq(batchId))
                .and(item.DELIVERY_ID.eq(deliveryId))
                .and(item.ELIGIBILITY.eq("ELIGIBLE"))
                .and(item.ITEM_STATUS.eq("PENDING"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("Batch replay item could not be marked SCHEDULED");
        }
    }

    @Override
    public void markItemFailed(
            String tenantId, UUID batchId, UUID deliveryId, String errorCode
    ) {
        IntBatchReplayItem item = INT_BATCH_REPLAY_ITEM;
        dsl.update(item)
                .set(item.ITEM_STATUS, "FAILED")
                .set(item.ERROR_CODE, errorCode)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.BATCH_ID.eq(batchId))
                .and(item.DELIVERY_ID.eq(deliveryId))
                .and(item.ELIGIBILITY.eq("ELIGIBLE"))
                .and(item.ITEM_STATUS.eq("PENDING"))
                .execute();
    }

    @Override
    public void markCompleted(String tenantId, UUID batchId) {
        IntBatchReplayRequest request = INT_BATCH_REPLAY_REQUEST;
        int updated = dsl.update(request)
                .set(request.STATUS, "COMPLETED")
                .where(request.TENANT_ID.eq(tenantId))
                .and(request.BATCH_ID.eq(batchId))
                .and(request.STATUS.eq("APPROVED"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("Batch replay request could not be completed");
        }
    }

    private BatchReplayRequestView header(Record record) {
        IntBatchReplayRequest request = INT_BATCH_REPLAY_REQUEST;
        return new BatchReplayRequestView(
                record.get(request.BATCH_ID),
                record.get(request.MODE),
                record.get(request.STATUS),
                record.get(request.REASON),
                record.get(request.APPROVAL_REF),
                record.get(request.REQUESTED_BY),
                record.get(request.DECIDED_BY),
                record.get(request.DECISION),
                record.get(request.DECISION_NOTE),
                record.get(request.MAX_ITEMS),
                record.get(request.CREATED_AT),
                record.get(request.DECIDED_AT),
                List.of());
    }

    private BatchReplayRequestView.BatchReplayItemView item(Record record) {
        IntBatchReplayItem item = INT_BATCH_REPLAY_ITEM;
        return new BatchReplayRequestView.BatchReplayItemView(
                record.get(item.DELIVERY_ID),
                record.get(item.PROJECT_ID),
                record.get(item.ELIGIBILITY),
                record.get(item.INELIGIBILITY_CODE),
                record.get(item.EXPECTED_DELIVERY_VERSION),
                record.get(item.ITEM_STATUS),
                record.get(item.SINGLE_REPLAY_REQUEST_ID),
                record.get(item.ERROR_CODE));
    }
}
