package com.serviceos.integration.geely.application;

import com.serviceos.integration.spi.OutboundSubmissionRequest;
import com.serviceos.integration.spi.OutboundTechnicalAcknowledgement;
import com.serviceos.integration.spi.OutboundTransportResult;
import com.serviceos.integration.spi.SignedOutboundRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GeelyOutboundSubmissionConnectorTest {

    @Test
    void preparesAesEnvelopeAndAcceptsLocalStubResponse() {
        var connector = new GeelyOutboundSubmissionConnector(
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse("2026-07-19T12:00:00Z"), ZoneOffset.UTC),
                "GENERAL_KEY_DEMO",
                "IV_DEMO_90123456",
                "LOCAL_ACCEPT");
        assertThat(connector.preflightErrorCode()).isEmpty();
        byte[] payload = "{\"installProcessNo\":\"IN1\"}".getBytes(StandardCharsets.UTF_8);
        SignedOutboundRequest signed = connector.prepare(new OutboundSubmissionRequest(
                "tenant-geely",
                UUID.randomUUID(),
                UUID.randomUUID(),
                payload,
                "a".repeat(64)));
        assertThat(signed.signature()).startsWith("LOCAL_STUB_");
        OutboundTransportResult transport = connector.send(signed);
        assertThat(transport.kind()).isEqualTo(OutboundTransportResult.Kind.SENT);
        OutboundTechnicalAcknowledgement ack = connector.interpret(
                transport.httpStatus(), transport.body());
        assertThat(ack.outcome()).isEqualTo(OutboundTechnicalAcknowledgement.Outcome.ACCEPTED);
    }

    @Test
    void profileClaimsGeelyCreateLineage() {
        var profile = new GeelyOutboundReviewSubmissionProfile();
        assertThat(profile.supportsInboundLineage("geely-haohan-v1.3-local", "CREATE_WORK_ORDER"))
                .isTrue();
        assertThat(profile.supportsInboundLineage("byd-cpim-v7.3.1", "CREATE_WORK_ORDER")).isFalse();
        assertThat(profile.extractExternalOrderCode("GEELY:INSTALL:IN2026")).isEqualTo("IN2026");
        assertThat(profile.taskType()).isEqualTo("integration.geely.submit-settlement");
    }
}
