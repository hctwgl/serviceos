package com.serviceos.forms.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;
import java.util.UUID;

/** FormSubmission MyBatis SQL 边界，仅供同包 Repository 适配器使用。 */
@Mapper
interface FormSubmissionMapper {
    Long lockExecutableTask(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId,
                            @Param("actorId") String actorId);
    int nextVersion(@Param("tenantId") String tenantId, @Param("taskId") UUID taskId,
                    @Param("formVersionId") UUID formVersionId);
    void insertSubmission(Map<String, Object> values);
    void insertValidation(Map<String, Object> values);
    Map<String, Object> find(@Param("tenantId") String tenantId,
                             @Param("submissionId") UUID submissionId);
    void insertResult(Map<String, Object> values);
    Map<String, Object> findResult(@Param("tenantId") String tenantId,
                                   @Param("operationType") String operationType,
                                   @Param("idempotencyKey") String idempotencyKey);
}
