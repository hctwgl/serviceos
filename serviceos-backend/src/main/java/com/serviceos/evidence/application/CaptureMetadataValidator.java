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
 * 客户端 onBehalfOf/delegationRef/onBehalfReason 失败关闭；M201 仅允许服务端命令级代补字段写入。
 */
final class CaptureMetadataValidator {
    private static final Set<String> SOURCES = Set.of(
            "CAMERA", "GALLERY", "FILE", "GENERATED", "EXTERNAL");

    private CaptureMetadataValidator() {
    }

    static String normalize(ObjectMapper objectMapper, JsonNode input, Instant receivedAt, String uploadedBy) {
        return normalize(objectMapper, input, receivedAt, uploadedBy, null, null);
    }

    /**
     * @param onBehalfOf     服务端命令级代补对象；非空时与 {@code onBehalfReason} 一并写入规范化 JSON
     * @param onBehalfReason 服务端命令级代补原因
     */
    static String normalize(
            ObjectMapper objectMapper,
            JsonNode input,
            Instant receivedAt,
            String uploadedBy,
            String onBehalfOf,
            String onBehalfReason
    ) {
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

        // 客户端不得伪造代补关系；Admin 普通 submit 与 Portal 路径均失败关闭 JSON 内字段。
        if (hasText(input, "onBehalfOf") || hasText(input, "delegationRef") || hasText(input, "onBehalfReason")) {
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

        boolean hasOnBehalfOf = onBehalfOf != null && !onBehalfOf.isBlank();
        boolean hasOnBehalfReason = onBehalfReason != null && !onBehalfReason.isBlank();
        if (hasOnBehalfOf || hasOnBehalfReason) {
            if (!hasOnBehalfOf || !hasOnBehalfReason) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED,
                        "onBehalfOf and onBehalfReason must both be provided by the service");
            }
            String behalfOf = onBehalfOf.trim();
            String reason = onBehalfReason.trim();
            if (behalfOf.length() > 128 || reason.length() > 500) {
                throw new BusinessProblem(ProblemCode.VALIDATION_FAILED, "on-behalf fields are invalid");
            }
            normalized.put("onBehalfOf", behalfOf);
            normalized.put("onBehalfReason", reason);
            normalized.put("uploadedRole", "NETWORK_OPERATOR");
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
