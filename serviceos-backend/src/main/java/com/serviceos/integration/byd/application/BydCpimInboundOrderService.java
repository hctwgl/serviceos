package com.serviceos.integration.byd.application;

import com.serviceos.integration.byd.api.BydCpimInboundOrderResponse;
import com.serviceos.integration.byd.api.BydCpimInstallOrderPayload;
import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import com.serviceos.integration.byd.infrastructure.BydCpimPayloadDigest;
import com.serviceos.integration.byd.infrastructure.BydCpimReplayConflictException;
import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.infrastructure.JdbcBydCpimReplayGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * BYD CPIM 安装订单入站安全边界。
 *
 * <p>处理顺序固定为：验签 → 摘要 → 防重放 → DTO 校验 → 反腐层映射。
 * 当前阶段只确认外部订单可安全接收，不在此服务中直接创建 WorkOrder。</p>
 */
@Service
public class BydCpimInboundOrderService {
    private final ObjectMapper objectMapper;
    private final JdbcBydCpimReplayGuard replayGuard;
    private final BydCpimOrderMapper mapper;
    private final BydCpimSignatureVerifier signatureVerifier;

    public BydCpimInboundOrderService(
            ObjectMapper objectMapper,
            JdbcBydCpimReplayGuard replayGuard,
            @Value("${serviceos.integration.byd.cpim.app-key:local-byd-app-key}") String appKey,
            @Value("${serviceos.integration.byd.cpim.app-secret:local-byd-app-secret-change-me}") String appSecret,
            @Value("${serviceos.integration.byd.cpim.allowed-clock-skew:PT5M}") Duration allowedClockSkew) {
        this.objectMapper = objectMapper;
        this.replayGuard = replayGuard;
        this.mapper = new BydCpimOrderMapper();
        this.signatureVerifier = new BydCpimSignatureVerifier(
                appKey, appSecret, Clock.systemUTC(), allowedClockSkew);
    }

    public BydCpimInboundOrderResponse receive(
            BydCpimSignatureHeaders headers,
            Map<String, Object> rawParameters) {
        var verification = signatureVerifier.verify(headers, rawParameters);
        if (!verification.valid()) {
            return BydCpimInboundOrderResponse.rejected(
                    verification.reason().name(), "request signature verification failed");
        }

        String payloadDigest;
        try {
            payloadDigest = BydCpimPayloadDigest.sha256(rawParameters);
        } catch (IllegalArgumentException exception) {
            return BydCpimInboundOrderResponse.rejected("INVALID_PAYLOAD", exception.getMessage());
        }

        try {
            var replayDecision = replayGuard.register(
                    headers.appKey(), headers.nonce(), headers.currentTime().getEpochSecond(), payloadDigest);

            BydCpimInstallOrderPayload payload = objectMapper.convertValue(
                    rawParameters, BydCpimInstallOrderPayload.class);
            BydCpimMappedOrder mapped = mapper.map(payload);
            BydCpimInboundOrderResponse response = BydCpimInboundOrderResponse.accepted(
                    mapped.externalOrderCode(),
                    mapped.adapterVersion(),
                    mapped.mappingVersion(),
                    replayDecision.kind() == com.serviceos.integration.byd.infrastructure.BydCpimReplayDecision.Kind.REPLAY);

            replayGuard.complete(
                    headers.appKey(), headers.nonce(), headers.currentTime().getEpochSecond(),
                    responseDigest(response));
            return response;
        } catch (BydCpimReplayConflictException exception) {
            return BydCpimInboundOrderResponse.rejected(
                    "REPLAY_CONFLICT", "nonce was already used with a different payload");
        } catch (IllegalArgumentException exception) {
            return BydCpimInboundOrderResponse.rejected("INVALID_ORDER", exception.getMessage());
        }
    }

    private static String responseDigest(BydCpimInboundOrderResponse response) {
        return BydCpimPayloadDigest.sha256(Map.of(
                "success", response.success(),
                "code", response.code(),
                "orderCode", response.orderCode() == null ? "" : response.orderCode(),
                "adapterVersion", response.adapterVersion() == null ? "" : response.adapterVersion(),
                "mappingVersion", response.mappingVersion() == null ? "" : response.mappingVersion()));
    }
}
