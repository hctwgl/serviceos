package com.serviceos.integration.infrastructure;

import com.serviceos.integration.api.DeliveryAttemptView;
import com.serviceos.integration.api.DeliveryReplayRequestView;
import com.serviceos.integration.api.DeliveryTimelineContext;
import com.serviceos.integration.api.ExternalAcknowledgementView;
import com.serviceos.integration.api.ManualDispositionView;
import com.serviceos.integration.api.OutboundDeliveryQueueItem;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.application.OutboundDeliveryRepository;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.shared.infrastructure.PostgresJdbcParameters.timestamptz;

/** 使用 PostgreSQL 条件更新保护外部副作用状态，禁止先读后写覆盖并发结果。 */
@Repository
final class JdbcOutboundDeliveryRepository implements OutboundDeliveryRepository {
    private static final String DELIVERY_SELECT = """
            SELECT delivery_id, project_id, connector_version_id, mapping_version_id,
                   business_message_type, business_key, source_review_case_id, source_task_id,
                   source_work_order_id, source_snapshot_id, source_snapshot_digest,
                   external_order_code, operator_principal_id, operator_display_value,
                   payload_object_ref, payload_digest, external_idempotency_key,
                   failure_policy_version_id, execution_task_id, status,
                   client_review_case_id, review_route_id, aggregate_version,
                   created_at, delivered_at, acknowledged_at
              FROM int_outbound_delivery
            """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    JdbcOutboundDeliveryRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Registration register(NewDelivery delivery) {
        int inserted = jdbc.sql("""
                INSERT INTO int_outbound_delivery (
                    delivery_id, tenant_id, project_id, connector_version_id, mapping_version_id,
                    business_message_type, business_key, source_review_case_id, source_task_id,
                    source_work_order_id, source_snapshot_id, source_snapshot_digest,
                    external_order_code, operator_principal_id, operator_display_value,
                    payload_object_ref, payload_digest, external_idempotency_key,
                    failure_policy_version_id, status, created_by, created_at)
                VALUES (:id, :tenant, :project, :connector, :mapping, :messageType, :businessKey,
                    :sourceReview, :sourceTask, :sourceWorkOrder, :sourceSnapshot, :snapshotDigest,
                    :orderCode, :operatorId, :operatorValue, :payloadRef, :payloadDigest,
                    :externalKey, :failurePolicy, 'PENDING', :createdBy, :createdAt)
                ON CONFLICT DO NOTHING
                """)
                .param("id", delivery.deliveryId()).param("tenant", delivery.tenantId())
                .param("project", delivery.projectId()).param("connector", delivery.connectorVersionId())
                .param("mapping", delivery.mappingVersionId()).param("messageType", delivery.businessMessageType())
                .param("businessKey", delivery.businessKey()).param("sourceReview", delivery.sourceReviewCaseId())
                .param("sourceTask", delivery.sourceTaskId()).param("sourceWorkOrder", delivery.sourceWorkOrderId())
                .param("sourceSnapshot", delivery.sourceSnapshotId())
                .param("snapshotDigest", delivery.sourceSnapshotDigest())
                .param("orderCode", delivery.externalOrderCode())
                .param("operatorId", delivery.operatorPrincipalId())
                .param("operatorValue", delivery.operatorDisplayValue())
                .param("payloadRef", delivery.payloadObjectRef()).param("payloadDigest", delivery.payloadDigest())
                .param("externalKey", delivery.externalIdempotencyKey())
                .param("failurePolicy", delivery.failurePolicyVersionId())
                .param("createdBy", delivery.createdBy()).param("createdAt", timestamptz(delivery.createdAt()))
                .update();
        DeliveryRecord current = findBySourceReview(
                delivery.tenantId(), delivery.sourceReviewCaseId(), delivery.businessMessageType())
                .orElseThrow(() -> new IllegalStateException("OutboundDelivery registration result missing"));
        return new Registration(current, inserted == 1);
    }

    @Override
    public void attachExecutionTask(String tenantId, UUID deliveryId, UUID taskId, Instant updatedAt) {
        int updated = jdbc.sql("""
                UPDATE int_outbound_delivery
                   SET execution_task_id=:taskId, aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND delivery_id=:id AND status='PENDING'
                   AND (execution_task_id IS NULL OR execution_task_id=:taskId)
                """).param("taskId", taskId).param("tenant", tenantId).param("id", deliveryId).update();
        if (updated != 1) {
            DeliveryRecord current = find(tenantId, deliveryId)
                    .orElseThrow(() -> new IllegalStateException("OutboundDelivery disappeared"));
            if (!taskId.equals(current.view().executionTaskId())) {
                throw new IllegalStateException("OutboundDelivery execution Task conflicts");
            }
        }
    }

    @Override
    public Optional<DeliveryRecord> find(String tenantId, UUID deliveryId) {
        return jdbc.sql(DELIVERY_SELECT + " WHERE tenant_id=:tenant AND delivery_id=:id")
                .param("tenant", tenantId).param("id", deliveryId).query(this::delivery).optional();
    }

    @Override
    public Optional<DeliveryTimelineContext> findTimelineContext(String tenantId, UUID deliveryId) {
        // 时间线只需要稳定身份；禁止在此路径加载 attempt/ack 图以免放大跨模块投影成本。
        return jdbc.sql("""
                SELECT delivery_id, project_id, source_work_order_id
                  FROM int_outbound_delivery
                 WHERE tenant_id=:tenant AND delivery_id=:id
                """)
                .param("tenant", tenantId)
                .param("id", deliveryId)
                .query((rs, row) -> new DeliveryTimelineContext(
                        rs.getObject("delivery_id", UUID.class),
                        rs.getObject("project_id", UUID.class),
                        rs.getObject("source_work_order_id", UUID.class)))
                .optional();
    }

    @Override
    public Optional<DeliveryRecord> findBySourceReview(
            String tenantId, UUID sourceReviewCaseId, String businessMessageType
    ) {
        return jdbc.sql(DELIVERY_SELECT + """
                 WHERE tenant_id=:tenant AND source_review_case_id=:sourceReview
                   AND business_message_type=:messageType
                """).param("tenant", tenantId).param("sourceReview", sourceReviewCaseId)
                .param("messageType", businessMessageType).query(this::delivery).optional();
    }

    @Override
    public List<DeliveryRecord> listByWorkOrder(
            String tenantId, UUID projectId, UUID workOrderId, int limit
    ) {
        return jdbc.sql(DELIVERY_SELECT + """
                 WHERE tenant_id=:tenant
                   AND project_id=:projectId
                   AND source_work_order_id=:workOrderId
                 ORDER BY created_at, delivery_id
                 LIMIT :limit
                """)
                .param("tenant", tenantId)
                .param("projectId", projectId)
                .param("workOrderId", workOrderId)
                .param("limit", limit)
                .query(this::delivery)
                .list();
    }

    @Override
    public List<OutboundDeliveryQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String status,
            String businessMessageType,
            UUID sourceWorkOrderId,
            UUID sourceReviewCaseId,
            Instant cursorCreatedAt,
            UUID cursorId,
            int fetchSize
    ) {
        StringBuilder sql = new StringBuilder("""
                SELECT delivery_id, project_id, connector_version_id, mapping_version_id,
                       business_message_type, business_key, source_review_case_id, source_task_id,
                       source_work_order_id, source_snapshot_id, external_order_code,
                       execution_task_id, status, client_review_case_id, review_route_id,
                       aggregate_version, attempt_count, created_at, delivered_at, acknowledged_at
                  FROM int_outbound_delivery
                 WHERE tenant_id = :tenant
                   AND status = :status
                """);
        if (!tenantWide) {
            if (projectIds == null || projectIds.isEmpty()) {
                sql.append(" AND 1 = 0");
            } else {
                sql.append(" AND project_id IN (");
                for (int index = 0; index < projectIds.size(); index++) {
                    if (index > 0) {
                        sql.append(", ");
                    }
                    sql.append(":projectId").append(index);
                }
                sql.append(')');
            }
        }
        if (businessMessageType != null) {
            sql.append(" AND business_message_type = :messageType");
        }
        if (sourceWorkOrderId != null) {
            sql.append(" AND source_work_order_id = :workOrderId");
        }
        if (sourceReviewCaseId != null) {
            sql.append(" AND source_review_case_id = :reviewCaseId");
        }
        if (cursorCreatedAt != null) {
            sql.append("""
                     AND (created_at, delivery_id) > (:cursorCreatedAt, :cursorId)
                    """);
        }
        sql.append("""
                 ORDER BY created_at, delivery_id
                 LIMIT :fetchSize
                """);
        var query = jdbc.sql(sql.toString())
                .param("tenant", tenantId)
                .param("status", status)
                .param("fetchSize", fetchSize);
        if (!tenantWide && projectIds != null) {
            for (int index = 0; index < projectIds.size(); index++) {
                query = query.param("projectId" + index, projectIds.get(index));
            }
        }
        if (businessMessageType != null) {
            query = query.param("messageType", businessMessageType);
        }
        if (sourceWorkOrderId != null) {
            query = query.param("workOrderId", sourceWorkOrderId);
        }
        if (sourceReviewCaseId != null) {
            query = query.param("reviewCaseId", sourceReviewCaseId);
        }
        if (cursorCreatedAt != null) {
            query = query.param("cursorCreatedAt", timestamptz(cursorCreatedAt))
                    .param("cursorId", cursorId);
        }
        return query.query(this::queueItem).list();
    }

    @Override
    public Optional<DeliveryReplayRequestView> findReplay(String tenantId, UUID replayRequestId) {
        return jdbc.sql("""
                SELECT replay_request_id, delivery_id, execution_task_id, status, reason,
                       approval_ref, requested_by, result_code, requested_at, started_at, finished_at
                  FROM int_delivery_replay_request
                 WHERE tenant_id=:tenant AND replay_request_id=:id
                """).param("tenant", tenantId).param("id", replayRequestId)
                .query(this::replayRequest).optional();
    }

    @Override
    public DeliveryReplayRequestView registerReplay(NewReplayRequest replay) {
        int inserted = jdbc.sql("""
                INSERT INTO int_delivery_replay_request (
                    replay_request_id, delivery_id, tenant_id, expected_delivery_version,
                    reason, approval_ref, requested_by, execution_task_id, status, requested_at)
                SELECT :replayId, delivery_id, tenant_id, :expectedVersion, :reason,
                       :approvalRef, :requestedBy, :taskId, 'REQUESTED', :requestedAt
                  FROM int_outbound_delivery
                 WHERE tenant_id=:tenant AND delivery_id=:deliveryId
                   AND status='UNKNOWN' AND aggregate_version=:expectedVersion
                ON CONFLICT DO NOTHING
                """).param("replayId", replay.replayRequestId()).param("deliveryId", replay.deliveryId())
                .param("tenant", replay.tenantId()).param("expectedVersion", replay.expectedDeliveryVersion())
                .param("reason", replay.reason()).param("approvalRef", replay.approvalRef())
                .param("requestedBy", replay.requestedBy()).param("taskId", replay.executionTaskId())
                .param("requestedAt", timestamptz(replay.requestedAt())).update();
        if (inserted != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "UNKNOWN OutboundDelivery changed or already has an active replay request");
        }
        return findReplay(replay.tenantId(), replay.replayRequestId())
                .orElseThrow(() -> new IllegalStateException("Delivery replay request missing after insert"));
    }

    @Override
    public boolean isAuthorizedExecutionTask(String tenantId, UUID deliveryId, UUID taskId) {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM int_outbound_delivery delivery
                     WHERE delivery.tenant_id=:tenant AND delivery.delivery_id=:deliveryId
                       AND (delivery.execution_task_id=:taskId OR EXISTS (
                           SELECT 1 FROM int_delivery_replay_request replay
                            WHERE replay.tenant_id=delivery.tenant_id
                              AND replay.delivery_id=delivery.delivery_id
                              AND replay.execution_task_id=:taskId
                              AND replay.status IN ('REQUESTED','EXECUTING','DELIVERED'))))
                """).param("tenant", tenantId).param("deliveryId", deliveryId)
                .param("taskId", taskId).query(Boolean.class).single();
    }

    @Override
    public AttemptStart startAttempt(
            String tenantId, UUID deliveryId, UUID taskId, UUID taskExecutionAttemptId,
            String nonce, LocalDate requestDate, String requestDigest,
            String credentialVersionId, Instant startedAt
    ) {
        jdbc.sql("""
                UPDATE int_delivery_replay_request
                   SET status='EXECUTING', started_at=:startedAt
                 WHERE tenant_id=:tenant AND delivery_id=:deliveryId
                   AND execution_task_id=:taskId AND status='REQUESTED'
                """).param("startedAt", timestamptz(startedAt)).param("tenant", tenantId)
                .param("deliveryId", deliveryId).param("taskId", taskId).update();
        UUID attemptId = UUID.randomUUID();
        int inserted = jdbc.sql("""
                INSERT INTO int_delivery_attempt (
                    delivery_attempt_id, delivery_id, tenant_id, task_execution_attempt_id,
                    attempt_no, nonce, request_date, request_digest, credential_version_id,
                    status, started_at)
                SELECT :attemptId, delivery_id, tenant_id, :taskAttempt, attempt_count + 1, :nonce,
                       :requestDate, :requestDigest, :credentialVersion, 'SENDING', :startedAt
                  FROM int_outbound_delivery
                 WHERE tenant_id=:tenant AND delivery_id=:deliveryId
                   AND ((status='PENDING' AND execution_task_id=:taskId)
                     OR (status='UNKNOWN' AND EXISTS (
                         SELECT 1 FROM int_delivery_replay_request replay
                          WHERE replay.tenant_id=:tenant AND replay.delivery_id=:deliveryId
                            AND replay.execution_task_id=:taskId AND replay.status='EXECUTING')))
                ON CONFLICT DO NOTHING
                """).param("attemptId", attemptId).param("taskAttempt", taskExecutionAttemptId)
                .param("nonce", nonce).param("requestDate", requestDate)
                .param("requestDigest", requestDigest).param("credentialVersion", credentialVersionId)
                .param("startedAt", timestamptz(startedAt)).param("tenant", tenantId)
                .param("deliveryId", deliveryId).param("taskId", taskId).update();
        if (inserted == 1) {
            int transitioned = jdbc.sql("""
                    UPDATE int_outbound_delivery
                       SET status='SENDING', attempt_count=attempt_count+1,
                           aggregate_version=aggregate_version+1
                     WHERE tenant_id=:tenant AND delivery_id=:id AND status IN ('PENDING','UNKNOWN')
                    """).param("tenant", tenantId).param("id", deliveryId).update();
            if (transitioned != 1) {
                throw new IllegalStateException("OutboundDelivery lost PENDING state before network attempt");
            }
        }
        return jdbc.sql("""
                SELECT delivery_attempt_id, status FROM int_delivery_attempt
                 WHERE tenant_id=:tenant AND task_execution_attempt_id=:taskAttempt
                """).param("tenant", tenantId).param("taskAttempt", taskExecutionAttemptId)
                .query((rs, row) -> new AttemptStart(
                        rs.getObject("delivery_attempt_id", UUID.class), inserted == 1, rs.getString("status")))
                .single();
    }

    @Override
    public void recordDelivered(
            String tenantId, UUID deliveryId, UUID taskExecutionAttemptId, int httpStatus,
            String responseObjectRef, String responseDigest, String resultCode,
            String acknowledgementReasonCode, Instant finishedAt
    ) {
        finishAttempt(tenantId, deliveryId, taskExecutionAttemptId, "DELIVERED", httpStatus,
                responseObjectRef, responseDigest, resultCode, finishedAt);
        insertAcknowledgement(tenantId, deliveryId, "ACCEPTED", acknowledgementReasonCode,
                responseObjectRef, responseDigest, finishedAt);
        int updated = jdbc.sql("""
                UPDATE int_outbound_delivery
                   SET status='DELIVERED', delivered_at=:finishedAt,
                       aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND delivery_id=:id AND status='SENDING'
                """).param("finishedAt", timestamptz(finishedAt)).param("tenant", tenantId)
                .param("id", deliveryId).update();
        requireOne(updated, "OutboundDelivery delivery result lost SENDING state");
    }

    @Override
    public void recordRejected(
            String tenantId, UUID deliveryId, UUID taskExecutionAttemptId, int httpStatus,
            String responseObjectRef, String responseDigest, String resultCode,
            String acknowledgementReasonCode, Instant finishedAt
    ) {
        finishAttempt(tenantId, deliveryId, taskExecutionAttemptId, "REJECTED", httpStatus,
                responseObjectRef, responseDigest, resultCode, finishedAt);
        insertAcknowledgement(tenantId, deliveryId, "REJECTED", acknowledgementReasonCode,
                responseObjectRef, responseDigest, finishedAt);
        int updated = jdbc.sql("""
                UPDATE int_outbound_delivery
                   SET status='REJECTED', aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND delivery_id=:id AND status='SENDING'
                """).param("tenant", tenantId).param("id", deliveryId).update();
        requireOne(updated, "OutboundDelivery rejection lost SENDING state");
    }

    @Override
    public void recordUnknown(
            String tenantId, UUID deliveryId, UUID taskExecutionAttemptId, Integer httpStatus,
            String responseObjectRef, String responseDigest, String resultCode, Instant finishedAt
    ) {
        finishAttempt(tenantId, deliveryId, taskExecutionAttemptId, "UNKNOWN", httpStatus,
                responseObjectRef, responseDigest, resultCode, finishedAt);
        int updated = jdbc.sql("""
                UPDATE int_outbound_delivery
                   SET status='UNKNOWN', aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND delivery_id=:id AND status='SENDING'
                """).param("tenant", tenantId).param("id", deliveryId).update();
        requireOne(updated, "OutboundDelivery UNKNOWN result lost SENDING state");
    }

    @Override
    public void recordFailedFinal(
            String tenantId, UUID deliveryId, UUID taskExecutionAttemptId, Integer httpStatus,
            String responseObjectRef, String responseDigest, String resultCode, Instant finishedAt
    ) {
        finishAttempt(tenantId, deliveryId, taskExecutionAttemptId, "FAILED_FINAL", httpStatus,
                responseObjectRef, responseDigest, resultCode, finishedAt);
        int updated = jdbc.sql("""
                UPDATE int_outbound_delivery
                   SET status='FAILED_FINAL', aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND delivery_id=:id AND status='SENDING'
                """).param("tenant", tenantId).param("id", deliveryId).update();
        requireOne(updated, "OutboundDelivery final failure lost SENDING state");
    }

    @Override
    public void failBeforeAttempt(
            String tenantId, UUID deliveryId, UUID taskId, String resultCode, Instant failedAt
    ) {
        int updated = jdbc.sql("""
                UPDATE int_outbound_delivery
                   SET status='FAILED_FINAL', aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND delivery_id=:id AND status='PENDING'
                   AND execution_task_id=:taskId
                """).param("tenant", tenantId).param("id", deliveryId).param("taskId", taskId).update();
        if (updated == 0) {
            updated = jdbc.sql("""
                    UPDATE int_delivery_replay_request
                       SET status='FAILED_FINAL', result_code=:resultCode,
                           finished_at=:failedAt
                     WHERE tenant_id=:tenant AND delivery_id=:id
                       AND execution_task_id=:taskId AND status='REQUESTED'
                    """).param("resultCode", resultCode).param("failedAt", timestamptz(failedAt))
                    .param("tenant", tenantId).param("id", deliveryId).param("taskId", taskId).update();
        }
        requireOne(updated, "OutboundDelivery preflight failure has no authorized execution");
    }

    @Override
    public ManualDispositionView recordManualDisposition(NewManualDisposition disposition) {
        // 状态与版本保持不变：Delivery 仍为 UNKNOWN，以 disposition 唯一约束保证一次性处置。
        Integer matched = jdbc.sql("""
                SELECT count(*)::int FROM int_outbound_delivery
                 WHERE tenant_id=:tenant AND delivery_id=:id
                   AND status='UNKNOWN' AND aggregate_version=:expectedVersion
                   AND NOT EXISTS (
                       SELECT 1 FROM int_delivery_replay_request replay
                        WHERE replay.delivery_id=:id
                          AND replay.status IN ('REQUESTED', 'EXECUTING'))
                """).param("tenant", disposition.tenantId())
                .param("id", disposition.deliveryId())
                .param("expectedVersion", disposition.expectedDeliveryVersion())
                .query(Integer.class).single();
        if (matched == null || matched != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "UNKNOWN OutboundDelivery changed or has an active replay request");
        }
        int inserted = jdbc.sql("""
                INSERT INTO int_delivery_manual_disposition (
                    disposition_id, delivery_id, tenant_id, expected_delivery_version,
                    result, reason, approval_ref, external_ref, evidence_refs_json,
                    requested_by, requested_at
                ) VALUES (
                    :dispositionId, :deliveryId, :tenant, :expectedVersion,
                    :result, :reason, :approvalRef, :externalRef, :evidenceJson,
                    :requestedBy, :requestedAt
                )
                """).param("dispositionId", disposition.dispositionId())
                .param("deliveryId", disposition.deliveryId())
                .param("tenant", disposition.tenantId())
                .param("expectedVersion", disposition.expectedDeliveryVersion())
                .param("result", disposition.result())
                .param("reason", disposition.reason())
                .param("approvalRef", disposition.approvalRef())
                .param("externalRef", disposition.externalRef())
                .param("evidenceJson", disposition.evidenceRefsJson())
                .param("requestedBy", disposition.requestedBy())
                .param("requestedAt", timestamptz(disposition.requestedAt()))
                .update();
        if (inserted == 0) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "Manual disposition already exists for OutboundDelivery");
        }
        List<String> evidenceRefs;
        try {
            evidenceRefs = objectMapper.readValue(
                    disposition.evidenceRefsJson(), new TypeReference<>() { });
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid evidenceRefs JSON", exception);
        }
        return new ManualDispositionView(
                disposition.dispositionId(),
                disposition.deliveryId(),
                disposition.result(),
                disposition.reason(),
                disposition.approvalRef(),
                disposition.externalRef(),
                List.copyOf(evidenceRefs),
                disposition.requestedBy(),
                disposition.requestedAt(),
                disposition.expectedDeliveryVersion());
    }

    @Override
    public boolean hasManualDisposition(String tenantId, UUID deliveryId) {
        Integer count = jdbc.sql("""
                SELECT count(*)::int FROM int_delivery_manual_disposition
                 WHERE tenant_id=:tenant AND delivery_id=:id
                """).param("tenant", tenantId).param("id", deliveryId)
                .query(Integer.class).single();
        return count != null && count > 0;
    }

    @Override
    public void acknowledge(
            String tenantId, UUID deliveryId, UUID clientReviewCaseId, UUID reviewRouteId,
            Instant acknowledgedAt
    ) {
        int updated = jdbc.sql("""
                UPDATE int_outbound_delivery
                   SET status='ACKNOWLEDGED', client_review_case_id=:clientCase,
                       review_route_id=:route, acknowledged_at=:acknowledgedAt,
                       aggregate_version=aggregate_version+1
                 WHERE tenant_id=:tenant AND delivery_id=:id AND status='DELIVERED'
                """).param("clientCase", clientReviewCaseId).param("route", reviewRouteId)
                .param("acknowledgedAt", timestamptz(acknowledgedAt))
                .param("tenant", tenantId).param("id", deliveryId).update();
        if (updated == 0) {
            DeliveryRecord current = find(tenantId, deliveryId)
                    .orElseThrow(() -> new IllegalStateException("OutboundDelivery disappeared"));
            if (!"ACKNOWLEDGED".equals(current.view().status())
                    || !clientReviewCaseId.equals(current.view().clientReviewCaseId())
                    || !reviewRouteId.equals(current.view().reviewRouteId())) {
                throw new IllegalStateException("OutboundDelivery acknowledgement conflicts");
            }
        }
    }

    @Override
    public boolean markSendingUnknownByTaskAttempt(
            String tenantId, UUID taskExecutionAttemptId, String resultCode, Instant finishedAt
    ) {
        Optional<AttemptIdentity> identity = jdbc.sql("""
                SELECT delivery_id, status FROM int_delivery_attempt
                 WHERE tenant_id=:tenant AND task_execution_attempt_id=:taskAttempt
                """).param("tenant", tenantId).param("taskAttempt", taskExecutionAttemptId)
                .query((rs, row) -> new AttemptIdentity(
                        rs.getObject("delivery_id", UUID.class), rs.getString("status"))).optional();
        if (identity.isEmpty() || !"SENDING".equals(identity.get().status())) {
            return false;
        }
        recordUnknown(tenantId, identity.get().deliveryId(), taskExecutionAttemptId,
                null, null, null, resultCode, finishedAt);
        return true;
    }

    private void finishAttempt(
            String tenantId, UUID deliveryId, UUID taskExecutionAttemptId, String status,
            Integer httpStatus, String responseObjectRef, String responseDigest,
            String resultCode, Instant finishedAt
    ) {
        int updated = jdbc.sql("""
                UPDATE int_delivery_attempt
                   SET status=:status, http_status=:httpStatus, response_object_ref=:responseRef,
                       response_digest=:responseDigest, result_code=:resultCode,
                       finished_at=:finishedAt
                 WHERE tenant_id=:tenant AND delivery_id=:deliveryId
                   AND task_execution_attempt_id=:taskAttempt AND status='SENDING'
                """).param("status", status).param("httpStatus", httpStatus, java.sql.Types.INTEGER)
                .param("responseRef", responseObjectRef, java.sql.Types.VARCHAR)
                .param("responseDigest", responseDigest, java.sql.Types.CHAR)
                .param("resultCode", resultCode).param("finishedAt", timestamptz(finishedAt))
                .param("tenant", tenantId).param("deliveryId", deliveryId)
                .param("taskAttempt", taskExecutionAttemptId).update();
        requireOne(updated, "DeliveryAttempt terminal transition lost SENDING state");
        jdbc.sql("""
                UPDATE int_delivery_replay_request replay
                   SET status=:status, result_code=:resultCode, finished_at=:finishedAt
                  FROM tsk_task_execution_attempt task_attempt
                 WHERE task_attempt.attempt_id=:taskAttempt
                   AND replay.tenant_id=:tenant AND replay.delivery_id=:deliveryId
                   AND replay.execution_task_id=task_attempt.task_id AND replay.status='EXECUTING'
                """).param("status", status).param("resultCode", resultCode)
                .param("finishedAt", timestamptz(finishedAt)).param("taskAttempt", taskExecutionAttemptId)
                .param("tenant", tenantId).param("deliveryId", deliveryId).update();
    }

    private void insertAcknowledgement(
            String tenantId, UUID deliveryId, String result, String reasonCode,
            String responseObjectRef, String responseDigest, Instant receivedAt
    ) {
        int inserted = jdbc.sql("""
                INSERT INTO int_external_acknowledgement (
                    acknowledgement_id, delivery_id, tenant_id, acknowledgement_type,
                    result, reason_code, response_object_ref, response_digest,
                    mapping_version_id, received_at)
                SELECT :id, delivery_id, tenant_id, 'BUSINESS', :result, :reason,
                       :responseRef, :responseDigest, mapping_version_id, :receivedAt
                  FROM int_outbound_delivery
                 WHERE tenant_id=:tenant AND delivery_id=:deliveryId
                ON CONFLICT (delivery_id, acknowledgement_type) DO NOTHING
                """).param("id", UUID.randomUUID()).param("result", result).param("reason", reasonCode)
                .param("responseRef", responseObjectRef).param("responseDigest", responseDigest)
                .param("receivedAt", timestamptz(receivedAt)).param("tenant", tenantId)
                .param("deliveryId", deliveryId).update();
        requireOne(inserted, "ExternalAcknowledgement business result already exists");
    }

    private OutboundDeliveryQueueItem queueItem(ResultSet rs, int row) throws SQLException {
        return new OutboundDeliveryQueueItem(
                rs.getObject("delivery_id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("connector_version_id"),
                rs.getString("mapping_version_id"),
                rs.getString("business_message_type"),
                rs.getString("business_key"),
                rs.getObject("source_review_case_id", UUID.class),
                rs.getObject("source_task_id", UUID.class),
                rs.getObject("source_work_order_id", UUID.class),
                rs.getObject("source_snapshot_id", UUID.class),
                rs.getString("external_order_code"),
                rs.getObject("execution_task_id", UUID.class),
                rs.getString("status"),
                rs.getObject("client_review_case_id", UUID.class),
                rs.getObject("review_route_id", UUID.class),
                rs.getLong("aggregate_version"),
                rs.getInt("attempt_count"),
                instant(rs, "created_at"),
                instant(rs, "delivered_at"),
                instant(rs, "acknowledged_at"));
    }

    private DeliveryRecord delivery(ResultSet rs, int row) throws SQLException {
        UUID deliveryId = rs.getObject("delivery_id", UUID.class);
        List<DeliveryAttemptView> attempts = jdbc.sql("""
                SELECT delivery_attempt_id, attempt_no, task_execution_attempt_id,
                       request_date, request_digest, status, http_status, response_digest,
                       result_code, started_at, finished_at
                  FROM int_delivery_attempt WHERE delivery_id=:id ORDER BY attempt_no
                """).param("id", deliveryId).query(this::attempt).list();
        List<ExternalAcknowledgementView> acknowledgements = jdbc.sql("""
                SELECT acknowledgement_id, acknowledgement_type, result, reason_code,
                       response_digest, mapping_version_id, received_at
                  FROM int_external_acknowledgement WHERE delivery_id=:id ORDER BY received_at
                """).param("id", deliveryId).query(this::acknowledgement).list();
        List<DeliveryReplayRequestView> replayRequests = jdbc.sql("""
                SELECT replay_request_id, delivery_id, execution_task_id, status, reason,
                       approval_ref, requested_by, result_code, requested_at, started_at, finished_at
                  FROM int_delivery_replay_request
                 WHERE delivery_id=:id ORDER BY requested_at, replay_request_id
                """).param("id", deliveryId).query(this::replayRequest).list();
        OutboundDeliveryView view = new OutboundDeliveryView(
                deliveryId, rs.getObject("project_id", UUID.class),
                rs.getString("connector_version_id"), rs.getString("mapping_version_id"),
                rs.getString("business_message_type"), rs.getString("business_key"),
                rs.getObject("source_review_case_id", UUID.class),
                rs.getObject("source_task_id", UUID.class),
                rs.getObject("source_work_order_id", UUID.class),
                rs.getObject("source_snapshot_id", UUID.class), rs.getString("source_snapshot_digest"),
                rs.getString("external_order_code"), rs.getString("operator_principal_id"),
                rs.getString("payload_digest"), rs.getString("external_idempotency_key"),
                rs.getObject("execution_task_id", UUID.class), rs.getString("status"),
                rs.getObject("client_review_case_id", UUID.class), rs.getObject("review_route_id", UUID.class),
                rs.getLong("aggregate_version"), instant(rs, "created_at"),
                instant(rs, "delivered_at"), instant(rs, "acknowledged_at"),
                attempts, acknowledgements, replayRequests);
        return new DeliveryRecord(view, rs.getString("operator_display_value"),
                rs.getString("payload_object_ref"), rs.getString("failure_policy_version_id"));
    }

    private DeliveryAttemptView attempt(ResultSet rs, int row) throws SQLException {
        return new DeliveryAttemptView(
                rs.getObject("delivery_attempt_id", UUID.class), rs.getInt("attempt_no"),
                rs.getObject("task_execution_attempt_id", UUID.class), rs.getObject("request_date", LocalDate.class),
                rs.getString("request_digest"), rs.getString("status"),
                (Integer) rs.getObject("http_status"), rs.getString("response_digest"),
                rs.getString("result_code"), instant(rs, "started_at"),
                instant(rs, "finished_at"));
    }

    private ExternalAcknowledgementView acknowledgement(ResultSet rs, int row) throws SQLException {
        return new ExternalAcknowledgementView(
                rs.getObject("acknowledgement_id", UUID.class), rs.getString("acknowledgement_type"),
                rs.getString("result"), rs.getString("reason_code"), rs.getString("response_digest"),
                rs.getString("mapping_version_id"), instant(rs, "received_at"));
    }

    private DeliveryReplayRequestView replayRequest(ResultSet rs, int row) throws SQLException {
        return new DeliveryReplayRequestView(
                rs.getObject("replay_request_id", UUID.class), rs.getObject("delivery_id", UUID.class),
                rs.getObject("execution_task_id", UUID.class), rs.getString("status"),
                rs.getString("reason"), rs.getString("approval_ref"), rs.getString("requested_by"),
                rs.getString("result_code"), instant(rs, "requested_at"),
                instant(rs, "started_at"), instant(rs, "finished_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void requireOne(int updated, String message) {
        if (updated != 1) {
            throw new IllegalStateException(message);
        }
    }

    private record AttemptIdentity(UUID deliveryId, String status) {
    }
}
