package com.serviceos.evidence.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/** EvidenceItem / Revision MyBatis 边界。 */
@Mapper
interface EvidenceItemMapper {
    Map<String, Object> findSlot(
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId,
            @Param("slotId") String slotId);

    Map<String, Object> lockSlot(@Param("tenantId") String tenantId, @Param("slotId") String slotId);

    void insertUploadBinding(Map<String, Object> values);

    Map<String, Object> findUploadBinding(
            @Param("tenantId") String tenantId, @Param("uploadSessionId") String uploadSessionId);

    Map<String, Object> findUploadBindingByFileId(
            @Param("tenantId") String tenantId, @Param("fileId") String fileId);

    int markUploadFinalized(
            @Param("tenantId") String tenantId, @Param("uploadSessionId") String uploadSessionId);

    Map<String, Object> findItem(
            @Param("tenantId") String tenantId, @Param("evidenceItemId") String evidenceItemId);

    List<Map<String, Object>> listItems(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listItemSummaries(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listRevisionsForTask(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listRevisionsForItem(
            @Param("tenantId") String tenantId, @Param("evidenceItemId") String evidenceItemId);

    String findCommandResult(
            @Param("tenantId") String tenantId,
            @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);

    void saveCommandResult(Map<String, Object> values);

    Integer maxItemOrdinal(@Param("tenantId") String tenantId, @Param("slotId") String slotId);

    int countItems(@Param("tenantId") String tenantId, @Param("slotId") String slotId);

    int countCountingItems(@Param("tenantId") String tenantId, @Param("slotId") String slotId);

    void insertItem(Map<String, Object> values);

    Integer maxRevisionNumber(
            @Param("tenantId") String tenantId, @Param("evidenceItemId") String evidenceItemId);

    void insertRevision(Map<String, Object> values);

    int updateRevisionStatus(
            @Param("tenantId") String tenantId,
            @Param("revisionId") String revisionId,
            @Param("expectedStatus") String expectedStatus,
            @Param("status") String status);

    int invalidateRevision(Map<String, Object> values);

    int updateSlotStatus(
            @Param("tenantId") String tenantId,
            @Param("slotId") String slotId,
            @Param("status") String status);

    Map<String, Object> findRevision(
            @Param("tenantId") String tenantId, @Param("revisionId") String revisionId);

    Map<String, Object> findRevisionByFileObjectId(
            @Param("tenantId") String tenantId, @Param("fileObjectId") String fileObjectId);

    Map<String, Object> findRevisionByUploadSession(
            @Param("tenantId") String tenantId, @Param("uploadSessionId") String uploadSessionId);

    List<Map<String, Object>> findRevisionsByIds(
            @Param("tenantId") String tenantId,
            @Param("taskId") String taskId,
            @Param("revisionIds") List<String> revisionIds);

    List<Map<String, Object>> listCountingRevisionsForSlot(
            @Param("tenantId") String tenantId, @Param("slotId") String slotId);

    int countOtherCountingDigest(
            @Param("tenantId") String tenantId,
            @Param("projectId") String projectId,
            @Param("contentDigest") String contentDigest,
            @Param("excludeRevisionId") String excludeRevisionId);

    List<Map<String, Object>> listValidations(
            @Param("tenantId") String tenantId, @Param("revisionId") String revisionId);

    List<Map<String, Object>> listValidationsForTask(
            @Param("tenantId") String tenantId, @Param("taskId") String taskId);

    List<Map<String, Object>> listValidationsForItem(
            @Param("tenantId") String tenantId, @Param("evidenceItemId") String evidenceItemId);

    void insertValidation(Map<String, Object> values);
}
