package com.serviceos.integration.byd.application;

import com.serviceos.integration.byd.spi.BydCpimSubmitReviewGateway;
import com.serviceos.integration.spi.OutboundSubmissionRequest;
import com.serviceos.integration.spi.OutboundTechnicalAcknowledgement;
import com.serviceos.integration.spi.OutboundTransportResult;
import com.serviceos.integration.spi.SignedOutboundRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BydOutboundSubmissionConnectorTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-19T01:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void preflightFailsWhenCredentialVersionMissing() {
        BydOutboundSubmissionConnector connector = connector("", gatewaySent(200, errnoBody(0)));
        assertThat(connector.preflightErrorCode()).contains("BYD_CREDENTIAL_VERSION_NOT_CONFIGURED");
        assertThat(connector.taskType()).isEqualTo("integration.byd.submit-review");
        assertThat(connector.identity().connectorCode()).isEqualTo("BYD_CPIM");
    }

    @Test
    void interpretAcceptsStrictErrnoZero() {
        BydOutboundSubmissionConnector connector = connector("cred-v1", gatewaySent(200, errnoBody(0)));
        OutboundTechnicalAcknowledgement ack = connector.interpret(200, errnoBody(0));
        assertThat(ack.outcome()).isEqualTo(OutboundTechnicalAcknowledgement.Outcome.ACCEPTED);
        assertThat(ack.reasonCode()).isEqualTo("BYD_ERRNO_0");
        assertThat(ack.acknowledgementReasonCode()).isEqualTo("BYD_ACCEPTED");
    }

    @Test
    void interpretRejectsNonZeroErrnoAsFinalTechnicalRejection() {
        BydOutboundSubmissionConnector connector = connector("cred-v1", gatewaySent(200, errnoBody(7)));
        OutboundTechnicalAcknowledgement ack = connector.interpret(200, errnoBody(7));
        assertThat(ack.outcome()).isEqualTo(OutboundTechnicalAcknowledgement.Outcome.REJECTED);
        assertThat(ack.reasonCode()).isEqualTo("BYD_ERRNO_7");
    }

    @Test
    void interpretMarksNon2xxAndInvalidBodyAsUnknown() {
        BydOutboundSubmissionConnector connector = connector("cred-v1", gatewaySent(500, "x".getBytes()));
        assertThat(connector.interpret(500, errnoBody(0)).outcome())
                .isEqualTo(OutboundTechnicalAcknowledgement.Outcome.UNKNOWN);
        assertThat(connector.interpret(200, "{\"errno\":0}".getBytes(StandardCharsets.UTF_8)).outcome())
                .isEqualTo(OutboundTechnicalAcknowledgement.Outcome.UNKNOWN);
    }

    @Test
    void sendMapsNotSentAndUnknownTransportKinds() {
        BydOutboundSubmissionConnector notSent = connector("cred-v1", (request) -> {
            throw new BydCpimSubmitReviewGateway.TransportException(
                    BydCpimSubmitReviewGateway.TransportException.Kind.NOT_SENT, "DNS", null);
        });
        BydOutboundSubmissionConnector unknown = connector("cred-v1", (request) -> {
            throw new BydCpimSubmitReviewGateway.TransportException(
                    BydCpimSubmitReviewGateway.TransportException.Kind.UNKNOWN, "TIMEOUT", null);
        });
        SignedOutboundRequest signed = new SignedOutboundRequest(
                "{\"operatePerson\":\"r\"}".getBytes(StandardCharsets.UTF_8),
                UUID.randomUUID().toString(),
                LocalDate.of(2026, 7, 19),
                "a".repeat(64),
                "cred-v1",
                "app-key");
        assertThat(notSent.send(signed).kind()).isEqualTo(OutboundTransportResult.Kind.NOT_SENT);
        assertThat(unknown.send(signed).kind()).isEqualTo(OutboundTransportResult.Kind.UNKNOWN);
    }

    @Test
    void prepareSignsFrozenPayloadWithAttemptNonce() {
        BydOutboundSubmissionConnector connector = connector("cred-v1", gatewaySent(200, errnoBody(0)));
        UUID attemptId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        byte[] payload = "{\"operatePerson\":\"reviewer-1\",\"orderCode\":\"ORDER-1\",\"commitDate\":\"2026-07-15 16:00:00\"}"
                .getBytes(StandardCharsets.UTF_8);
        SignedOutboundRequest signed = connector.prepare(new OutboundSubmissionRequest(
                "tenant-a", UUID.randomUUID(), attemptId, payload, "digest"));
        assertThat(signed.nonce()).isEqualTo(attemptId.toString());
        assertThat(signed.credentialVersionId()).isEqualTo("cred-v1");
        assertThat(signed.signature()).hasSize(64);
        assertThat(signed.requestDate()).isEqualTo(LocalDate.of(2026, 7, 19));
    }

    private BydOutboundSubmissionConnector connector(String credentialVersion, BydCpimSubmitReviewGateway gateway) {
        return new BydOutboundSubmissionConnector(
                gateway,
                mapper,
                clock,
                ZoneId.of("Asia/Shanghai"),
                "app-key",
                "app-secret-value-32chars!!!!!!!!!",
                credentialVersion);
    }

    private static BydCpimSubmitReviewGateway gatewaySent(int status, byte[] body) {
        return request -> new BydCpimSubmitReviewGateway.Response(status, body);
    }

    private static byte[] errnoBody(int errno) {
        return ("{\"errno\":" + errno + ",\"errmsg\":\"msg\",\"data\":null}")
                .getBytes(StandardCharsets.UTF_8);
    }
}
