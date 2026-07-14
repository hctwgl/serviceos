package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceMachineValidatorTest {
    private final EvidenceMachineValidator validator =
            new EvidenceMachineValidator(JsonMapper.builder().build());

    @Test
    void passesFormatSizeAndCapturePolicy() {
        List<EvidenceValidationView> results = validator.evaluate(
                revision("image/png", 100, "{\"captureSource\":\"CAMERA\"}"),
                slot("""
                        {"evidenceKey":"site.photo","mediaType":"PHOTO","required":true,
                         "capture":{"maxSizeBytes":200,"allowGallery":false,"requireRealtimeCapture":true}}
                        """),
                false, Instant.parse("2026-07-14T10:00:00Z"));

        assertThat(results).extracting(EvidenceValidationView::checkType)
                .containsExactly("FORMAT", "SIZE", "CAPTURE_POLICY");
        assertThat(results).allMatch(v -> "PASSED".equals(v.result()));
        assertThat(EvidenceMachineValidator.terminalStatus(results)).isEqualTo("VALIDATED");
    }

    @Test
    void blocksGalleryWhenDisallowedAndUnsupportedBlockChecks() {
        List<EvidenceValidationView> results = validator.evaluate(
                revision("image/png", 100, "{\"captureSource\":\"GALLERY\"}"),
                slot("""
                        {"evidenceKey":"site.photo","mediaType":"PHOTO","required":true,
                         "capture":{"allowGallery":false},
                         "qualityChecks":[{"checkType":"OCR","severity":"BLOCK"},
                                          {"checkType":"BLUR","severity":"WARN"}]}
                        """),
                false, Instant.parse("2026-07-14T10:00:00Z"));

        assertThat(results).anySatisfy(v -> {
            assertThat(v.checkType()).isEqualTo("CAPTURE_POLICY");
            assertThat(v.result()).isEqualTo("FAILED");
            assertThat(v.reasonCode()).isEqualTo("GALLERY_NOT_ALLOWED");
        });
        assertThat(results).anySatisfy(v -> {
            assertThat(v.checkType()).isEqualTo("OCR");
            assertThat(v.result()).isEqualTo("FAILED");
            assertThat(v.reasonCode()).isEqualTo("UNSUPPORTED_CHECK_TYPE");
        });
        assertThat(results).anySatisfy(v -> {
            assertThat(v.checkType()).isEqualTo("BLUR");
            assertThat(v.result()).isEqualTo("SKIPPED");
        });
        assertThat(EvidenceMachineValidator.terminalStatus(results)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void duplicateDigestFailsWhenConfigured() {
        List<EvidenceValidationView> results = validator.evaluate(
                revision("image/png", 10, "{\"captureSource\":\"CAMERA\"}"),
                slot("""
                        {"evidenceKey":"site.photo","mediaType":"PHOTO","required":true,
                         "qualityChecks":[{"checkType":"DUPLICATE","severity":"BLOCK"}]}
                        """),
                true, Instant.parse("2026-07-14T10:00:00Z"));

        assertThat(results).anySatisfy(v -> {
            assertThat(v.checkType()).isEqualTo("DUPLICATE");
            assertThat(v.result()).isEqualTo("FAILED");
        });
        assertThat(EvidenceMachineValidator.terminalStatus(results)).isEqualTo("VALIDATION_FAILED");
    }

    private static EvidenceRevisionView revision(String mime, long size, String capture) {
        return new EvidenceRevisionView(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, UUID.randomUUID(), "a".repeat(64), mime, size, capture,
                "VALIDATING", UUID.randomUUID(), "cmd", "tech", Instant.parse("2026-07-14T09:00:00Z"),
                List.of(), null, null, null, null);
    }

    private static EvidenceSlotView slot(String definition) {
        return new EvidenceSlotView(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "survey.site", "1.0.0", "b".repeat(64), "site.photo", "default",
                "现场照片", "PHOTO", true, 1, 2, "c".repeat(64), "{\"kind\":\"FIXED\"}",
                definition, "d".repeat(64), "MISSING", Instant.parse("2026-07-14T08:00:00Z"));
    }
}
