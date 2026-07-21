package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.ExternalReviewAffectedTarget;
import com.serviceos.evidence.api.ExternalReviewReceiptView;
import com.serviceos.evidence.application.ExternalReviewReceiptRepository;
import com.serviceos.jooq.generated.tables.records.EvdExternalReviewReceiptRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.EvdExternalReceiptCommandResult.EVD_EXTERNAL_RECEIPT_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.EvdExternalReviewReceipt.EVD_EXTERNAL_REVIEW_RECEIPT;

/** 外部审核回执持久化的 jOOQ 实现（取代 MyBatis Mapper + XML）。 */
@Repository
final class JooqExternalReviewReceiptRepository implements ExternalReviewReceiptRepository {
    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqExternalReviewReceiptRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(String tenantId, ExternalReviewReceiptView receipt) {
        dsl.insertInto(EVD_EXTERNAL_REVIEW_RECEIPT)
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.RECEIPT_ID, receipt.receiptId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.TENANT_ID, tenantId)
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.PROJECT_ID, receipt.projectId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.REVIEW_CASE_ID, receipt.reviewCaseId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.REVIEW_DECISION_ID, receipt.reviewDecisionId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.INBOUND_ENVELOPE_ID, receipt.inboundEnvelopeId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.CANONICAL_MESSAGE_ID, receipt.canonicalMessageId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.EXTERNAL_KEY, receipt.externalKey())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.CALLBACK_BATCH_REF, receipt.callbackBatchRef())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.MAPPING_VERSION_ID, receipt.mappingVersionId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.RESULT, receipt.result())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.REASON_CODES, writeJson(receipt.reasonCodes()))
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.AFFECTED_TARGETS, writeJson(receipt.affectedTargets()))
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.PAYLOAD_REF, receipt.payloadRef())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.COORDINATION_TASK_ID, receipt.coordinationTaskId())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.RECEIVED_BY, receipt.receivedBy())
                .set(EVD_EXTERNAL_REVIEW_RECEIPT.RECEIVED_AT, receipt.receivedAt())
                .execute();
    }

    @Override
    public Optional<ExternalReviewReceiptView> find(String tenantId, UUID receiptId) {
        return dsl.selectFrom(EVD_EXTERNAL_REVIEW_RECEIPT)
                .where(EVD_EXTERNAL_REVIEW_RECEIPT.TENANT_ID.eq(tenantId))
                .and(EVD_EXTERNAL_REVIEW_RECEIPT.RECEIPT_ID.eq(receiptId))
                .fetchOptional()
                .map(this::view);
    }

    @Override
    public Optional<UUID> findByCanonicalMessage(String tenantId, String canonicalMessageId) {
        // canonicalMessageId 是权威幂等键，至多一行；多于一行与原 MyBatis 单值映射一样失败。
        return dsl.select(EVD_EXTERNAL_REVIEW_RECEIPT.RECEIPT_ID)
                .from(EVD_EXTERNAL_REVIEW_RECEIPT)
                .where(EVD_EXTERNAL_REVIEW_RECEIPT.TENANT_ID.eq(tenantId))
                .and(EVD_EXTERNAL_REVIEW_RECEIPT.CANONICAL_MESSAGE_ID.eq(canonicalMessageId))
                .fetchOptional(EVD_EXTERNAL_REVIEW_RECEIPT.RECEIPT_ID);
    }

    @Override
    public Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey) {
        return dsl.select(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.RESULT_ID)
                .from(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT)
                .where(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.TENANT_ID.eq(tenantId))
                .and(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.OPERATION_TYPE.eq(operationType))
                .and(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.RESULT_ID);
    }

    @Override
    public void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId) {
        dsl.insertInto(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT)
                .set(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.TENANT_ID, tenantId)
                .set(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.OPERATION_TYPE, operationType)
                .set(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.IDEMPOTENCY_KEY, idempotencyKey)
                .set(EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.RESULT_ID, resultId)
                .onConflict(
                        EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.TENANT_ID,
                        EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.OPERATION_TYPE,
                        EVD_EXTERNAL_RECEIPT_COMMAND_RESULT.IDEMPOTENCY_KEY)
                .doNothing()
                .execute();
    }

    private ExternalReviewReceiptView view(EvdExternalReviewReceiptRecord row) {
        return new ExternalReviewReceiptView(
                row.getReceiptId(), row.getProjectId(), row.getReviewCaseId(), row.getReviewDecisionId(),
                row.getInboundEnvelopeId(), row.getCanonicalMessageId(), row.getExternalKey(),
                row.getCallbackBatchRef(), row.getMappingVersionId(), row.getResult(),
                readCodes(row.getReasonCodes()), readTargets(row.getAffectedTargets()),
                row.getPayloadRef(), row.getCoordinationTaskId(), row.getReceivedBy(), row.getReceivedAt());
    }

    private List<String> readCodes(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("reasonCodes are invalid", exception);
        }
    }

    private List<ExternalReviewAffectedTarget> readTargets(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JacksonException exception) {
            throw new IllegalStateException("affectedTargets are invalid", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("JSON serialization failed", exception);
        }
    }
}
