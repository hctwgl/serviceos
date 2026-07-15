package com.serviceos.evidence.infrastructure;

import com.serviceos.evidence.api.ExternalReviewAffectedTarget;
import com.serviceos.evidence.api.ExternalReviewReceiptView;
import com.serviceos.evidence.application.ExternalReviewReceiptRepository;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisExternalReviewReceiptRepository implements ExternalReviewReceiptRepository {
    private final ExternalReviewReceiptMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisExternalReviewReceiptRepository(ExternalReviewReceiptMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void insert(String tenantId, ExternalReviewReceiptView receipt) {
        Map<String, Object> values = new HashMap<>();
        values.put("receiptId", receipt.receiptId().toString());
        values.put("tenantId", tenantId);
        values.put("projectId", receipt.projectId().toString());
        values.put("reviewCaseId", receipt.reviewCaseId().toString());
        values.put("reviewDecisionId", receipt.reviewDecisionId().toString());
        values.put("inboundEnvelopeId", receipt.inboundEnvelopeId());
        values.put("canonicalMessageId", receipt.canonicalMessageId());
        values.put("externalKey", receipt.externalKey());
        values.put("callbackBatchRef", receipt.callbackBatchRef());
        values.put("mappingVersionId", receipt.mappingVersionId());
        values.put("result", receipt.result());
        values.put("reasonCodes", writeJson(receipt.reasonCodes()));
        values.put("affectedTargets", writeJson(receipt.affectedTargets()));
        values.put("payloadRef", receipt.payloadRef());
        values.put("coordinationTaskId", receipt.coordinationTaskId() == null
                ? null : receipt.coordinationTaskId().toString());
        values.put("receivedBy", receipt.receivedBy());
        values.put("receivedAt", receipt.receivedAt());
        mapper.insert(values);
    }

    @Override
    public Optional<ExternalReviewReceiptView> find(String tenantId, UUID receiptId) {
        Map<String, Object> row = mapper.find(tenantId, receiptId.toString());
        return row == null ? Optional.empty() : Optional.of(view(row));
    }

    @Override
    public Optional<UUID> findByCanonicalMessage(String tenantId, String canonicalMessageId) {
        String id = mapper.findByCanonicalMessage(tenantId, canonicalMessageId);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    @Override
    public Optional<UUID> findCommandResult(String tenantId, String operationType, String idempotencyKey) {
        String id = mapper.findCommandResult(tenantId, operationType, idempotencyKey);
        return id == null ? Optional.empty() : Optional.of(UUID.fromString(id));
    }

    @Override
    public void saveCommandResult(String tenantId, String operationType, String idempotencyKey, UUID resultId) {
        Map<String, Object> values = new HashMap<>();
        values.put("tenantId", tenantId);
        values.put("operationType", operationType);
        values.put("idempotencyKey", idempotencyKey);
        values.put("resultId", resultId.toString());
        mapper.saveCommandResult(values);
    }

    private ExternalReviewReceiptView view(Map<String, Object> row) {
        return new ExternalReviewReceiptView(
                uuid(row, "receiptId"), uuid(row, "projectId"), uuid(row, "reviewCaseId"),
                uuid(row, "reviewDecisionId"), text(row, "inboundEnvelopeId"),
                text(row, "canonicalMessageId"), text(row, "externalKey"),
                text(row, "callbackBatchRef"), text(row, "mappingVersionId"),
                text(row, "result"), readCodes(text(row, "reasonCodes")),
                readTargets(text(row, "affectedTargets")),
                row.get("payloadRef") == null ? null : text(row, "payloadRef"),
                row.get("coordinationTaskId") == null ? null : uuid(row, "coordinationTaskId"),
                text(row, "receivedBy"), instant(row.get("receivedAt")));
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

    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }

    private static String text(Map<String, Object> row, String key) {
        return row.get(key).toString();
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new IllegalArgumentException("unsupported time type: " + value.getClass().getName());
    }
}
