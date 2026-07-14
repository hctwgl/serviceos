package com.serviceos.evidence.application;

import com.serviceos.shared.BusinessProblem;
import com.serviceos.shared.ProblemCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

/**
 * M38 最小可演进 CaptureMetadata。客户端声明不是 GPS/水印/EXIF 权威事实；
 * onBehalfOf 在本切片失败关闭。
 */
final class CaptureMetadataValidator {
    private static final Set<String> SOURCES = Set.of(
            "CAMERA", "GALLERY", "FILE", "GENERATED", "EXTERNAL");

    private CaptureMetadataValidator() {
    }

    static String normalize(ObjectMapper objectMapper, JsonNode input, Instant receivedAt, String uploadedBy) {
        if (input == null || !input.isObject()) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "captureMetadata must be an object");
        }
        String source = text(input, "captureSource");
        if (source == null) {
            source = text(input, "source");
        }
        if (source == null || !SOURCES.contains(source.toUpperCase(Locale.ROOT))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "captureSource must be CAMERA, GALLERY, FILE, GENERATED or EXTERNAL");
        }
        source = source.toUpperCase(Locale.ROOT);

        Instant capturedAt = instant(text(input, "capturedAt"));
        if (capturedAt == null) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "capturedAt is required");
        }
        if (capturedAt.isAfter(receivedAt.plusSeconds(300))) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "capturedAt is too far in the future");
        }

        if (hasText(input, "onBehalfOf") || hasText(input, "delegationRef")) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                    "on-behalf evidence upload is not enabled in M38");
        }

        Boolean offline = input.get("offlineFlag") != null && !input.get("offlineFlag").isNull()
                ? input.get("offlineFlag").asBoolean()
                : (input.get("offline") != null && !input.get("offline").isNull()
                ? input.get("offline").asBoolean() : Boolean.FALSE);

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("captureSource", source);
        normalized.put("capturedAt", capturedAt.toString());
        normalized.put("receivedAt", receivedAt.toString());
        putOptional(normalized, "deviceId", text(input, "deviceId"), 128);
        putOptional(normalized, "appVersion", text(input, "appVersion"), 64);
        normalized.put("offlineFlag", offline);
        normalized.put("uploadedBy", uploadedBy);
        // GPS / watermark / EXIF 扩展位保留，但不把客户端坐标提升为已校验事实。
        if (input.get("location") != null && !input.get("location").isNull()) {
            normalized.set("locationClaim", input.get("location"));
            normalized.put("locationVerified", false);
        }
        if (hasText(input, "checksum")) {
            String checksum = text(input, "checksum").toLowerCase(Locale.ROOT);
            if (!checksum.matches("^[0-9a-f]{64}$")) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "captureMetadata.checksum invalid");
            }
            normalized.put("clientChecksum", checksum);
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception exception) {
            throw new IllegalStateException("CaptureMetadata serialization failed", exception);
        }
    }

    private static void putOptional(ObjectNode node, String field, String value, int max) {
        if (value == null) {
            return;
        }
        if (value.isBlank() || value.length() > max) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, field + " is invalid");
        }
        node.put(field, value);
    }

    private static boolean hasText(JsonNode input, String field) {
        String value = text(input, field);
        return value != null && !value.isBlank();
    }

    private static String text(JsonNode input, String field) {
        JsonNode node = input.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception exception) {
            throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "timestamp must be ISO-8601 instant");
        }
    }
}
