package com.serviceos.evidence.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceSlotStatusProjectorTest {
    @Test
    void projectsMissingPartialAndSatisfiedFromCountingItems() {
        assertThat(EvidenceSlotStatusProjector.project(1, 3, 0)).isEqualTo("MISSING");
        assertThat(EvidenceSlotStatusProjector.project(2, 5, 1)).isEqualTo("PARTIAL");
        assertThat(EvidenceSlotStatusProjector.project(1, 3, 1)).isEqualTo("SATISFIED");
        assertThat(EvidenceSlotStatusProjector.project(0, null, 0)).isEqualTo("SATISFIED");
    }

    @Test
    void onlyCleanPipelineStatusesCountTowardSlot() {
        assertThat(EvidenceSlotStatusProjector.countsTowardSlot("STORED")).isTrue();
        assertThat(EvidenceSlotStatusProjector.countsTowardSlot("VALIDATING")).isTrue();
        assertThat(EvidenceSlotStatusProjector.countsTowardSlot("VALIDATED")).isTrue();
        assertThat(EvidenceSlotStatusProjector.countsTowardSlot("QUARANTINED")).isFalse();
        assertThat(EvidenceSlotStatusProjector.countsTowardSlot("VALIDATION_FAILED")).isFalse();
        assertThat(EvidenceSlotStatusProjector.countsTowardSlot("INVALIDATED")).isFalse();
    }
}

class CaptureMetadataValidatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesMinimalCaptureMetadataAndRejectsOnBehalf() {
        Instant receivedAt = Instant.parse("2026-07-14T10:00:00Z");
        ObjectNode input = objectMapper.createObjectNode();
        input.put("captureSource", "CAMERA");
        input.put("capturedAt", "2026-07-14T09:59:00Z");
        input.put("deviceId", "DEVICE-1");
        input.put("offline", true);

        String normalized = CaptureMetadataValidator.normalize(
                objectMapper, input, receivedAt, "technician-1");
        assertThat(normalized).contains("\"uploadedBy\":\"technician-1\"");
        assertThat(normalized).contains("\"offlineFlag\":true");
        assertThat(normalized).contains("\"receivedAt\":\"2026-07-14T10:00:00Z\"");

        ObjectNode onBehalf = input.deepCopy();
        onBehalf.put("onBehalfOf", "other-user");
        assertThatThrownBy(() -> CaptureMetadataValidator.normalize(
                objectMapper, onBehalf, receivedAt, "technician-1"))
                .hasMessageContaining("on-behalf");
    }
}
