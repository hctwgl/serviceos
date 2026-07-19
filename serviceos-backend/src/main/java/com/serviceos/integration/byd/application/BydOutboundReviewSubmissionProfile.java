package com.serviceos.integration.byd.application;

import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.OutboundReviewSubmissionProfile;
import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * BYD CPIM 提审创建档案：业务键前缀与 lineage 认领仅留在 OEM 适配包。
 *
 * <p>提审 Payload 形状由冻结 Bundle OUTBOUND INTEGRATION Mapping 生成（M331）。</p>
 */
@Component
public class BydOutboundReviewSubmissionProfile implements OutboundReviewSubmissionProfile {
    private static final ConnectorIdentity IDENTITY = new ConnectorIdentity(
            "BYD_CPIM", "byd-cpim-v7.3.1");
    private static final String INSTALL_BUSINESS_PREFIX = "BYD:INSTALL:";

    @Override
    public ConnectorIdentity identity() {
        return IDENTITY;
    }

    @Override
    public String callbackMappingVersion() {
        return "byd-ocean-shandong-review-callback-v1";
    }

    @Override
    public String businessMessageType() {
        return "SUBMIT_CLIENT_REVIEW";
    }

    @Override
    public String taskType() {
        return BydOutboundSubmissionConnector.TASK_TYPE;
    }

    @Override
    public String failurePolicy() {
        return "byd-submit-review-fail-closed-v1";
    }

    @Override
    public String clientPolicy() {
        return "byd-client-review-v1";
    }

    @Override
    public String payloadStorageSegment() {
        return "byd-cpim/submit-review";
    }

    @Override
    public boolean supportsInboundLineage(String inboundConnectorVersionId, String inboundMessageType) {
        return IDENTITY.connectorVersionId().equals(inboundConnectorVersionId)
                && "CREATE_WORK_ORDER".equals(inboundMessageType);
    }

    @Override
    public String extractExternalOrderCode(String inboundBusinessKey) {
        if (inboundBusinessKey == null || !inboundBusinessKey.startsWith(INSTALL_BUSINESS_PREFIX)) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "BYD inbound CanonicalMessage has an invalid business key");
        }
        String orderCode = inboundBusinessKey.substring(INSTALL_BUSINESS_PREFIX.length());
        if (orderCode.isBlank() || !orderCode.equals(orderCode.trim()) || orderCode.length() > 50) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "orderCode is invalid");
        }
        return orderCode;
    }

    @Override
    public String submitBusinessKey(String externalOrderCode, String snapshotContentDigest) {
        return "BYD:SUBMIT_REVIEW:" + externalOrderCode + ":" + snapshotContentDigest;
    }

    @Override
    public String clientSubmissionRef(UUID deliveryId) {
        return "BYD:SUBMIT_REVIEW:" + deliveryId;
    }

    @Override
    public String callbackBatchRef(UUID deliveryId) {
        return "BYD:REVIEW_CALLBACK:" + deliveryId;
    }
}
