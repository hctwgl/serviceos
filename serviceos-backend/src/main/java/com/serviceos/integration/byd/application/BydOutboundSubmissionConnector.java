package com.serviceos.integration.byd.application;

import com.serviceos.integration.byd.infrastructure.BydCpimSignatureVerifier;
import com.serviceos.integration.byd.spi.BydCpimSubmitReviewGateway;
import com.serviceos.integration.spi.ConnectorIdentity;
import com.serviceos.integration.spi.OutboundSubmissionConnector;
import com.serviceos.integration.spi.OutboundSubmissionRequest;
import com.serviceos.integration.spi.OutboundTechnicalAcknowledgement;
import com.serviceos.integration.spi.OutboundTransportResult;
import com.serviceos.integration.spi.SignedOutboundRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * BYD CPIM 提审出站连接器：协议签名、单次 HTTP 与 errno 技术 ACK 解释。
 *
 * <p>不写领域表；执行编排委托 {@code OutboundSubmissionPipeline}。</p>
 */
@Component
public class BydOutboundSubmissionConnector implements OutboundSubmissionConnector {
    static final String TASK_TYPE = "integration.byd.submit-review";
    private static final ConnectorIdentity IDENTITY = new ConnectorIdentity(
            "BYD_CPIM", "byd-cpim-submit-review-v7.3.1");
    private static final Set<String> RESPONSE_FIELDS = Set.of("errno", "errmsg", "data");

    private final BydCpimSubmitReviewGateway gateway;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId protocolZone;
    private final String appKey;
    private final BydCpimSignatureVerifier signer;
    private final String credentialVersionId;

    BydOutboundSubmissionConnector(
            BydCpimSubmitReviewGateway gateway,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${serviceos.integration.byd.cpim.zone-id}") ZoneId protocolZone,
            @Value("${serviceos.integration.byd.cpim.app-key}") String appKey,
            @Value("${serviceos.integration.byd.cpim.app-secret}") String appSecret,
            @Value("${serviceos.integration.byd.cpim.credential-version-id:}") String credentialVersionId
    ) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.protocolZone = protocolZone;
        this.appKey = requireText(appKey, "appKey");
        this.signer = new BydCpimSignatureVerifier(appKey, appSecret, clock, protocolZone);
        this.credentialVersionId = credentialVersionId == null ? "" : credentialVersionId.trim();
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
        return "byd-cpim/submit-review";
    }

    @Override
    public Optional<String> preflightErrorCode() {
        if (credentialVersionId.isBlank()) {
            return Optional.of("BYD_CREDENTIAL_VERSION_NOT_CONFIGURED");
        }
        return Optional.empty();
    }

    @Override
    public SignedOutboundRequest prepare(OutboundSubmissionRequest request) {
        Map<String, Object> parameters;
        try {
            parameters = objectMapper.readValue(request.payload(), new TypeReference<>() { });
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("BYD outbound payload is not valid JSON object", exception);
        }
        LocalDate requestDate = LocalDate.now(clock.withZone(protocolZone));
        String nonce = request.attemptId().toString();
        String signature = signer.sign(nonce, requestDate, parameters);
        return new SignedOutboundRequest(
                request.payload(), nonce, requestDate, signature, credentialVersionId, appKey);
    }

    @Override
    public OutboundTransportResult send(SignedOutboundRequest signed) {
        try {
            BydCpimSubmitReviewGateway.Response response = gateway.send(new BydCpimSubmitReviewGateway.Request(
                    signed.appKey(),
                    signed.nonce(),
                    signed.requestDate().toString(),
                    signed.signature(),
                    signed.payload()));
            return OutboundTransportResult.sent(response.httpStatus(), response.body());
        } catch (BydCpimSubmitReviewGateway.TransportException exception) {
            if (exception.kind() == BydCpimSubmitReviewGateway.TransportException.Kind.NOT_SENT) {
                return OutboundTransportResult.notSent(exception.errorCode(), exception);
            }
            return OutboundTransportResult.unknown(exception.errorCode(), exception);
        }
    }

    @Override
    public OutboundTechnicalAcknowledgement interpret(int httpStatus, byte[] body) {
        if (httpStatus < 200 || httpStatus >= 300) {
            return OutboundTechnicalAcknowledgement.unknown("BYD_HTTP_RESULT_UNKNOWN");
        }
        try {
            int errno = normalizeErrno(body);
            if (errno != 0) {
                String reason = "BYD_ERRNO_" + errno;
                return OutboundTechnicalAcknowledgement.rejected(reason, reason);
            }
            return OutboundTechnicalAcknowledgement.accepted("BYD_ERRNO_0", "BYD_ACCEPTED");
        } catch (IllegalArgumentException exception) {
            return OutboundTechnicalAcknowledgement.unknown("BYD_PROTOCOL_RESULT_UNKNOWN");
        }
    }

    private int normalizeErrno(byte[] body) {
        final Map<String, Object> response;
        try {
            response = objectMapper.readValue(body, new TypeReference<>() { });
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("BYD response is not valid JSON", exception);
        }
        if (!RESPONSE_FIELDS.equals(response.keySet())) {
            throw new IllegalArgumentException("BYD response fields are invalid");
        }
        Object errnoValue = response.get("errno");
        Object errmsgValue = response.get("errmsg");
        if (!(errnoValue instanceof Number number)
                || number.doubleValue() != number.intValue()
                || !(errmsgValue instanceof String errmsg)
                || errmsg.isBlank() || !errmsg.equals(errmsg.trim()) || errmsg.length() > 500
                || response.get("data") != null) {
            throw new IllegalArgumentException("BYD response value types are invalid");
        }
        return number.intValue();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
