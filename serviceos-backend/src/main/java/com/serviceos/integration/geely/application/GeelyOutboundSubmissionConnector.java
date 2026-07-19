package com.serviceos.integration.geely.application;

import com.serviceos.integration.geely.infrastructure.GeelyAesCipher;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.OutboundSubmissionConnector;
import com.serviceos.integration.spi.OutboundSubmissionRequest;
import com.serviceos.integration.spi.OutboundTechnicalAcknowledgement;
import com.serviceos.integration.spi.OutboundTransportResult;
import com.serviceos.integration.spi.SignedOutboundRequest;
import com.serviceos.shared.Sha256;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 吉利提审出站本地 stub：AES 包装 payload，不访问真实 Sandbox。
 *
 * <p>默认返回本地技术 ACK ACCEPTED；配置 {@code serviceos.integration.geely.outbound-mode=UNKNOWN}
 * 可演练 UNKNOWN。真实 OpenAPI 签名/HTTP 为 BLOCKED_EXTERNAL。</p>
 */
@Component
public class GeelyOutboundSubmissionConnector implements OutboundSubmissionConnector {
    static final String TASK_TYPE = "integration.geely.submit-settlement";
    private static final ConnectorIdentity IDENTITY = new ConnectorIdentity(
            GeelyInboundCreateOrderService.CONNECTOR_CODE,
            "geely-haohan-submit-settlement-v1.3-local");

    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String accessKey;
    private final String aesIv;
    private final String outboundMode;

    GeelyOutboundSubmissionConnector(
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${serviceos.integration.geely.access-key:GENERAL_KEY_DEMO}") String accessKey,
            @Value("${serviceos.integration.geely.aes-iv:IV_DEMO_90123456}") String aesIv,
            @Value("${serviceos.integration.geely.outbound-mode:LOCAL_ACCEPT}") String outboundMode
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.accessKey = accessKey.trim();
        this.aesIv = aesIv.trim();
        this.outboundMode = outboundMode == null ? "LOCAL_ACCEPT" : outboundMode.trim();
    }

    @Override
    public ConnectorIdentity identity() {
        return IDENTITY;
    }

    @Override
    public String taskType() {
        return TASK_TYPE;
    }

    @Override
    public String responseStorageSegment() {
        return "geely/submit-settlement";
    }

    @Override
    public Optional<String> preflightErrorCode() {
        if (accessKey.isBlank() || aesIv.isBlank()) {
            return Optional.of("GEELY_AES_CREDENTIAL_NOT_CONFIGURED");
        }
        return Optional.empty();
    }

    @Override
    public SignedOutboundRequest prepare(OutboundSubmissionRequest request) {
        String plain = new String(request.payload(), StandardCharsets.UTF_8);
        String cipher = GeelyAesCipher.encryptToBase64(plain, accessKey, aesIv);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("providerNo", "SEA_LOCAL");
        envelope.put("timestamp", clock.instant().toString());
        envelope.put("data", cipher);
        byte[] signedPayload = objectMapper.writeValueAsBytes(envelope);
        String digest = Sha256.digest(signedPayload);
        return new SignedOutboundRequest(
                signedPayload,
                digest.substring(0, 16),
                LocalDate.ofInstant(clock.instant(), java.time.ZoneOffset.UTC),
                "LOCAL_STUB_" + digest.substring(0, 12),
                "geely-local-aes-v1",
                accessKey.length() > 16 ? accessKey.substring(0, 16) : accessKey);
    }

    @Override
    public OutboundTransportResult send(SignedOutboundRequest signed) {
        if ("NOT_SENT".equalsIgnoreCase(outboundMode)) {
            return OutboundTransportResult.notSent("GEELY_LOCAL_NOT_SENT", null);
        }
        if ("UNKNOWN".equalsIgnoreCase(outboundMode)) {
            return OutboundTransportResult.unknown("GEELY_LOCAL_UNKNOWN", null);
        }
        byte[] body = "{\"code\":\"0\",\"message\":\"LOCAL_STUB_ACCEPTED\"}".getBytes(StandardCharsets.UTF_8);
        return OutboundTransportResult.sent(200, body);
    }

    @Override
    public OutboundTechnicalAcknowledgement interpret(int httpStatus, byte[] body) {
        if (httpStatus / 100 != 2) {
            return OutboundTechnicalAcknowledgement.unknown("GEELY_HTTP_" + httpStatus);
        }
        String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        if (text.contains("\"code\":\"0\"") || text.contains("\"code\": \"0\"")) {
            return OutboundTechnicalAcknowledgement.accepted(
                    "GEELY_LOCAL_ACCEPTED", "LOCAL_STUB_TECHNICAL_ACK");
        }
        return OutboundTechnicalAcknowledgement.rejected(
                "GEELY_LOCAL_REJECTED", "LOCAL_STUB_TECHNICAL_NACK");
    }
}
