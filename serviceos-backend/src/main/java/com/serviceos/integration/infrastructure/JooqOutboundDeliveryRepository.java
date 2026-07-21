package com.serviceos.integration.infrastructure;

import com.serviceos.integration.api.DeliveryAttemptView;
import com.serviceos.integration.api.DeliveryReplayRequestView;
import com.serviceos.integration.api.DeliveryTimelineContext;
import com.serviceos.integration.api.ExternalAcknowledgementView;
import com.serviceos.integration.api.ManualDispositionView;
import com.serviceos.integration.api.OutboundDeliveryQueueItem;
import com.serviceos.integration.api.OutboundDeliveryView;
import com.serviceos.integration.application.OutboundDeliveryRepository;
import com.serviceos.jooq.generated.tables.IntDeliveryAttempt;
import com.serviceos.jooq.generated.tables.IntDeliveryManualDisposition;
import com.serviceos.jooq.generated.tables.IntDeliveryReplayRequest;
import com.serviceos.jooq.generated.tables.IntExternalAcknowledgement;
import com.serviceos.jooq.generated.tables.IntOutboundDelivery;
import com.serviceos.jooq.generated.tables.TskTaskExecutionAttempt;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IntDeliveryAttempt.INT_DELIVERY_ATTEMPT;
import static com.serviceos.jooq.generated.tables.IntDeliveryManualDisposition.INT_DELIVERY_MANUAL_DISPOSITION;
import static com.serviceos.jooq.generated.tables.IntDeliveryReplayRequest.INT_DELIVERY_REPLAY_REQUEST;
import static com.serviceos.jooq.generated.tables.IntExternalAcknowledgement.INT_EXTERNAL_ACKNOWLEDGEMENT;
import static com.serviceos.jooq.generated.tables.IntOutboundDelivery.INT_OUTBOUND_DELIVERY;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionAttempt.TSK_TASK_EXECUTION_ATTEMPT;

/** 使用 PostgreSQL 条件更新保护外部副作用状态，禁止先读后写覆盖并发结果（jOOQ 实现）。 */
@Repository
final class JooqOutboundDeliveryRepository implements OutboundDeliveryRepository {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqOutboundDeliveryRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public Registration register(NewDelivery delivery) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        int inserted = dsl.insertInto(d)
                .set(d.DELIVERY_ID, delivery.deliveryId())
                .set(d.TENANT_ID, delivery.tenantId())
                .set(d.PROJECT_ID, delivery.projectId())
                .set(d.CONNECTOR_VERSION_ID, delivery.connectorVersionId())
                .set(d.MAPPING_VERSION_ID, delivery.mappingVersionId())
                .set(d.BUSINESS_MESSAGE_TYPE, delivery.businessMessageType())
                .set(d.BUSINESS_KEY, delivery.businessKey())
                .set(d.SOURCE_REVIEW_CASE_ID, delivery.sourceReviewCaseId())
                .set(d.SOURCE_TASK_ID, delivery.sourceTaskId())
                .set(d.SOURCE_WORK_ORDER_ID, delivery.sourceWorkOrderId())
                .set(d.SOURCE_SNAPSHOT_ID, delivery.sourceSnapshotId())
                .set(d.SOURCE_SNAPSHOT_DIGEST, delivery.sourceSnapshotDigest())
                .set(d.EXTERNAL_ORDER_CODE, delivery.externalOrderCode())
                .set(d.OPERATOR_PRINCIPAL_ID, delivery.operatorPrincipalId())
                .set(d.OPERATOR_DISPLAY_VALUE, delivery.operatorDisplayValue())
                .set(d.PAYLOAD_OBJECT_REF, delivery.payloadObjectRef())
                .set(d.PAYLOAD_DIGEST, delivery.payloadDigest())
                .set(d.EXTERNAL_IDEMPOTENCY_KEY, delivery.externalIdempotencyKey())
                .set(d.FAILURE_POLICY_VERSION_ID, delivery.failurePolicyVersionId())
                .set(d.STATUS, "PENDING")
                .set(d.CREATED_BY, delivery.createdBy())
                .set(d.CREATED_AT, delivery.createdAt())
                .onConflictDoNothing()
                .execute();
        DeliveryRecord current = findBySourceReview(
                delivery.tenantId(), delivery.sourceReviewCaseId(), delivery.businessMessageType())
                .orElseThrow(() -> new IllegalStateException("OutboundDelivery registration result missing"));
        return new Registration(current, inserted == 1);
    }

    @Override
    public void attachExecutionTask(String tenantId, UUID deliveryId, UUID taskId, Instant updatedAt) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        // 条件更新同时校验 PENDING 状态与执行 Task 归属，影响行数不为 1 时进入幂等校验。
        int updated = dsl.update(d)
                .set(d.EXECUTION_TASK_ID, taskId)
                .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.STATUS.eq("PENDING"))
                .and(d.EXECUTION_TASK_ID.isNull().or(d.EXECUTION_TASK_ID.eq(taskId)))
                .execute();
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
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        return dsl.select(deliveryFields())
                .from(d)
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .fetchOptional(this::delivery);
    }

    @Override
    public Optional<DeliveryTimelineContext> findTimelineContext(String tenantId, UUID deliveryId) {
        // 时间线只需要稳定身份；禁止在此路径加载 attempt/ack 图以免放大跨模块投影成本。
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        return dsl.select(d.DELIVERY_ID, d.PROJECT_ID, d.SOURCE_WORK_ORDER_ID)
                .from(d)
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .fetchOptional(record -> new DeliveryTimelineContext(
                        record.get(d.DELIVERY_ID),
                        record.get(d.PROJECT_ID),
                        record.get(d.SOURCE_WORK_ORDER_ID)));
    }

    @Override
    public Optional<DeliveryRecord> findBySourceReview(
            String tenantId, UUID sourceReviewCaseId, String businessMessageType
    ) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        return dsl.select(deliveryFields())
                .from(d)
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.SOURCE_REVIEW_CASE_ID.eq(sourceReviewCaseId))
                .and(d.BUSINESS_MESSAGE_TYPE.eq(businessMessageType))
                .fetchOptional(this::delivery);
    }

    @Override
    public List<DeliveryRecord> listByWorkOrder(
            String tenantId, UUID projectId, UUID workOrderId, int limit
    ) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        return dsl.select(deliveryFields())
                .from(d)
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.PROJECT_ID.eq(projectId))
                .and(d.SOURCE_WORK_ORDER_ID.eq(workOrderId))
                .orderBy(d.CREATED_AT, d.DELIVERY_ID)
                .limit(limit)
                .fetch(this::delivery);
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
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        Condition condition = d.TENANT_ID.eq(tenantId)
                .and(d.STATUS.eq(status));
        if (!tenantWide) {
            // 非全租户视角且项目集合为空时与原 SQL 的 AND 1 = 0 一致，直接无结果。
            condition = condition.and(projectIds == null || projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : d.PROJECT_ID.in(projectIds));
        }
        if (businessMessageType != null) {
            condition = condition.and(d.BUSINESS_MESSAGE_TYPE.eq(businessMessageType));
        }
        if (sourceWorkOrderId != null) {
            condition = condition.and(d.SOURCE_WORK_ORDER_ID.eq(sourceWorkOrderId));
        }
        if (sourceReviewCaseId != null) {
            condition = condition.and(d.SOURCE_REVIEW_CASE_ID.eq(sourceReviewCaseId));
        }
        if (cursorCreatedAt != null) {
            condition = condition.and(DSL.row(d.CREATED_AT, d.DELIVERY_ID)
                    .gt(cursorCreatedAt, cursorId));
        }
        return dsl.select(
                        d.DELIVERY_ID, d.PROJECT_ID, d.CONNECTOR_VERSION_ID, d.MAPPING_VERSION_ID,
                        d.BUSINESS_MESSAGE_TYPE, d.BUSINESS_KEY, d.SOURCE_REVIEW_CASE_ID,
                        d.SOURCE_TASK_ID, d.SOURCE_WORK_ORDER_ID, d.SOURCE_SNAPSHOT_ID,
                        d.EXTERNAL_ORDER_CODE, d.EXECUTION_TASK_ID, d.STATUS,
                        d.CLIENT_REVIEW_CASE_ID, d.REVIEW_ROUTE_ID, d.AGGREGATE_VERSION,
                        d.ATTEMPT_COUNT, d.CREATED_AT, d.DELIVERED_AT, d.ACKNOWLEDGED_AT)
                .from(d)
                .where(condition)
                .orderBy(d.CREATED_AT, d.DELIVERY_ID)
                .limit(fetchSize)
                .fetch(this::queueItem);
    }

    @Override
    public Optional<DeliveryReplayRequestView> findReplay(String tenantId, UUID replayRequestId) {
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        return dsl.select(replayFields(r))
                .from(r)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.REPLAY_REQUEST_ID.eq(replayRequestId))
                .fetchOptional(this::replayRequest);
    }

    @Override
    public DeliveryReplayRequestView registerReplay(NewReplayRequest replay) {
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        // INSERT ... SELECT：只有 UNKNOWN 且版本未变的 Delivery 才允许登记重放，并发变更直接落空。
        int inserted = dsl.insertInto(r)
                .columns(r.REPLAY_REQUEST_ID, r.DELIVERY_ID, r.TENANT_ID, r.EXPECTED_DELIVERY_VERSION,
                        r.REASON, r.APPROVAL_REF, r.REQUESTED_BY, r.EXECUTION_TASK_ID,
                        r.STATUS, r.REQUESTED_AT)
                .select(dsl.select(
                                DSL.val(replay.replayRequestId()),
                                d.DELIVERY_ID,
                                d.TENANT_ID,
                                DSL.val(replay.expectedDeliveryVersion(), r.EXPECTED_DELIVERY_VERSION),
                                DSL.val(replay.reason(), r.REASON),
                                DSL.val(replay.approvalRef(), r.APPROVAL_REF),
                                DSL.val(replay.requestedBy(), r.REQUESTED_BY),
                                DSL.val(replay.executionTaskId(), r.EXECUTION_TASK_ID),
                                DSL.val("REQUESTED", r.STATUS),
                                DSL.val(replay.requestedAt(), r.REQUESTED_AT))
                        .from(d)
                        .where(d.TENANT_ID.eq(replay.tenantId()))
                        .and(d.DELIVERY_ID.eq(replay.deliveryId()))
                        .and(d.STATUS.eq("UNKNOWN"))
                        .and(d.AGGREGATE_VERSION.eq(replay.expectedDeliveryVersion())))
                .onConflictDoNothing()
                .execute();
        if (inserted != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "UNKNOWN OutboundDelivery changed or already has an active replay request");
        }
        return findReplay(replay.tenantId(), replay.replayRequestId())
                .orElseThrow(() -> new IllegalStateException("Delivery replay request missing after insert"));
    }

    @Override
    public boolean isAuthorizedExecutionTask(String tenantId, UUID deliveryId, UUID taskId) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        return dsl.fetchExists(dsl.selectOne()
                .from(d)
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.EXECUTION_TASK_ID.eq(taskId)
                        .or(DSL.exists(dsl.selectOne()
                                .from(r)
                                .where(r.TENANT_ID.eq(d.TENANT_ID))
                                .and(r.DELIVERY_ID.eq(d.DELIVERY_ID))
                                .and(r.EXECUTION_TASK_ID.eq(taskId))
                                .and(r.STATUS.in("REQUESTED", "EXECUTING", "DELIVERED"))))));
    }

    @Override
    public AttemptStart startAttempt(
            String tenantId, UUID deliveryId, UUID taskId, UUID taskExecutionAttemptId,
            String nonce, LocalDate requestDate, String requestDigest,
            String credentialVersionId, Instant startedAt
    ) {
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        IntDeliveryAttempt a = INT_DELIVERY_ATTEMPT;
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        dsl.update(r)
                .set(r.STATUS, "EXECUTING")
                .set(r.STARTED_AT, startedAt)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.DELIVERY_ID.eq(deliveryId))
                .and(r.EXECUTION_TASK_ID.eq(taskId))
                .and(r.STATUS.eq("REQUESTED"))
                .execute();
        UUID attemptId = UUID.randomUUID();
        // INSERT ... SELECT：attempt_no 取自 Delivery 当前计数，并发重复 attempt 由唯一约束裁决。
        int inserted = dsl.insertInto(a)
                .columns(a.DELIVERY_ATTEMPT_ID, a.DELIVERY_ID, a.TENANT_ID, a.TASK_EXECUTION_ATTEMPT_ID,
                        a.ATTEMPT_NO, a.NONCE, a.REQUEST_DATE, a.REQUEST_DIGEST,
                        a.CREDENTIAL_VERSION_ID, a.STATUS, a.STARTED_AT)
                .select(dsl.select(
                                DSL.val(attemptId, a.DELIVERY_ATTEMPT_ID),
                                d.DELIVERY_ID,
                                d.TENANT_ID,
                                DSL.val(taskExecutionAttemptId, a.TASK_EXECUTION_ATTEMPT_ID),
                                d.ATTEMPT_COUNT.plus(1),
                                DSL.val(nonce, a.NONCE),
                                DSL.val(requestDate, a.REQUEST_DATE),
                                DSL.val(requestDigest, a.REQUEST_DIGEST),
                                DSL.val(credentialVersionId, a.CREDENTIAL_VERSION_ID),
                                DSL.val("SENDING", a.STATUS),
                                DSL.val(startedAt, a.STARTED_AT))
                        .from(d)
                        .where(d.TENANT_ID.eq(tenantId))
                        .and(d.DELIVERY_ID.eq(deliveryId))
                        .and(d.STATUS.eq("PENDING").and(d.EXECUTION_TASK_ID.eq(taskId))
                                .or(d.STATUS.eq("UNKNOWN").and(DSL.exists(dsl.selectOne()
                                        .from(r)
                                        .where(r.TENANT_ID.eq(tenantId))
                                        .and(r.DELIVERY_ID.eq(deliveryId))
                                        .and(r.EXECUTION_TASK_ID.eq(taskId))
                                        .and(r.STATUS.eq("EXECUTING")))))))
                .onConflictDoNothing()
                .execute();
        if (inserted == 1) {
            int transitioned = dsl.update(d)
                    .set(d.STATUS, "SENDING")
                    .set(d.ATTEMPT_COUNT, d.ATTEMPT_COUNT.plus(1))
                    .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                    .where(d.TENANT_ID.eq(tenantId))
                    .and(d.DELIVERY_ID.eq(deliveryId))
                    .and(d.STATUS.in("PENDING", "UNKNOWN"))
                    .execute();
            if (transitioned != 1) {
                throw new IllegalStateException("OutboundDelivery lost PENDING state before network attempt");
            }
        }
        return dsl.select(a.DELIVERY_ATTEMPT_ID, a.STATUS)
                .from(a)
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.TASK_EXECUTION_ATTEMPT_ID.eq(taskExecutionAttemptId))
                .fetchSingle(record -> new AttemptStart(
                        record.get(a.DELIVERY_ATTEMPT_ID), inserted == 1, record.get(a.STATUS)));
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
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        int updated = dsl.update(d)
                .set(d.STATUS, "DELIVERED")
                .set(d.DELIVERED_AT, finishedAt)
                .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.STATUS.eq("SENDING"))
                .execute();
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
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        int updated = dsl.update(d)
                .set(d.STATUS, "REJECTED")
                .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.STATUS.eq("SENDING"))
                .execute();
        requireOne(updated, "OutboundDelivery rejection lost SENDING state");
    }

    @Override
    public void recordUnknown(
            String tenantId, UUID deliveryId, UUID taskExecutionAttemptId, Integer httpStatus,
            String responseObjectRef, String responseDigest, String resultCode, Instant finishedAt
    ) {
        finishAttempt(tenantId, deliveryId, taskExecutionAttemptId, "UNKNOWN", httpStatus,
                responseObjectRef, responseDigest, resultCode, finishedAt);
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        int updated = dsl.update(d)
                .set(d.STATUS, "UNKNOWN")
                .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.STATUS.eq("SENDING"))
                .execute();
        requireOne(updated, "OutboundDelivery UNKNOWN result lost SENDING state");
    }

    @Override
    public void recordFailedFinal(
            String tenantId, UUID deliveryId, UUID taskExecutionAttemptId, Integer httpStatus,
            String responseObjectRef, String responseDigest, String resultCode, Instant finishedAt
    ) {
        finishAttempt(tenantId, deliveryId, taskExecutionAttemptId, "FAILED_FINAL", httpStatus,
                responseObjectRef, responseDigest, resultCode, finishedAt);
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        int updated = dsl.update(d)
                .set(d.STATUS, "FAILED_FINAL")
                .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.STATUS.eq("SENDING"))
                .execute();
        requireOne(updated, "OutboundDelivery final failure lost SENDING state");
    }

    @Override
    public void failBeforeAttempt(
            String tenantId, UUID deliveryId, UUID taskId, String resultCode, Instant failedAt
    ) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        int updated = dsl.update(d)
                .set(d.STATUS, "FAILED_FINAL")
                .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.STATUS.eq("PENDING"))
                .and(d.EXECUTION_TASK_ID.eq(taskId))
                .execute();
        if (updated == 0) {
            IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
            updated = dsl.update(r)
                    .set(r.STATUS, "FAILED_FINAL")
                    .set(r.RESULT_CODE, resultCode)
                    .set(r.FINISHED_AT, failedAt)
                    .where(r.TENANT_ID.eq(tenantId))
                    .and(r.DELIVERY_ID.eq(deliveryId))
                    .and(r.EXECUTION_TASK_ID.eq(taskId))
                    .and(r.STATUS.eq("REQUESTED"))
                    .execute();
        }
        requireOne(updated, "OutboundDelivery preflight failure has no authorized execution");
    }

    @Override
    public ManualDispositionView recordManualDisposition(NewManualDisposition disposition) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        IntDeliveryManualDisposition m = INT_DELIVERY_MANUAL_DISPOSITION;
        // 状态与版本保持不变：Delivery 仍为 UNKNOWN，以 disposition 唯一约束保证一次性处置。
        Condition eligible = d.TENANT_ID.eq(disposition.tenantId())
                .and(d.DELIVERY_ID.eq(disposition.deliveryId()))
                .and(d.STATUS.eq("UNKNOWN"))
                .and(d.AGGREGATE_VERSION.eq(disposition.expectedDeliveryVersion()))
                .and(DSL.notExists(dsl.selectOne()
                        .from(r)
                        .where(r.DELIVERY_ID.eq(disposition.deliveryId()))
                        .and(r.STATUS.in("REQUESTED", "EXECUTING"))));
        int matched = dsl.selectCount().from(d).where(eligible).fetchSingle().value1();
        if (matched != 1) {
            throw new BusinessProblem(ProblemCode.VERSION_CONFLICT,
                    "UNKNOWN OutboundDelivery changed or has an active replay request");
        }
        int inserted = dsl.insertInto(m)
                .set(m.DISPOSITION_ID, disposition.dispositionId())
                .set(m.DELIVERY_ID, disposition.deliveryId())
                .set(m.TENANT_ID, disposition.tenantId())
                .set(m.EXPECTED_DELIVERY_VERSION, disposition.expectedDeliveryVersion())
                .set(m.RESULT, disposition.result())
                .set(m.REASON, disposition.reason())
                .set(m.APPROVAL_REF, disposition.approvalRef())
                .set(m.EXTERNAL_REF, disposition.externalRef())
                .set(m.EVIDENCE_REFS_JSON, disposition.evidenceRefsJson())
                .set(m.REQUESTED_BY, disposition.requestedBy())
                .set(m.REQUESTED_AT, disposition.requestedAt())
                .execute();
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
        IntDeliveryManualDisposition m = INT_DELIVERY_MANUAL_DISPOSITION;
        return dsl.fetchExists(dsl.selectOne()
                .from(m)
                .where(m.TENANT_ID.eq(tenantId))
                .and(m.DELIVERY_ID.eq(deliveryId)));
    }

    @Override
    public void acknowledge(
            String tenantId, UUID deliveryId, UUID clientReviewCaseId, UUID reviewRouteId,
            Instant acknowledgedAt
    ) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        int updated = dsl.update(d)
                .set(d.STATUS, "ACKNOWLEDGED")
                .set(d.CLIENT_REVIEW_CASE_ID, clientReviewCaseId)
                .set(d.REVIEW_ROUTE_ID, reviewRouteId)
                .set(d.ACKNOWLEDGED_AT, acknowledgedAt)
                .set(d.AGGREGATE_VERSION, d.AGGREGATE_VERSION.plus(1))
                .where(d.TENANT_ID.eq(tenantId))
                .and(d.DELIVERY_ID.eq(deliveryId))
                .and(d.STATUS.eq("DELIVERED"))
                .execute();
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
        IntDeliveryAttempt a = INT_DELIVERY_ATTEMPT;
        Optional<AttemptIdentity> identity = dsl.select(a.DELIVERY_ID, a.STATUS)
                .from(a)
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.TASK_EXECUTION_ATTEMPT_ID.eq(taskExecutionAttemptId))
                .fetchOptional(record -> new AttemptIdentity(
                        record.get(a.DELIVERY_ID), record.get(a.STATUS)));
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
        IntDeliveryAttempt a = INT_DELIVERY_ATTEMPT;
        int updated = dsl.update(a)
                .set(a.STATUS, status)
                .set(a.HTTP_STATUS, httpStatus)
                .set(a.RESPONSE_OBJECT_REF, responseObjectRef)
                .set(a.RESPONSE_DIGEST, responseDigest)
                .set(a.RESULT_CODE, resultCode)
                .set(a.FINISHED_AT, finishedAt)
                .where(a.TENANT_ID.eq(tenantId))
                .and(a.DELIVERY_ID.eq(deliveryId))
                .and(a.TASK_EXECUTION_ATTEMPT_ID.eq(taskExecutionAttemptId))
                .and(a.STATUS.eq("SENDING"))
                .execute();
        requireOne(updated, "DeliveryAttempt terminal transition lost SENDING state");
        // 同步终结处于 EXECUTING 的重放请求：以 attempt 行定位所属 Task，UPDATE ... FROM 单语句完成。
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        TskTaskExecutionAttempt taskAttempt = TSK_TASK_EXECUTION_ATTEMPT;
        dsl.update(r)
                .set(r.STATUS, status)
                .set(r.RESULT_CODE, resultCode)
                .set(r.FINISHED_AT, finishedAt)
                .from(taskAttempt)
                .where(taskAttempt.ATTEMPT_ID.eq(taskExecutionAttemptId))
                .and(r.TENANT_ID.eq(tenantId))
                .and(r.DELIVERY_ID.eq(deliveryId))
                .and(r.EXECUTION_TASK_ID.eq(taskAttempt.TASK_ID))
                .and(r.STATUS.eq("EXECUTING"))
                .execute();
    }

    private void insertAcknowledgement(
            String tenantId, UUID deliveryId, String result, String reasonCode,
            String responseObjectRef, String responseDigest, Instant receivedAt
    ) {
        IntExternalAcknowledgement ack = INT_EXTERNAL_ACKNOWLEDGEMENT;
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        // INSERT ... SELECT + ON CONFLICT：每个 Delivery 每种确认类型只允许一条业务结果。
        int inserted = dsl.insertInto(ack)
                .columns(ack.ACKNOWLEDGEMENT_ID, ack.DELIVERY_ID, ack.TENANT_ID,
                        ack.ACKNOWLEDGEMENT_TYPE, ack.RESULT, ack.REASON_CODE,
                        ack.RESPONSE_OBJECT_REF, ack.RESPONSE_DIGEST,
                        ack.MAPPING_VERSION_ID, ack.RECEIVED_AT)
                .select(dsl.select(
                                DSL.val(UUID.randomUUID(), ack.ACKNOWLEDGEMENT_ID),
                                d.DELIVERY_ID,
                                d.TENANT_ID,
                                DSL.val("BUSINESS", ack.ACKNOWLEDGEMENT_TYPE),
                                DSL.val(result, ack.RESULT),
                                DSL.val(reasonCode, ack.REASON_CODE),
                                DSL.val(responseObjectRef, ack.RESPONSE_OBJECT_REF),
                                DSL.val(responseDigest, ack.RESPONSE_DIGEST),
                                d.MAPPING_VERSION_ID,
                                DSL.val(receivedAt, ack.RECEIVED_AT))
                        .from(d)
                        .where(d.TENANT_ID.eq(tenantId))
                        .and(d.DELIVERY_ID.eq(deliveryId)))
                .onConflict(ack.DELIVERY_ID, ack.ACKNOWLEDGEMENT_TYPE)
                .doNothing()
                .execute();
        requireOne(inserted, "ExternalAcknowledgement business result already exists");
    }

    private OutboundDeliveryQueueItem queueItem(Record record) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        return new OutboundDeliveryQueueItem(
                record.get(d.DELIVERY_ID),
                record.get(d.PROJECT_ID),
                record.get(d.CONNECTOR_VERSION_ID),
                record.get(d.MAPPING_VERSION_ID),
                record.get(d.BUSINESS_MESSAGE_TYPE),
                record.get(d.BUSINESS_KEY),
                record.get(d.SOURCE_REVIEW_CASE_ID),
                record.get(d.SOURCE_TASK_ID),
                record.get(d.SOURCE_WORK_ORDER_ID),
                record.get(d.SOURCE_SNAPSHOT_ID),
                record.get(d.EXTERNAL_ORDER_CODE),
                record.get(d.EXECUTION_TASK_ID),
                record.get(d.STATUS),
                record.get(d.CLIENT_REVIEW_CASE_ID),
                record.get(d.REVIEW_ROUTE_ID),
                record.get(d.AGGREGATE_VERSION),
                record.get(d.ATTEMPT_COUNT),
                record.get(d.CREATED_AT),
                record.get(d.DELIVERED_AT),
                record.get(d.ACKNOWLEDGED_AT));
    }

    private DeliveryRecord delivery(Record record) {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        IntDeliveryAttempt a = INT_DELIVERY_ATTEMPT;
        IntExternalAcknowledgement ack = INT_EXTERNAL_ACKNOWLEDGEMENT;
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        UUID deliveryId = record.get(d.DELIVERY_ID);
        List<DeliveryAttemptView> attempts = dsl.select(
                        a.DELIVERY_ATTEMPT_ID, a.ATTEMPT_NO, a.TASK_EXECUTION_ATTEMPT_ID,
                        a.REQUEST_DATE, a.REQUEST_DIGEST, a.STATUS, a.HTTP_STATUS,
                        a.RESPONSE_DIGEST, a.RESULT_CODE, a.STARTED_AT, a.FINISHED_AT)
                .from(a)
                .where(a.DELIVERY_ID.eq(deliveryId))
                .orderBy(a.ATTEMPT_NO)
                .fetch(this::attempt);
        List<ExternalAcknowledgementView> acknowledgements = dsl.select(
                        ack.ACKNOWLEDGEMENT_ID, ack.ACKNOWLEDGEMENT_TYPE, ack.RESULT,
                        ack.REASON_CODE, ack.RESPONSE_DIGEST, ack.MAPPING_VERSION_ID, ack.RECEIVED_AT)
                .from(ack)
                .where(ack.DELIVERY_ID.eq(deliveryId))
                .orderBy(ack.RECEIVED_AT)
                .fetch(this::acknowledgement);
        List<DeliveryReplayRequestView> replayRequests = dsl.select(replayFields(r))
                .from(r)
                .where(r.DELIVERY_ID.eq(deliveryId))
                .orderBy(r.REQUESTED_AT, r.REPLAY_REQUEST_ID)
                .fetch(this::replayRequest);
        OutboundDeliveryView view = new OutboundDeliveryView(
                deliveryId, record.get(d.PROJECT_ID),
                record.get(d.CONNECTOR_VERSION_ID), record.get(d.MAPPING_VERSION_ID),
                record.get(d.BUSINESS_MESSAGE_TYPE), record.get(d.BUSINESS_KEY),
                record.get(d.SOURCE_REVIEW_CASE_ID),
                record.get(d.SOURCE_TASK_ID),
                record.get(d.SOURCE_WORK_ORDER_ID),
                record.get(d.SOURCE_SNAPSHOT_ID), record.get(d.SOURCE_SNAPSHOT_DIGEST),
                record.get(d.EXTERNAL_ORDER_CODE), record.get(d.OPERATOR_PRINCIPAL_ID),
                record.get(d.PAYLOAD_DIGEST), record.get(d.EXTERNAL_IDEMPOTENCY_KEY),
                record.get(d.EXECUTION_TASK_ID), record.get(d.STATUS),
                record.get(d.CLIENT_REVIEW_CASE_ID), record.get(d.REVIEW_ROUTE_ID),
                record.get(d.AGGREGATE_VERSION), record.get(d.CREATED_AT),
                record.get(d.DELIVERED_AT), record.get(d.ACKNOWLEDGED_AT),
                attempts, acknowledgements, replayRequests);
        return new DeliveryRecord(view, record.get(d.OPERATOR_DISPLAY_VALUE),
                record.get(d.PAYLOAD_OBJECT_REF), record.get(d.FAILURE_POLICY_VERSION_ID));
    }

    private DeliveryAttemptView attempt(Record record) {
        IntDeliveryAttempt a = INT_DELIVERY_ATTEMPT;
        return new DeliveryAttemptView(
                record.get(a.DELIVERY_ATTEMPT_ID), record.get(a.ATTEMPT_NO),
                record.get(a.TASK_EXECUTION_ATTEMPT_ID), record.get(a.REQUEST_DATE),
                record.get(a.REQUEST_DIGEST), record.get(a.STATUS),
                record.get(a.HTTP_STATUS), record.get(a.RESPONSE_DIGEST),
                record.get(a.RESULT_CODE), record.get(a.STARTED_AT), record.get(a.FINISHED_AT));
    }

    private ExternalAcknowledgementView acknowledgement(Record record) {
        IntExternalAcknowledgement ack = INT_EXTERNAL_ACKNOWLEDGEMENT;
        return new ExternalAcknowledgementView(
                record.get(ack.ACKNOWLEDGEMENT_ID), record.get(ack.ACKNOWLEDGEMENT_TYPE),
                record.get(ack.RESULT), record.get(ack.REASON_CODE), record.get(ack.RESPONSE_DIGEST),
                record.get(ack.MAPPING_VERSION_ID), record.get(ack.RECEIVED_AT));
    }

    private DeliveryReplayRequestView replayRequest(Record record) {
        IntDeliveryReplayRequest r = INT_DELIVERY_REPLAY_REQUEST;
        return new DeliveryReplayRequestView(
                record.get(r.REPLAY_REQUEST_ID), record.get(r.DELIVERY_ID),
                record.get(r.EXECUTION_TASK_ID), record.get(r.STATUS),
                record.get(r.REASON), record.get(r.APPROVAL_REF), record.get(r.REQUESTED_BY),
                record.get(r.RESULT_CODE), record.get(r.REQUESTED_AT),
                record.get(r.STARTED_AT), record.get(r.FINISHED_AT));
    }

    private static List<SelectField<?>> deliveryFields() {
        IntOutboundDelivery d = INT_OUTBOUND_DELIVERY;
        return List.of(
                d.DELIVERY_ID, d.PROJECT_ID, d.CONNECTOR_VERSION_ID, d.MAPPING_VERSION_ID,
                d.BUSINESS_MESSAGE_TYPE, d.BUSINESS_KEY, d.SOURCE_REVIEW_CASE_ID, d.SOURCE_TASK_ID,
                d.SOURCE_WORK_ORDER_ID, d.SOURCE_SNAPSHOT_ID, d.SOURCE_SNAPSHOT_DIGEST,
                d.EXTERNAL_ORDER_CODE, d.OPERATOR_PRINCIPAL_ID, d.OPERATOR_DISPLAY_VALUE,
                d.PAYLOAD_OBJECT_REF, d.PAYLOAD_DIGEST, d.EXTERNAL_IDEMPOTENCY_KEY,
                d.FAILURE_POLICY_VERSION_ID, d.EXECUTION_TASK_ID, d.STATUS,
                d.CLIENT_REVIEW_CASE_ID, d.REVIEW_ROUTE_ID, d.AGGREGATE_VERSION,
                d.CREATED_AT, d.DELIVERED_AT, d.ACKNOWLEDGED_AT);
    }

    private static List<SelectField<?>> replayFields(IntDeliveryReplayRequest r) {
        return List.of(
                r.REPLAY_REQUEST_ID, r.DELIVERY_ID, r.EXECUTION_TASK_ID, r.STATUS, r.REASON,
                r.APPROVAL_REF, r.REQUESTED_BY, r.RESULT_CODE, r.REQUESTED_AT,
                r.STARTED_AT, r.FINISHED_AT);
    }

    private static void requireOne(int updated, String message) {
        if (updated != 1) {
            throw new IllegalStateException(message);
        }
    }

    private record AttemptIdentity(UUID deliveryId, String status) {
    }
}
