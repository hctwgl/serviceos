package com.serviceos.integration.infrastructure;

import com.serviceos.integration.api.CanonicalMessageView;
import com.serviceos.integration.api.ExternalReviewRouteView;
import com.serviceos.integration.api.InboundEnvelopeQueueItem;
import com.serviceos.integration.api.InboundEnvelopeView;
import com.serviceos.integration.application.InboundMessageRepository;
import com.serviceos.jooq.generated.tables.IntCanonicalMessage;
import com.serviceos.jooq.generated.tables.IntExternalReviewRoute;
import com.serviceos.jooq.generated.tables.IntInboundEnvelope;
import com.serviceos.jooq.generated.tables.IntInboundItemResult;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.IntCanonicalMessage.INT_CANONICAL_MESSAGE;
import static com.serviceos.jooq.generated.tables.IntExternalReviewRoute.INT_EXTERNAL_REVIEW_ROUTE;
import static com.serviceos.jooq.generated.tables.IntInboundEnvelope.INT_INBOUND_ENVELOPE;
import static com.serviceos.jooq.generated.tables.IntInboundItemResult.INT_INBOUND_ITEM_RESULT;

/**
 * 入站幂等内核使用 PostgreSQL ON CONFLICT，而不是先查后插（jOOQ 实现）。
 *
 * <p>Envelope transport key 和 Canonical business key 的并发判定必须
 * 依赖唯一约束，并检查每次状态更新的影响行数。</p>
 */
@Repository
final class JooqInboundMessageRepository implements InboundMessageRepository {
    private final DSLContext dsl;

    JooqInboundMessageRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EnvelopeRegistration registerEnvelope(NewInboundEnvelope envelope) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        int inserted = dsl.insertInto(e)
                .set(e.INBOUND_ENVELOPE_ID, envelope.inboundEnvelopeId())
                .set(e.TENANT_ID, envelope.tenantId())
                .set(e.CONNECTOR_VERSION_ID, envelope.connectorVersionId())
                .set(e.MESSAGE_TYPE, envelope.messageType())
                .set(e.TRANSPORT_DEDUP_KEY, envelope.transportDedupKey())
                .set(e.EXTERNAL_MESSAGE_ID, envelope.externalMessageId())
                .set(e.RECEIVED_AT, envelope.receivedAt())
                .set(e.RAW_PAYLOAD_OBJECT_REF, envelope.rawPayloadObjectRef())
                .set(e.RAW_PAYLOAD_DIGEST, envelope.rawPayloadDigest())
                .set(e.SIGNATURE_STATUS, "VALID")
                .set(e.PROCESSING_STATUS, "RECEIVED")
                .set(e.CORRELATION_ID, envelope.correlationId())
                .onConflict(e.TENANT_ID, e.CONNECTOR_VERSION_ID, e.TRANSPORT_DEDUP_KEY)
                .doNothing()
                .execute();
        InboundEnvelopeRecord current = dsl.select(envelopeFields())
                .from(e)
                .where(e.TENANT_ID.eq(envelope.tenantId()))
                .and(e.CONNECTOR_VERSION_ID.eq(envelope.connectorVersionId()))
                .and(e.TRANSPORT_DEDUP_KEY.eq(envelope.transportDedupKey()))
                .fetchSingle(this::envelope);
        return new EnvelopeRegistration(current, inserted == 1);
    }

    @Override
    public Optional<InboundEnvelopeRecord> findEnvelope(String tenantId, UUID envelopeId) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        return dsl.select(envelopeFields())
                .from(e)
                .where(e.TENANT_ID.eq(tenantId))
                .and(e.INBOUND_ENVELOPE_ID.eq(envelopeId))
                .fetchOptional(this::envelope);
    }

    @Override
    public boolean rejectEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            String resultCode,
            Instant completedAt
    ) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        // 条件更新带原状态 RECEIVED：并发重复拒绝只有一方生效，其余进入下方幂等校验。
        int updated = dsl.update(e)
                .set(e.PROJECT_ID, projectId)
                .set(e.CANONICAL_PAYLOAD_DIGEST, canonicalPayloadDigest)
                .set(e.MAPPING_VERSION_ID, mappingVersionId)
                .set(e.PROCESSING_STATUS, "REJECTED")
                .set(e.RESULT_CODE, resultCode)
                .set(e.COMPLETED_AT, completedAt)
                .where(e.TENANT_ID.eq(tenantId))
                .and(e.INBOUND_ENVELOPE_ID.eq(envelopeId))
                .and(e.PROCESSING_STATUS.eq("RECEIVED"))
                .execute();
        if (updated == 1) {
            return true;
        }
        InboundEnvelopeRecord current = findEnvelope(tenantId, envelopeId)
                .orElseThrow(() -> new IllegalStateException("InboundEnvelope disappeared"));
        if (!"REJECTED".equals(current.view().processingStatus())
                || !resultCode.equals(current.view().resultCode())) {
            throw new IllegalStateException("InboundEnvelope rejection conflicts with existing result");
        }
        return false;
    }

    @Override
    public CanonicalRegistration registerCanonical(NewCanonicalMessage message) {
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        int inserted = dsl.insertInto(c)
                .set(c.CANONICAL_MESSAGE_ID, message.canonicalMessageId())
                .set(c.TENANT_ID, message.tenantId())
                .set(c.PROJECT_ID, message.projectId())
                .set(c.CONNECTOR_VERSION_ID, message.connectorVersionId())
                .set(c.MESSAGE_TYPE, message.messageType())
                .set(c.BUSINESS_KEY, message.businessKey())
                .set(c.PAYLOAD_OBJECT_REF, message.payloadObjectRef())
                .set(c.PAYLOAD_DIGEST, message.payloadDigest())
                .set(c.MAPPING_VERSION_ID, message.mappingVersionId())
                .set(c.PROCESSING_STATUS, "PROCESSING")
                .set(c.SOURCE_ENVELOPE_ID, message.sourceEnvelopeId())
                .set(c.CREATED_AT, message.createdAt())
                .onConflict(c.TENANT_ID, c.CONNECTOR_VERSION_ID, c.MESSAGE_TYPE, c.BUSINESS_KEY)
                .doNothing()
                .execute();
        CanonicalMessageRecord current = dsl.select(canonicalFields())
                .from(c)
                .where(c.TENANT_ID.eq(message.tenantId()))
                .and(c.CONNECTOR_VERSION_ID.eq(message.connectorVersionId()))
                .and(c.MESSAGE_TYPE.eq(message.messageType()))
                .and(c.BUSINESS_KEY.eq(message.businessKey()))
                .fetchSingle(this::canonical);
        return new CanonicalRegistration(current, inserted == 1);
    }

    @Override
    public void completeCanonical(
            String tenantId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant processedAt
    ) {
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        int updated = dsl.update(c)
                .set(c.PROCESSING_STATUS, "COMPLETED")
                .set(c.RESULT_CODE, resultCode)
                .set(c.RESULT_TYPE, resultType)
                .set(c.RESULT_ID, resultId)
                .set(c.PROCESSED_AT, processedAt)
                .where(c.TENANT_ID.eq(tenantId))
                .and(c.CANONICAL_MESSAGE_ID.eq(canonicalMessageId))
                .and(c.PROCESSING_STATUS.eq("PROCESSING"))
                .execute();
        if (updated != 1) {
            throw new IllegalStateException("CanonicalMessage completion lost its PROCESSING state");
        }
    }

    @Override
    public void completeEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            UUID canonicalMessageId,
            String resultCode,
            String resultType,
            String resultId,
            Instant completedAt
    ) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        int updated = dsl.update(e)
                .set(e.PROJECT_ID, projectId)
                .set(e.CANONICAL_PAYLOAD_DIGEST, canonicalPayloadDigest)
                .set(e.MAPPING_VERSION_ID, mappingVersionId)
                .set(e.CANONICAL_MESSAGE_ID, canonicalMessageId)
                .set(e.PROCESSING_STATUS, "COMPLETED")
                .set(e.RESULT_CODE, resultCode)
                .set(e.RESULT_TYPE, resultType)
                .set(e.RESULT_ID, resultId)
                .set(e.COMPLETED_AT, completedAt)
                .where(e.TENANT_ID.eq(tenantId))
                .and(e.INBOUND_ENVELOPE_ID.eq(envelopeId))
                .and(e.PROCESSING_STATUS.eq("RECEIVED"))
                .execute();
        if (updated == 0) {
            InboundEnvelopeRecord current = findEnvelope(tenantId, envelopeId)
                    .orElseThrow(() -> new IllegalStateException("InboundEnvelope disappeared"));
            if (!"COMPLETED".equals(current.view().processingStatus())
                    || !canonicalMessageId.equals(current.view().canonicalMessageId())) {
                throw new IllegalStateException("InboundEnvelope completion conflicts with existing result");
            }
        }
    }

    @Override
    public Optional<CanonicalMessageRecord> findCanonical(String tenantId, UUID canonicalMessageId) {
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        return dsl.select(canonicalFields())
                .from(c)
                .where(c.TENANT_ID.eq(tenantId))
                .and(c.CANONICAL_MESSAGE_ID.eq(canonicalMessageId))
                .fetchOptional(this::canonical);
    }

    @Override
    public Optional<CanonicalMessageRecord> findCanonicalByBusinessKey(
            String tenantId,
            String connectorVersionId,
            String messageType,
            String businessKey
    ) {
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        return dsl.select(canonicalFields())
                .from(c)
                .where(c.TENANT_ID.eq(tenantId))
                .and(c.CONNECTOR_VERSION_ID.eq(connectorVersionId))
                .and(c.MESSAGE_TYPE.eq(messageType))
                .and(c.BUSINESS_KEY.eq(businessKey))
                .fetchOptional(this::canonical);
    }

    @Override
    public Optional<CanonicalMessageRecord> findCanonicalByResult(
            String tenantId,
            String connectorVersionId,
            String messageType,
            String resultType,
            String resultId
    ) {
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        return dsl.select(canonicalFields())
                .from(c)
                .where(c.TENANT_ID.eq(tenantId))
                .and(c.CONNECTOR_VERSION_ID.eq(connectorVersionId))
                .and(c.MESSAGE_TYPE.eq(messageType))
                .and(c.RESULT_TYPE.eq(resultType))
                .and(c.RESULT_ID.eq(resultId))
                .fetchOptional(this::canonical);
    }

    @Override
    public List<InboundEnvelopeRecord> listEnvelopesByWorkOrder(
            String tenantId, UUID projectId, UUID workOrderId, int limit
    ) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        return dsl.select(envelopeFields())
                .from(e)
                .where(e.TENANT_ID.eq(tenantId))
                .and(e.PROJECT_ID.eq(projectId))
                .and(e.INBOUND_ENVELOPE_ID.in(dsl.select(c.SOURCE_ENVELOPE_ID)
                        .from(c)
                        .where(c.TENANT_ID.eq(tenantId))
                        .and(c.PROJECT_ID.eq(projectId))
                        .and(c.RESULT_TYPE.eq("WORK_ORDER"))
                        .and(c.RESULT_ID.eq(workOrderId.toString()))))
                .orderBy(e.RECEIVED_AT, e.INBOUND_ENVELOPE_ID)
                .limit(limit)
                .fetch(this::envelope);
    }

    @Override
    public List<InboundEnvelopeQueueItem> findQueuePage(
            String tenantId,
            boolean tenantWide,
            List<UUID> projectIds,
            String processingStatus,
            String messageType,
            String resultType,
            String resultId,
            UUID canonicalMessageId,
            Instant cursorReceivedAt,
            UUID cursorId,
            int fetchSize
    ) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        // M158：始终排除 null project；null-project 可见性仍属草案。
        Condition condition = e.TENANT_ID.eq(tenantId)
                .and(e.PROJECT_ID.isNotNull())
                .and(e.PROCESSING_STATUS.eq(processingStatus));
        if (!tenantWide) {
            // 非全租户视角且项目集合为空时与原 SQL 的 AND 1 = 0 一致，直接无结果。
            condition = condition.and(projectIds == null || projectIds.isEmpty()
                    ? DSL.falseCondition()
                    : e.PROJECT_ID.in(projectIds));
        }
        if (messageType != null) {
            condition = condition.and(e.MESSAGE_TYPE.eq(messageType));
        }
        if (resultType != null) {
            condition = condition.and(e.RESULT_TYPE.eq(resultType));
        }
        if (resultId != null) {
            condition = condition.and(e.RESULT_ID.eq(resultId));
        }
        if (canonicalMessageId != null) {
            condition = condition.and(e.CANONICAL_MESSAGE_ID.eq(canonicalMessageId));
        }
        if (cursorReceivedAt != null) {
            condition = condition.and(DSL.row(e.RECEIVED_AT, e.INBOUND_ENVELOPE_ID)
                    .lt(cursorReceivedAt, cursorId));
        }
        return dsl.select(
                        e.INBOUND_ENVELOPE_ID, e.PROJECT_ID, e.CONNECTOR_VERSION_ID, e.MESSAGE_TYPE,
                        e.EXTERNAL_MESSAGE_ID, e.SIGNATURE_STATUS, e.PROCESSING_STATUS,
                        e.MAPPING_VERSION_ID, e.CANONICAL_MESSAGE_ID, e.RESULT_CODE, e.RESULT_TYPE,
                        e.RESULT_ID, e.RECEIVED_AT, e.COMPLETED_AT, e.CORRELATION_ID)
                .from(e)
                .where(condition)
                .orderBy(e.RECEIVED_AT.desc(), e.INBOUND_ENVELOPE_ID.desc())
                .limit(fetchSize)
                .fetch(this::queueItem);
    }

    @Override
    public ExternalReviewRouteRegistration registerExternalReviewRoute(NewExternalReviewRoute route) {
        IntExternalReviewRoute r = INT_EXTERNAL_REVIEW_ROUTE;
        int inserted = dsl.insertInto(r)
                .set(r.REVIEW_ROUTE_ID, route.reviewRouteId())
                .set(r.TENANT_ID, route.tenantId())
                .set(r.PROJECT_ID, route.projectId())
                .set(r.CONNECTOR_VERSION_ID, route.connectorVersionId())
                .set(r.EXTERNAL_ORDER_CODE, route.externalOrderCode())
                .set(r.REVIEW_CASE_ID, route.reviewCaseId())
                .set(r.EXTERNAL_SUBMISSION_REF, route.externalSubmissionRef())
                .set(r.CALLBACK_BATCH_REF, route.callbackBatchRef())
                .set(r.MAPPING_VERSION_ID, route.mappingVersionId())
                .set(r.STATUS, "ACTIVE")
                .set(r.CREATED_BY, route.createdBy())
                .set(r.CREATED_AT, route.createdAt())
                .onConflictDoNothing()
                .execute();
        ExternalReviewRouteView current = dsl.select(reviewRouteFields())
                .from(r)
                .where(r.TENANT_ID.eq(route.tenantId()))
                .and(r.REVIEW_CASE_ID.eq(route.reviewCaseId()))
                .fetchOptional(this::reviewRoute)
                .orElseGet(() -> dsl.select(reviewRouteFields())
                        .from(r)
                        .where(r.TENANT_ID.eq(route.tenantId()))
                        .and(r.CONNECTOR_VERSION_ID.eq(route.connectorVersionId()))
                        .and(r.EXTERNAL_ORDER_CODE.eq(route.externalOrderCode()))
                        .and(r.STATUS.eq("ACTIVE"))
                        .fetchSingle(this::reviewRoute));
        return new ExternalReviewRouteRegistration(current, inserted == 1);
    }

    @Override
    public Optional<ExternalReviewRouteView> findActiveExternalReviewRoute(
            String tenantId,
            String connectorVersionId,
            String externalOrderCode
    ) {
        IntExternalReviewRoute r = INT_EXTERNAL_REVIEW_ROUTE;
        return dsl.select(reviewRouteFields())
                .from(r)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.CONNECTOR_VERSION_ID.eq(connectorVersionId))
                .and(r.EXTERNAL_ORDER_CODE.eq(externalOrderCode))
                .and(r.STATUS.eq("ACTIVE"))
                .fetchOptional(this::reviewRoute);
    }

    @Override
    public Optional<ExternalReviewRouteView> findExternalReviewRoute(String tenantId, UUID reviewRouteId) {
        IntExternalReviewRoute r = INT_EXTERNAL_REVIEW_ROUTE;
        return dsl.select(reviewRouteFields())
                .from(r)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.REVIEW_ROUTE_ID.eq(reviewRouteId))
                .fetchOptional(this::reviewRoute);
    }

    @Override
    public void completeExternalReviewRoute(
            String tenantId,
            UUID reviewRouteId,
            UUID canonicalMessageId,
            Instant completedAt
    ) {
        IntExternalReviewRoute r = INT_EXTERNAL_REVIEW_ROUTE;
        int updated = dsl.update(r)
                .set(r.STATUS, "COMPLETED")
                .set(r.CANONICAL_MESSAGE_ID, canonicalMessageId)
                .set(r.COMPLETED_AT, completedAt)
                .where(r.TENANT_ID.eq(tenantId))
                .and(r.REVIEW_ROUTE_ID.eq(reviewRouteId))
                .and(r.STATUS.eq("ACTIVE"))
                .execute();
        if (updated == 0) {
            ExternalReviewRouteView current = dsl.select(reviewRouteFields())
                    .from(r)
                    .where(r.TENANT_ID.eq(tenantId))
                    .and(r.REVIEW_ROUTE_ID.eq(reviewRouteId))
                    .fetchSingle(this::reviewRoute);
            if (!"COMPLETED".equals(current.status())
                    || !canonicalMessageId.equals(current.canonicalMessageId())) {
                throw new IllegalStateException("External review route completion conflicts with existing result");
            }
        }
    }

    @Override
    public InboundItemResult insertItemResult(InboundItemResult result) {
        IntInboundItemResult item = INT_INBOUND_ITEM_RESULT;
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        // INSERT ... SELECT：tenant 以 Envelope 行为准，不依赖调用方传入；唯一键裁决并发重复。
        dsl.insertInto(item)
                .columns(item.INBOUND_ENVELOPE_ID, item.TENANT_ID, item.ITEM_KEY,
                        item.CANONICAL_MESSAGE_ID, item.PROCESSING_RESULT, item.RESULT_CODE,
                        item.RESULT_TYPE, item.RESULT_ID, item.COMPLETED_AT)
                .select(dsl.select(
                                DSL.val(result.inboundEnvelopeId()),
                                e.TENANT_ID,
                                DSL.val(result.itemKey()),
                                DSL.val(result.canonicalMessageId()),
                                DSL.val(result.processingResult()),
                                DSL.val(result.resultCode()),
                                DSL.val(result.resultType()),
                                DSL.val(result.resultId()),
                                DSL.val(result.completedAt(), item.COMPLETED_AT))
                        .from(e)
                        .where(e.INBOUND_ENVELOPE_ID.eq(result.inboundEnvelopeId())))
                .onConflict(item.INBOUND_ENVELOPE_ID, item.ITEM_KEY)
                .doNothing()
                .execute();
        return dsl.select(itemResultFields())
                .from(item)
                .where(item.TENANT_ID.eq(dsl.select(e.TENANT_ID)
                        .from(e)
                        .where(e.INBOUND_ENVELOPE_ID.eq(result.inboundEnvelopeId()))))
                .and(item.INBOUND_ENVELOPE_ID.eq(result.inboundEnvelopeId()))
                .and(item.ITEM_KEY.eq(result.itemKey()))
                .fetchSingle(this::itemResult);
    }

    @Override
    public List<InboundItemResult> findItemResults(String tenantId, UUID inboundEnvelopeId) {
        IntInboundItemResult item = INT_INBOUND_ITEM_RESULT;
        return dsl.select(itemResultFields())
                .from(item)
                .where(item.TENANT_ID.eq(tenantId))
                .and(item.INBOUND_ENVELOPE_ID.eq(inboundEnvelopeId))
                .orderBy(item.ITEM_KEY)
                .fetch(this::itemResult);
    }

    @Override
    public void completeBatchEnvelope(
            String tenantId,
            UUID envelopeId,
            UUID projectId,
            String canonicalPayloadDigest,
            String mappingVersionId,
            String resultCode,
            String resultId,
            Instant completedAt
    ) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        int updated = dsl.update(e)
                .set(e.PROJECT_ID, projectId)
                .set(e.CANONICAL_PAYLOAD_DIGEST, canonicalPayloadDigest)
                .set(e.MAPPING_VERSION_ID, mappingVersionId)
                .set(e.PROCESSING_STATUS, "COMPLETED")
                .set(e.RESULT_CODE, resultCode)
                .set(e.RESULT_TYPE, "REVIEW_CALLBACK_BATCH")
                .set(e.RESULT_ID, resultId)
                .set(e.COMPLETED_AT, completedAt)
                .where(e.TENANT_ID.eq(tenantId))
                .and(e.INBOUND_ENVELOPE_ID.eq(envelopeId))
                .and(e.PROCESSING_STATUS.eq("RECEIVED"))
                .execute();
        if (updated == 0) {
            InboundEnvelopeView current = findEnvelope(tenantId, envelopeId)
                    .map(InboundEnvelopeRecord::view)
                    .orElseThrow(() -> new IllegalStateException("InboundEnvelope disappeared"));
            if (!"COMPLETED".equals(current.processingStatus())
                    || !resultCode.equals(current.resultCode())
                    || !resultId.equals(current.resultId())) {
                throw new IllegalStateException("Batch Envelope completion conflicts with existing result");
            }
        }
    }

    private InboundEnvelopeRecord envelope(Record record) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        return new InboundEnvelopeRecord(new InboundEnvelopeView(
                record.get(e.INBOUND_ENVELOPE_ID), record.get(e.PROJECT_ID),
                record.get(e.CONNECTOR_VERSION_ID), record.get(e.MESSAGE_TYPE),
                record.get(e.EXTERNAL_MESSAGE_ID), record.get(e.RAW_PAYLOAD_DIGEST),
                record.get(e.CANONICAL_PAYLOAD_DIGEST), record.get(e.SIGNATURE_STATUS),
                record.get(e.PROCESSING_STATUS), record.get(e.MAPPING_VERSION_ID),
                record.get(e.CANONICAL_MESSAGE_ID),
                record.get(e.RESULT_CODE), record.get(e.RESULT_TYPE), record.get(e.RESULT_ID),
                record.get(e.RECEIVED_AT), record.get(e.COMPLETED_AT), record.get(e.CORRELATION_ID)),
                record.get(e.RAW_PAYLOAD_OBJECT_REF), record.get(e.TRANSPORT_DEDUP_KEY));
    }

    private InboundEnvelopeQueueItem queueItem(Record record) {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        return new InboundEnvelopeQueueItem(
                record.get(e.INBOUND_ENVELOPE_ID),
                record.get(e.PROJECT_ID),
                record.get(e.CONNECTOR_VERSION_ID),
                record.get(e.MESSAGE_TYPE),
                record.get(e.EXTERNAL_MESSAGE_ID),
                record.get(e.SIGNATURE_STATUS),
                record.get(e.PROCESSING_STATUS),
                record.get(e.MAPPING_VERSION_ID),
                record.get(e.CANONICAL_MESSAGE_ID),
                record.get(e.RESULT_CODE),
                record.get(e.RESULT_TYPE),
                record.get(e.RESULT_ID),
                record.get(e.RECEIVED_AT),
                record.get(e.COMPLETED_AT),
                record.get(e.CORRELATION_ID));
    }

    private CanonicalMessageRecord canonical(Record record) {
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        return new CanonicalMessageRecord(new CanonicalMessageView(
                record.get(c.CANONICAL_MESSAGE_ID),
                record.get(c.PROJECT_ID), record.get(c.CONNECTOR_VERSION_ID),
                record.get(c.MESSAGE_TYPE), record.get(c.BUSINESS_KEY),
                record.get(c.PAYLOAD_DIGEST), record.get(c.MAPPING_VERSION_ID),
                record.get(c.PROCESSING_STATUS), record.get(c.RESULT_CODE),
                record.get(c.RESULT_TYPE), record.get(c.RESULT_ID),
                record.get(c.CREATED_AT), record.get(c.PROCESSED_AT)),
                record.get(c.PAYLOAD_OBJECT_REF), record.get(c.SOURCE_ENVELOPE_ID));
    }

    private ExternalReviewRouteView reviewRoute(Record record) {
        IntExternalReviewRoute r = INT_EXTERNAL_REVIEW_ROUTE;
        return new ExternalReviewRouteView(
                record.get(r.REVIEW_ROUTE_ID), record.get(r.PROJECT_ID),
                record.get(r.CONNECTOR_VERSION_ID), record.get(r.EXTERNAL_ORDER_CODE),
                record.get(r.REVIEW_CASE_ID), record.get(r.EXTERNAL_SUBMISSION_REF),
                record.get(r.CALLBACK_BATCH_REF), record.get(r.MAPPING_VERSION_ID),
                record.get(r.STATUS), record.get(r.CANONICAL_MESSAGE_ID),
                record.get(r.CREATED_BY), record.get(r.CREATED_AT), record.get(r.COMPLETED_AT));
    }

    private InboundItemResult itemResult(Record record) {
        IntInboundItemResult item = INT_INBOUND_ITEM_RESULT;
        return new InboundItemResult(
                record.get(item.INBOUND_ENVELOPE_ID), record.get(item.ITEM_KEY),
                record.get(item.CANONICAL_MESSAGE_ID), record.get(item.PROCESSING_RESULT),
                record.get(item.RESULT_CODE), record.get(item.RESULT_TYPE),
                record.get(item.RESULT_ID), record.get(item.COMPLETED_AT));
    }

    private static List<SelectField<?>> envelopeFields() {
        IntInboundEnvelope e = INT_INBOUND_ENVELOPE;
        return List.of(
                e.INBOUND_ENVELOPE_ID, e.TENANT_ID, e.PROJECT_ID, e.CONNECTOR_VERSION_ID,
                e.MESSAGE_TYPE, e.TRANSPORT_DEDUP_KEY, e.EXTERNAL_MESSAGE_ID, e.RECEIVED_AT,
                e.RAW_PAYLOAD_OBJECT_REF, e.RAW_PAYLOAD_DIGEST, e.CANONICAL_PAYLOAD_DIGEST,
                e.SIGNATURE_STATUS, e.PROCESSING_STATUS, e.MAPPING_VERSION_ID,
                e.CANONICAL_MESSAGE_ID, e.RESULT_CODE, e.RESULT_TYPE, e.RESULT_ID,
                e.CORRELATION_ID, e.COMPLETED_AT);
    }

    private static List<SelectField<?>> canonicalFields() {
        IntCanonicalMessage c = INT_CANONICAL_MESSAGE;
        return List.of(
                c.CANONICAL_MESSAGE_ID, c.TENANT_ID, c.PROJECT_ID, c.CONNECTOR_VERSION_ID,
                c.MESSAGE_TYPE, c.BUSINESS_KEY, c.PAYLOAD_OBJECT_REF, c.PAYLOAD_DIGEST,
                c.MAPPING_VERSION_ID, c.PROCESSING_STATUS, c.RESULT_CODE, c.RESULT_TYPE,
                c.RESULT_ID, c.SOURCE_ENVELOPE_ID, c.CREATED_AT, c.PROCESSED_AT);
    }

    private static List<SelectField<?>> reviewRouteFields() {
        IntExternalReviewRoute r = INT_EXTERNAL_REVIEW_ROUTE;
        return List.of(
                r.REVIEW_ROUTE_ID, r.TENANT_ID, r.PROJECT_ID, r.CONNECTOR_VERSION_ID,
                r.EXTERNAL_ORDER_CODE, r.REVIEW_CASE_ID, r.EXTERNAL_SUBMISSION_REF,
                r.CALLBACK_BATCH_REF, r.MAPPING_VERSION_ID, r.STATUS, r.CANONICAL_MESSAGE_ID,
                r.CREATED_BY, r.CREATED_AT, r.COMPLETED_AT);
    }

    private static List<SelectField<?>> itemResultFields() {
        IntInboundItemResult item = INT_INBOUND_ITEM_RESULT;
        return List.of(
                item.INBOUND_ENVELOPE_ID, item.TENANT_ID, item.ITEM_KEY, item.CANONICAL_MESSAGE_ID,
                item.PROCESSING_RESULT, item.RESULT_CODE, item.RESULT_TYPE, item.RESULT_ID,
                item.COMPLETED_AT);
    }
}
