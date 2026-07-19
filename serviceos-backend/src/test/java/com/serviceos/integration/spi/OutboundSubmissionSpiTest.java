package com.serviceos.integration.spi;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundSubmissionSpiTest {

    @Test
    void transportResultDistinguishesNotSentFromUnknown() {
        OutboundTransportResult notSent = OutboundTransportResult.notSent("DNS_FAILED", null);
        OutboundTransportResult unknown = OutboundTransportResult.unknown("TIMEOUT", null);
        OutboundTransportResult sent = OutboundTransportResult.sent(200, "{\"ok\":true}".getBytes());

        assertThat(notSent.kind()).isEqualTo(OutboundTransportResult.Kind.NOT_SENT);
        assertThat(unknown.kind()).isEqualTo(OutboundTransportResult.Kind.UNKNOWN);
        assertThat(sent.httpStatus()).isEqualTo(200);
        assertThat(new String(sent.body())).contains("ok");
    }

    @Test
    void technicalAckKeepsBusinessAckSeparateFromAcceptance() {
        OutboundTechnicalAcknowledgement accepted =
                OutboundTechnicalAcknowledgement.accepted("BYD_ERRNO_0", "BYD_ACCEPTED");
        OutboundTechnicalAcknowledgement rejected =
                OutboundTechnicalAcknowledgement.rejected("BYD_ERRNO_1", "BYD_ERRNO_1");
        OutboundTechnicalAcknowledgement unknown =
                OutboundTechnicalAcknowledgement.unknown("BYD_PROTOCOL_RESULT_UNKNOWN");

        assertThat(accepted.outcome()).isEqualTo(OutboundTechnicalAcknowledgement.Outcome.ACCEPTED);
        assertThat(rejected.outcome()).isEqualTo(OutboundTechnicalAcknowledgement.Outcome.REJECTED);
        assertThat(unknown.acknowledgementReasonCode()).isNull();
    }

    @Test
    void connectorFailureClassificationsAreExplicit() {
        assertThat(ConnectorFailure.finalFailure("X").classification())
                .isEqualTo(ConnectorFailure.Classification.FINAL);
        assertThat(ConnectorFailure.unknown("Y").classification())
                .isEqualTo(ConnectorFailure.Classification.UNKNOWN);
        assertThat(ConnectorFailure.localRetryable("Z").classification())
                .isEqualTo(ConnectorFailure.Classification.LOCAL_RETRYABLE);
    }

    @Test
    void signedRequestRejectsBlankCredentialVersion() {
        assertThatThrownBy(() -> new SignedOutboundRequest(
                "{}".getBytes(), "nonce", LocalDate.of(2026, 7, 19), "sig", " ", "app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credentialVersionId");
    }

    @Test
    void submissionRequestClonesPayload() {
        byte[] payload = "{\"a\":1}".getBytes();
        OutboundSubmissionRequest request = new OutboundSubmissionRequest(
                "tenant-a", UUID.randomUUID(), UUID.randomUUID(), payload, "digest");
        payload[0] = 'x';
        assertThat(request.payload()[0]).isEqualTo((byte) '{');
    }
}
