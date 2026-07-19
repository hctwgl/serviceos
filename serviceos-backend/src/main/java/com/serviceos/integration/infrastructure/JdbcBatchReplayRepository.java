package com.serviceos.integration.infrastructure;

import com.serviceos.integration.api.BatchReplayRequestView;
import com.serviceos.integration.application.BatchReplayRepository;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

@Repository
final class JdbcBatchReplayRepository implements BatchReplayRepository {
    private final JdbcClient jdbc;

    JdbcBatchReplayRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(NewBatch batch, List<NewItem> items) {
        int inserted = jdbc.sql("""
                INSERT INTO int_batch_replay_request (
                    batch_id, tenant_id, mode, status, reason, approval_ref,
                    requested_by, max_items, created_at
                ) VALUES (
                    :batchId, :tenant, :mode, :status, :reason, :approvalRef,
                    :requestedBy, :maxItems, :createdAt
                )
                """).param("batchId", batch.batchId())
                .param("tenant", batch.tenantId())
                .param("mode", batch.mode())
                .param("status", batch.status())
                .param("reason", batch.reason())
                .param("approvalRef", batch.approvalRef())
                .param("requestedBy", batch.requestedBy())
                .param("maxItems", batch.maxItems())
                .param("createdAt", timestamptz(batch.createdAt()))
                .update();
        if (inserted != 1) {
            throw new IllegalStateException("Failed to insert batch replay request");
        }
        for (NewItem item : items) {
            jdbc.sql("""
                    INSERT INTO int_batch_replay_item (
                        batch_id, delivery_id, tenant_id, project_id, eligibility,
                        ineligibility_code, expected_delivery_version, item_status
                    ) VALUES (
                        :batchId, :deliveryId, :tenant, :projectId, :eligibility,
                        :code, :version, :itemStatus
                    )
                    """).param("batchId", item.batchId())
                    .param("deliveryId", item.deliveryId())
                    .param("tenant", item.tenantId())
                    .param("projectId", item.projectId())
                    .param("eligibility", item.eligibility())
                    .param("code", item.ineligibilityCode())
                    .param("version", item.expectedDeliveryVersion())
                    .param("itemStatus", item.itemStatus())
                    .update();
        }
    }

    @Override
    public Optional<BatchReplayRequestView> find(String tenantId, UUID batchId) {
        Optional<BatchReplayRequestView> header = jdbc.sql("""
                SELECT batch_id, mode, status, reason, approval_ref, requested_by,
                       decided_by, decision, decision_note, max_items, created_at, decided_at
                  FROM int_batch_replay_request
                 WHERE tenant_id=:tenant AND batch_id=:id
                """).param("tenant", tenantId).param("id", batchId)
                .query(this::header).optional();
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<BatchReplayRequestView.BatchReplayItemView> items = jdbc.sql("""
                SELECT delivery_id, project_id, eligibility, ineligibility_code,
                       expected_delivery_version, item_status, single_replay_request_id, error_code
                  FROM int_batch_replay_item
                 WHERE tenant_id=:tenant AND batch_id=:id
                 ORDER BY delivery_id
                """).param("tenant", tenantId).param("id", batchId)
                .query(this::item).list();
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
        int updated = jdbc.sql("""
                UPDATE int_batch_replay_request
                   SET status=:status, decision=:decision, decided_by=:decidedBy,
                       decision_note=:note, decided_at=:decidedAt
                 WHERE tenant_id=:tenant AND batch_id=:id AND status='PENDING_APPROVAL'
                """).param("status", status).param("decision", decision)
                .param("decidedBy", decidedBy).param("note", decisionNote)
                .param("decidedAt", timestamptz(decidedAt))
                .param("tenant", tenantId).param("id", batchId).update();
        if (updated != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Batch replay request is not pending approval");
        }
    }

    @Override
    public void markItemScheduled(
            String tenantId, UUID batchId, UUID deliveryId, UUID singleReplayRequestId
    ) {
        int updated = jdbc.sql("""
                UPDATE int_batch_replay_item
                   SET item_status='SCHEDULED', single_replay_request_id=:replayId
                 WHERE tenant_id=:tenant AND batch_id=:batchId AND delivery_id=:deliveryId
                   AND eligibility='ELIGIBLE' AND item_status='PENDING'
                """).param("replayId", singleReplayRequestId)
                .param("tenant", tenantId).param("batchId", batchId)
                .param("deliveryId", deliveryId).update();
        if (updated != 1) {
            throw new IllegalStateException("Batch replay item could not be marked SCHEDULED");
        }
    }

    @Override
    public void markItemFailed(
            String tenantId, UUID batchId, UUID deliveryId, String errorCode
    ) {
        jdbc.sql("""
                UPDATE int_batch_replay_item
                   SET item_status='FAILED', error_code=:code
                 WHERE tenant_id=:tenant AND batch_id=:batchId AND delivery_id=:deliveryId
                   AND eligibility='ELIGIBLE' AND item_status='PENDING'
                """).param("code", errorCode)
                .param("tenant", tenantId).param("batchId", batchId)
                .param("deliveryId", deliveryId).update();
    }

    @Override
    public void markCompleted(String tenantId, UUID batchId) {
        int updated = jdbc.sql("""
                UPDATE int_batch_replay_request
                   SET status='COMPLETED'
                 WHERE tenant_id=:tenant AND batch_id=:id AND status='APPROVED'
                """).param("tenant", tenantId).param("id", batchId).update();
        if (updated != 1) {
            throw new IllegalStateException("Batch replay request could not be completed");
        }
    }

    private BatchReplayRequestView header(ResultSet rs, int row) throws SQLException {
        return new BatchReplayRequestView(
                rs.getObject("batch_id", UUID.class),
                rs.getString("mode"),
                rs.getString("status"),
                rs.getString("reason"),
                rs.getString("approval_ref"),
                rs.getString("requested_by"),
                rs.getString("decided_by"),
                rs.getString("decision"),
                rs.getString("decision_note"),
                rs.getInt("max_items"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("decided_at", java.time.OffsetDateTime.class) == null
                        ? null : rs.getObject("decided_at", java.time.OffsetDateTime.class).toInstant(),
                List.of());
    }

    private BatchReplayRequestView.BatchReplayItemView item(ResultSet rs, int row) throws SQLException {
        Long version = rs.getObject("expected_delivery_version") == null
                ? null : rs.getLong("expected_delivery_version");
        return new BatchReplayRequestView.BatchReplayItemView(
                rs.getObject("delivery_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("eligibility"),
                rs.getString("ineligibility_code"),
                version,
                rs.getString("item_status"),
                rs.getObject("single_replay_request_id", UUID.class),
                rs.getString("error_code"));
    }
}
