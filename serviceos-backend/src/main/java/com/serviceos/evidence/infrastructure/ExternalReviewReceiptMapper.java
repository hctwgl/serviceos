package com.serviceos.evidence.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
interface ExternalReviewReceiptMapper {
    void insert(Map<String, Object> values);

    Map<String, Object> find(
            @Param("tenantId") String tenantId, @Param("receiptId") String receiptId);

    String findByInboundEnvelope(
            @Param("tenantId") String tenantId, @Param("inboundEnvelopeId") String inboundEnvelopeId);

    String findCommandResult(
            @Param("tenantId") String tenantId,
            @Param("operationType") String operationType,
            @Param("idempotencyKey") String idempotencyKey);

    void saveCommandResult(Map<String, Object> values);
}
