package com.serviceos.integration.byd.infrastructure;

import com.serviceos.integration.byd.api.BydCpimSignatureHeaders;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BydCpimSignatureVerifierTest {
    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
    private final BydCpimSignatureVerifier verifier = new BydCpimSignatureVerifier(
            "test-app-key", "test-app-secret", Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

    @Test
    void verifiesSignatureIndependentlyOfInputMapIterationOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("orderCode", "BYD-SD-OCEAN-001");
        first.put("carBrand", "40");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("carBrand", "40");
        second.put("orderCode", "BYD-SD-OCEAN-001");

        String signature = verifier.sign("test-app-key", "nonce-001", NOW.getEpochSecond(), first);
        BydCpimSignatureHeaders headers = new BydCpimSignatureHeaders(
                "test-app-key", "nonce-001", NOW, signature);

        assertThat(verifier.verify(headers, second).valid()).isTrue();
    }

    @Test
    void rejectsUnknownAppKeyExpiredTimestampAndMutatedPayload() {
        Map<String, Object> payload = Map.of("orderCode", "BYD-SD-OCEAN-001");
        String signature = verifier.sign("test-app-key", "nonce-001", NOW.getEpochSecond(), payload);

        assertThat(verifier.verify(new BydCpimSignatureHeaders(
                "unknown-app", "nonce-001", NOW, signature), payload).reason())
                .isEqualTo(BydCpimSignatureVerifier.Reason.UNKNOWN_APP_KEY);

        assertThat(verifier.verify(new BydCpimSignatureHeaders(
                "test-app-key", "nonce-001", NOW.minus(Duration.ofMinutes(6)), signature), payload).reason())
                .isEqualTo(BydCpimSignatureVerifier.Reason.TIMESTAMP_OUT_OF_WINDOW);

        assertThat(verifier.verify(new BydCpimSignatureHeaders(
                "test-app-key", "nonce-001", NOW, signature),
                Map.of("orderCode", "MUTATED")).reason())
                .isEqualTo(BydCpimSignatureVerifier.Reason.SIGNATURE_MISMATCH);
    }

    @Test
    void refusesNestedSigningParametersUntilProtocolCanonicalizationIsExplicit() {
        assertThatThrownBy(() -> verifier.sign(
                "test-app-key", "nonce-001", NOW.getEpochSecond(), Map.of("nested", Map.of("a", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nested CPIM signing parameter");
    }
}
