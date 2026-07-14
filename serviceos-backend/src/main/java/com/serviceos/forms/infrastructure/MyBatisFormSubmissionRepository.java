package com.serviceos.forms.infrastructure;

import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.forms.api.FormValidationIssue;
import com.serviceos.forms.application.FormSubmissionRepository;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
final class MyBatisFormSubmissionRepository implements FormSubmissionRepository {
    private static final TypeReference<List<FormValidationIssue>> ISSUES = new TypeReference<>() { };
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private final FormSubmissionMapper mapper;
    private final ObjectMapper objectMapper;

    MyBatisFormSubmissionRepository(FormSubmissionMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean lockExecutableTask(String tenantId, UUID taskId, String actorId) {
        return mapper.lockExecutableTask(tenantId, taskId, actorId) != null;
    }

    @Override
    public int nextVersion(String tenantId, UUID taskId, UUID formVersionId) {
        return mapper.nextVersion(tenantId, taskId, formVersionId);
    }

    @Override
    public void insert(String tenantId, FormSubmissionView submission) {
        Map<String, Object> values = values(tenantId, submission);
        mapper.insertSubmission(values);
    }

    @Override
    public void insertValidation(UUID validationId, String tenantId, FormSubmissionView submission,
                                 String validatorVersion, String inputDigest) {
        Map<String, Object> values = new HashMap<>();
        values.put("validationId", validationId);
        values.put("tenantId", tenantId);
        values.put("submissionId", submission.submissionId());
        values.put("validatorVersion", validatorVersion);
        values.put("inputDigest", inputDigest);
        values.put("status", submission.validationStatus());
        values.put("errors", json(submission.errors()));
        values.put("warnings", json(submission.warnings()));
        values.put("executedAt", time(submission.submittedAt()));
        mapper.insertValidation(values);
    }

    @Override
    public Optional<FormSubmissionView> find(String tenantId, UUID submissionId) {
        return Optional.ofNullable(mapper.find(tenantId, submissionId)).map(this::view);
    }

    @Override
    public void saveResult(String tenantId, String operationType, String idempotencyKey, UUID submissionId) {
        mapper.insertResult(Map.of("tenantId", tenantId, "operationType", operationType,
                "idempotencyKey", idempotencyKey, "submissionId", submissionId));
    }

    @Override
    public FormSubmissionView findResult(String tenantId, String operationType, String idempotencyKey) {
        Map<String, Object> row = mapper.findResult(tenantId, operationType, idempotencyKey);
        if (row == null) throw new IllegalStateException("Frozen FormSubmission result is missing");
        return view(row);
    }

    private Map<String, Object> values(String tenantId, FormSubmissionView submission) {
        Map<String, Object> values = new HashMap<>();
        values.put("submissionId", submission.submissionId());
        values.put("tenantId", tenantId);
        values.put("taskId", submission.taskId());
        values.put("projectId", submission.projectId());
        values.put("formVersionId", submission.formVersionId());
        values.put("formKey", submission.formKey());
        values.put("submissionVersion", submission.submissionVersion());
        values.put("valuesJson", submission.valuesJson());
        values.put("contentDigest", submission.contentDigest());
        values.put("validationStatus", submission.validationStatus());
        values.put("prefillVersion", submission.prefillVersion());
        values.put("submittedBy", submission.submittedBy());
        values.put("submittedAt", time(submission.submittedAt()));
        return values;
    }

    private FormSubmissionView view(Map<String, Object> row) {
        return new FormSubmissionView(
                uuid(row, "submissionId"), uuid(row, "taskId"), uuid(row, "projectId"),
                uuid(row, "formVersionId"), text(row, "formKey"), integer(row, "submissionVersion"),
                canonicalJson(text(row, "valuesJson")), text(row, "contentDigest"),
                text(row, "validationStatus"),
                issues(row.get("errors")), issues(row.get("warnings")), text(row, "prefillVersion"),
                text(row, "submittedBy"), instant(row, "submittedAt"));
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JacksonException exception) { throw new IllegalStateException("Validation issues serialization failed", exception); }
    }

    private static String canonicalJson(String value) {
        try {
            return CANONICAL_JSON.writeValueAsString(CANONICAL_JSON.readValue(value, Object.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored FormSubmission values are invalid", exception);
        }
    }

    private List<FormValidationIssue> issues(Object value) {
        if (value == null) return List.of();
        try { return objectMapper.readValue(value.toString(), ISSUES); }
        catch (JacksonException exception) { throw new IllegalStateException("Stored validation issues are invalid", exception); }
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? null : value.toString();
    }
    private static UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key); return value instanceof UUID id ? id : UUID.fromString(value.toString());
    }
    private static int integer(Map<String, Object> row, String key) { return ((Number) row.get(key)).intValue(); }
    private static Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime time) return time.toInstant();
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        return Instant.parse(value.toString());
    }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
