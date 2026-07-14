package com.serviceos.forms.application;

import com.serviceos.forms.api.FormSubmissionView;

import java.util.Optional;
import java.util.UUID;

/** 表单提交持久化端口；所有读取和幂等结果都显式携带 tenant scope。 */
public interface FormSubmissionRepository {
    boolean lockExecutableTask(String tenantId, UUID taskId, String actorId);
    int nextVersion(String tenantId, UUID taskId, UUID formVersionId);
    void insert(String tenantId, FormSubmissionView submission);
    void insertValidation(UUID validationId, String tenantId, FormSubmissionView submission,
                          String validatorVersion, String inputDigest);
    Optional<FormSubmissionView> find(String tenantId, UUID submissionId);
    void saveResult(String tenantId, String operationType, String idempotencyKey, UUID submissionId);
    FormSubmissionView findResult(String tenantId, String operationType, String idempotencyKey);
}
