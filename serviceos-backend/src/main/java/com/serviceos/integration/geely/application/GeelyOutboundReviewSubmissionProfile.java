package com.serviceos.integration.geely.application;

import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.OutboundReviewSubmissionProfile;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 吉利浩瀚提审创建档案（本地 stub）；真实 OpenAPI 签名仍 BLOCKED_EXTERNAL。 */
@Component
public class GeelyOutboundReviewSubmissionProfile implements OutboundReviewSubmissionProfile {
    private static final ConnectorIdentity IDENTITY = new ConnectorIdentity(
            GeelyInboundCreateOrderService.CONNECTOR_CODE,
            GeelyInboundCreateOrderService.ADAPTER_VERSION);
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("uuuuMMddHHmmss");

    private final ObjectMapper objectMapper;

    public GeelyOutboundReviewSubmissionProfile(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ConnectorIdentity identity() {
        return IDENTITY;
    }

    @Override
    public String outboundMappingVersion() {
        return "geely-haohan-v1.3-submit-settlement-v1";
    }

    @Override
    public String callbackMappingVersion() {
        return "geely-haohan-v1.3-settlement-audit-callback-v1";
    }

    @Override
    public String businessMessageType() {
        return "SUBMIT_CLIENT_REVIEW";
    }

    @Override
    public String taskType() {
        return GeelyOutboundSubmissionConnector.TASK_TYPE;
    }

    @Override
    public String failurePolicy() {
        return "geely-submit-settlement-fail-closed-v1";
    }

    @Override
    public String clientPolicy() {
        return "geely-client-review-v1";
    }

    @Override
    public String payloadStorageSegment() {
        return "geely/submit-settlement";
    }

    @Override
    public boolean supportsInboundLineage(String inboundConnectorVersionId, String inboundMessageType) {
        return IDENTITY.connectorVersionId().equals(inboundConnectorVersionId)
                && "CREATE_WORK_ORDER".equals(inboundMessageType);
    }

    @Override
    public String extractExternalOrderCode(String inboundBusinessKey) {
        String prefix = GeelyCreateOrderMapper.BUSINESS_PREFIX;
        if (inboundBusinessKey == null || !inboundBusinessKey.startsWith(prefix)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "GEELY inbound CanonicalMessage has an invalid business key");
        }
        String orderCode = inboundBusinessKey.substring(prefix.length());
        if (orderCode.isBlank() || orderCode.length() > 50) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "installProcessNo is invalid");
        }
        return orderCode;
    }

    @Override
    public String submitBusinessKey(String externalOrderCode, String snapshotContentDigest) {
        return "GEELY:SUBMIT_SETTLEMENT:" + externalOrderCode + ":" + snapshotContentDigest;
    }

    @Override
    public String clientSubmissionRef(UUID deliveryId) {
        return "GEELY:SUBMIT_SETTLEMENT:" + deliveryId;
    }

    @Override
    public String callbackBatchRef(UUID deliveryId) {
        return "GEELY:SETTLEMENT_CB:" + deliveryId;
    }

    @Override
    public byte[] buildSubmitPayload(
            String operator,
            String externalOrderCode,
            Instant createdAt,
            ZoneId protocolZone
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("installProcessNo", externalOrderCode);
        body.put("operateName", operator == null ? "system" : operator);
        body.put("timestamp", TS.format(createdAt.atZone(protocolZone)));
        body.put("localStub", true);
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (RuntimeException exception) {
            throw new BusinessProblem(ProblemCode.INTERNAL_ERROR,
                    "Cannot build GEELY submit payload: " + exception.getMessage());
        }
    }
}
