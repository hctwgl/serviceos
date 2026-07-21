package com.serviceos.forms.infrastructure;

import com.serviceos.forms.api.FormSubmissionSummaryView;
import com.serviceos.forms.api.FormSubmissionView;
import com.serviceos.forms.api.FormValidationIssue;
import com.serviceos.forms.application.FormSubmissionRepository;
import com.serviceos.jooq.generated.tables.FrmFormSubmission;
import com.serviceos.jooq.generated.tables.FrmSubmissionValidation;
import com.serviceos.jooq.generated.tables.TskTask;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.serviceos.jooq.generated.tables.FrmFormCommandResult.FRM_FORM_COMMAND_RESULT;
import static com.serviceos.jooq.generated.tables.FrmFormSubmission.FRM_FORM_SUBMISSION;
import static com.serviceos.jooq.generated.tables.FrmSubmissionValidation.FRM_SUBMISSION_VALIDATION;
import static com.serviceos.jooq.generated.tables.TskTask.TSK_TASK;
import static com.serviceos.jooq.generated.tables.TskTaskAssignment.TSK_TASK_ASSIGNMENT;
import static com.serviceos.jooq.generated.tables.TskTaskExecutionGuard.TSK_TASK_EXECUTION_GUARD;

/**
 * 表单提交的 jOOQ 持久化（ADR-091）。可执行检查在原 SQL 内完成：RUNNING 人工 Task、
 * 责任人有效指派、无活跃执行守卫，并以 FOR UPDATE OF t 锁定 Task 行；values/errors/warnings
 * 等 jsonb 文档由全局 JsonbStringConverter 直接绑定 JSON 文本。
 */
@Repository
final class JooqFormSubmissionRepository implements FormSubmissionRepository {
    private static final TypeReference<List<FormValidationIssue>> ISSUES = new TypeReference<>() { };
    private static final ObjectMapper CANONICAL_JSON = JsonMapper.builder()
            .findAndAddModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();
    private static final FrmFormSubmission S = FRM_FORM_SUBMISSION;
    private static final FrmSubmissionValidation V = FRM_SUBMISSION_VALIDATION;
    private static final List<SelectField<?>> SUBMISSION_FIELDS = List.of(
            S.FORM_SUBMISSION_ID, S.TASK_ID, S.PROJECT_ID, S.FORM_VERSION_ID,
            S.FORM_KEY, S.SUBMISSION_VERSION, S.VALUES_DOCUMENT, S.CONTENT_DIGEST,
            S.VALIDATION_STATUS, S.PREFILL_VERSION, S.SUBMITTED_BY, S.SUBMITTED_AT,
            V.ERRORS_DOCUMENT, V.WARNINGS_DOCUMENT);

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    JooqFormSubmissionRepository(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean lockExecutableTask(String tenantId, UUID taskId, String actorId) {
        TskTask t = TSK_TASK.as("t");
        Long version = dsl.select(t.VERSION)
                .from(t)
                .where(t.TENANT_ID.eq(tenantId))
                .and(t.TASK_ID.eq(taskId))
                .and(t.TASK_KIND.eq("HUMAN"))
                .and(t.STATUS.eq("RUNNING"))
                .andExists(dsl.selectOne()
                        .from(TSK_TASK_ASSIGNMENT)
                        .where(TSK_TASK_ASSIGNMENT.TENANT_ID.eq(t.TENANT_ID))
                        .and(TSK_TASK_ASSIGNMENT.TASK_ID.eq(t.TASK_ID))
                        .and(TSK_TASK_ASSIGNMENT.ASSIGNMENT_KIND.eq("RESPONSIBLE"))
                        .and(TSK_TASK_ASSIGNMENT.STATUS.eq("ACTIVE"))
                        .and(TSK_TASK_ASSIGNMENT.PRINCIPAL_ID.eq(actorId)))
                .andNotExists(dsl.selectOne()
                        .from(TSK_TASK_EXECUTION_GUARD)
                        .where(TSK_TASK_EXECUTION_GUARD.TENANT_ID.eq(t.TENANT_ID))
                        .and(TSK_TASK_EXECUTION_GUARD.TASK_ID.eq(t.TASK_ID))
                        .and(TSK_TASK_EXECUTION_GUARD.STATUS.eq("ACTIVE")))
                .forUpdate().of(t)
                .fetchOne(t.VERSION);
        return version != null;
    }

    @Override
    public int nextVersion(String tenantId, UUID taskId, UUID formVersionId) {
        return dsl.select(DSL.coalesce(DSL.max(S.SUBMISSION_VERSION), 0).plus(1))
                .from(S)
                .where(S.TENANT_ID.eq(tenantId))
                .and(S.TASK_ID.eq(taskId))
                .and(S.FORM_VERSION_ID.eq(formVersionId))
                .fetchSingle()
                .value1();
    }

    @Override
    public void insert(String tenantId, FormSubmissionView submission) {
        dsl.insertInto(S)
                .set(S.FORM_SUBMISSION_ID, submission.submissionId())
                .set(S.TENANT_ID, tenantId)
                .set(S.TASK_ID, submission.taskId())
                .set(S.PROJECT_ID, submission.projectId())
                .set(S.FORM_VERSION_ID, submission.formVersionId())
                .set(S.FORM_KEY, submission.formKey())
                .set(S.SUBMISSION_VERSION, submission.submissionVersion())
                .set(S.VALUES_DOCUMENT, submission.valuesJson())
                .set(S.CONTENT_DIGEST, submission.contentDigest())
                .set(S.VALIDATION_STATUS, submission.validationStatus())
                .set(S.PREFILL_VERSION, submission.prefillVersion())
                .set(S.SUBMITTED_BY, submission.submittedBy())
                .set(S.SUBMITTED_AT, submission.submittedAt())
                .execute();
    }

    @Override
    public void insertValidation(UUID validationId, String tenantId, FormSubmissionView submission,
                                 String validatorVersion, String inputDigest) {
        dsl.insertInto(V)
                .set(V.SUBMISSION_VALIDATION_ID, validationId)
                .set(V.TENANT_ID, tenantId)
                .set(V.FORM_SUBMISSION_ID, submission.submissionId())
                .set(V.VALIDATOR_VERSION, validatorVersion)
                .set(V.INPUT_DIGEST, inputDigest)
                .set(V.VALIDATION_STATUS, submission.validationStatus())
                .set(V.ERRORS_DOCUMENT, json(submission.errors()))
                .set(V.WARNINGS_DOCUMENT, json(submission.warnings()))
                .set(V.EXECUTED_AT, submission.submittedAt())
                .execute();
    }

    @Override
    public Optional<FormSubmissionView> find(String tenantId, UUID submissionId) {
        return dsl.select(SUBMISSION_FIELDS)
                .from(S)
                .join(V).on(V.TENANT_ID.eq(S.TENANT_ID))
                .and(V.FORM_SUBMISSION_ID.eq(S.FORM_SUBMISSION_ID))
                .where(S.TENANT_ID.eq(tenantId))
                .and(S.FORM_SUBMISSION_ID.eq(submissionId))
                .fetchOptional()
                .map(this::view);
    }

    @Override
    public List<FormSubmissionSummaryView> listSummariesByTask(String tenantId, UUID taskId) {
        return dsl.select(
                        S.FORM_SUBMISSION_ID, S.TASK_ID, S.PROJECT_ID, S.FORM_VERSION_ID,
                        S.FORM_KEY, S.SUBMISSION_VERSION, S.CONTENT_DIGEST, S.VALIDATION_STATUS,
                        DSL.function("jsonb_array_length", Integer.class, V.ERRORS_DOCUMENT)
                                .as("errorCount"),
                        DSL.function("jsonb_array_length", Integer.class, V.WARNINGS_DOCUMENT)
                                .as("warningCount"),
                        S.SUBMITTED_AT)
                .from(S)
                .join(V).on(V.TENANT_ID.eq(S.TENANT_ID))
                .and(V.FORM_SUBMISSION_ID.eq(S.FORM_SUBMISSION_ID))
                .where(S.TENANT_ID.eq(tenantId))
                .and(S.TASK_ID.eq(taskId))
                .orderBy(S.SUBMITTED_AT, S.FORM_SUBMISSION_ID)
                .fetch(row -> new FormSubmissionSummaryView(
                        row.get(S.FORM_SUBMISSION_ID), row.get(S.TASK_ID), row.get(S.PROJECT_ID),
                        row.get(S.FORM_VERSION_ID), row.get(S.FORM_KEY), row.get(S.SUBMISSION_VERSION),
                        row.get(S.CONTENT_DIGEST), row.get(S.VALIDATION_STATUS),
                        row.get("errorCount", Integer.class), row.get("warningCount", Integer.class),
                        row.get(S.SUBMITTED_AT)));
    }

    @Override
    public void saveResult(String tenantId, String operationType, String idempotencyKey, UUID submissionId) {
        dsl.insertInto(FRM_FORM_COMMAND_RESULT)
                .set(FRM_FORM_COMMAND_RESULT.TENANT_ID, tenantId)
                .set(FRM_FORM_COMMAND_RESULT.OPERATION_TYPE, operationType)
                .set(FRM_FORM_COMMAND_RESULT.IDEMPOTENCY_KEY, idempotencyKey)
                .set(FRM_FORM_COMMAND_RESULT.FORM_SUBMISSION_ID, submissionId)
                .execute();
    }

    @Override
    public FormSubmissionView findResult(String tenantId, String operationType, String idempotencyKey) {
        return dsl.select(SUBMISSION_FIELDS)
                .from(S)
                .join(V).on(V.TENANT_ID.eq(S.TENANT_ID))
                .and(V.FORM_SUBMISSION_ID.eq(S.FORM_SUBMISSION_ID))
                .join(FRM_FORM_COMMAND_RESULT)
                .on(FRM_FORM_COMMAND_RESULT.TENANT_ID.eq(S.TENANT_ID))
                .and(FRM_FORM_COMMAND_RESULT.FORM_SUBMISSION_ID.eq(S.FORM_SUBMISSION_ID))
                .where(FRM_FORM_COMMAND_RESULT.TENANT_ID.eq(tenantId))
                .and(FRM_FORM_COMMAND_RESULT.OPERATION_TYPE.eq(operationType))
                .and(FRM_FORM_COMMAND_RESULT.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOptional()
                .map(this::view)
                .orElseThrow(() -> new IllegalStateException("Frozen FormSubmission result is missing"));
    }

    private FormSubmissionView view(Record row) {
        return new FormSubmissionView(
                row.get(S.FORM_SUBMISSION_ID), row.get(S.TASK_ID), row.get(S.PROJECT_ID),
                row.get(S.FORM_VERSION_ID), row.get(S.FORM_KEY), row.get(S.SUBMISSION_VERSION),
                canonicalJson(row.get(S.VALUES_DOCUMENT)), row.get(S.CONTENT_DIGEST),
                row.get(S.VALIDATION_STATUS),
                issues(row.get(V.ERRORS_DOCUMENT)), issues(row.get(V.WARNINGS_DOCUMENT)),
                row.get(S.PREFILL_VERSION), row.get(S.SUBMITTED_BY), row.get(S.SUBMITTED_AT));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Validation issues serialization failed", exception);
        }
    }

    private static String canonicalJson(String value) {
        try {
            return CANONICAL_JSON.writeValueAsString(CANONICAL_JSON.readValue(value, Object.class));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored FormSubmission values are invalid", exception);
        }
    }

    private List<FormValidationIssue> issues(String value) {
        if (value == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, ISSUES);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored validation issues are invalid", exception);
        }
    }
}
