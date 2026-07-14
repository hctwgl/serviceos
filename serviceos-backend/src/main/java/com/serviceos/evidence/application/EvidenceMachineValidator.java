package com.serviceos.evidence.application;

import com.serviceos.evidence.api.EvidenceRevisionView;
import com.serviceos.evidence.api.EvidenceSlotView;
import com.serviceos.evidence.api.EvidenceValidationView;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 确定性机器校验：FORMAT / SIZE / CAPTURE_POLICY / DUPLICATE / HISTORICAL_IMAGE。
 * 未实现的 BLOCK 检查失败关闭；WARN 记录为 SKIPPED，不阻塞 VALIDATED。
 */
final class EvidenceMachineValidator {
    static final String VALIDATOR_NAME = "evidence.machine-validation";
    static final String VALIDATOR_VERSION = "1.0.0";

    private static final Set<String> SUPPORTED_CONFIGURED = Set.of("DUPLICATE", "HISTORICAL_IMAGE");

    private final ObjectMapper objectMapper;

    EvidenceMachineValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    List<EvidenceValidationView> evaluate(
            EvidenceRevisionView revision,
            EvidenceSlotView slot,
            boolean duplicateDigestExists,
            Instant now
    ) {
        JsonNode definition = read(slot.requirementDefinitionJson());
        JsonNode capture = definition.path("capture");
        JsonNode captureMeta = read(revision.captureMetadataJson());
        List<EvidenceValidationView> results = new ArrayList<>();

        results.add(formatCheck(revision, slot.mediaType(), now));
        if (capture.has("maxSizeBytes") && capture.path("maxSizeBytes").canConvertToLong()) {
            results.add(sizeCheck(revision, capture.path("maxSizeBytes").asLong(), now));
        }
        results.add(capturePolicyCheck(revision, capture, captureMeta, now));

        for (JsonNode check : definition.path("qualityChecks")) {
            results.add(configuredCheck(revision, check, duplicateDigestExists, now));
        }
        for (JsonNode check : definition.path("contentChecks")) {
            results.add(configuredCheck(revision, check, duplicateDigestExists, now));
        }
        return List.copyOf(results);
    }

    static String terminalStatus(List<EvidenceValidationView> validations) {
        for (EvidenceValidationView validation : validations) {
            if ("BLOCK".equals(validation.severity()) && "FAILED".equals(validation.result())) {
                return "VALIDATION_FAILED";
            }
        }
        return "VALIDATED";
    }

    private EvidenceValidationView formatCheck(
            EvidenceRevisionView revision, String mediaType, Instant now
    ) {
        boolean passed = mimeMatches(mediaType, revision.mimeType());
        return validation(
                revision, "FORMAT", "BLOCK",
                passed ? "PASSED" : "FAILED",
                passed ? null : "MIME_TYPE_MISMATCH",
                passed ? "MIME matches mediaType" : "MIME does not match mediaType " + mediaType,
                details("mediaType", mediaType, "mimeType", revision.mimeType()),
                now);
    }

    private EvidenceValidationView sizeCheck(
            EvidenceRevisionView revision, long maxSizeBytes, Instant now
    ) {
        boolean passed = revision.sizeBytes() <= maxSizeBytes;
        return validation(
                revision, "SIZE", "BLOCK",
                passed ? "PASSED" : "FAILED",
                passed ? null : "SIZE_EXCEEDED",
                passed ? "Size within limit" : "Size exceeds maxSizeBytes",
                details("sizeBytes", revision.sizeBytes(), "maxSizeBytes", maxSizeBytes),
                now);
    }

    private EvidenceValidationView capturePolicyCheck(
            EvidenceRevisionView revision,
            JsonNode capture,
            JsonNode captureMeta,
            Instant now
    ) {
        String source = textOrEmpty(captureMeta, "captureSource").toUpperCase(Locale.ROOT);
        boolean allowGallery = !capture.has("allowGallery") || capture.path("allowGallery").asBoolean(true);
        boolean requireRealtime = capture.has("requireRealtimeCapture")
                && capture.path("requireRealtimeCapture").asBoolean(false);
        boolean requireGps = capture.has("requireGps") && capture.path("requireGps").asBoolean(false);

        if (!allowGallery && "GALLERY".equals(source)) {
            return validation(
                    revision, "CAPTURE_POLICY", "BLOCK", "FAILED", "GALLERY_NOT_ALLOWED",
                    "Gallery capture is not allowed for this requirement",
                    details("captureSource", source, "allowGallery", false), now);
        }
        if (requireRealtime && !"CAMERA".equals(source)) {
            return validation(
                    revision, "CAPTURE_POLICY", "BLOCK", "FAILED", "REALTIME_CAPTURE_REQUIRED",
                    "Realtime camera capture is required",
                    details("captureSource", source, "requireRealtimeCapture", true), now);
        }
        if (requireGps && (captureMeta.path("locationClaim").isMissingNode()
                || captureMeta.path("locationClaim").isNull())) {
            return validation(
                    revision, "CAPTURE_POLICY", "BLOCK", "FAILED", "GPS_CLAIM_REQUIRED",
                    "GPS location claim is required",
                    details("requireGps", true), now);
        }
        return validation(
                revision, "CAPTURE_POLICY", "BLOCK", "PASSED", null,
                "Capture policy satisfied",
                details("captureSource", source), now);
    }

    private EvidenceValidationView configuredCheck(
            EvidenceRevisionView revision,
            JsonNode check,
            boolean duplicateDigestExists,
            Instant now
    ) {
        String checkType = requiredText(check, "checkType");
        String severity = requiredText(check, "severity");
        if ("DUPLICATE".equals(checkType) || "HISTORICAL_IMAGE".equals(checkType)) {
            boolean passed = !duplicateDigestExists;
            return validation(
                    revision, checkType, severity,
                    passed ? "PASSED" : "FAILED",
                    passed ? null : "CONTENT_DIGEST_DUPLICATE",
                    passed ? "No duplicate content digest found"
                            : "Matching content digest already exists",
                    details("contentDigest", revision.contentDigest(),
                            "duplicateExists", duplicateDigestExists),
                    now);
        }
        if (SUPPORTED_CONFIGURED.contains(checkType)) {
            throw new IllegalStateException("supported check missing branch: " + checkType);
        }
        if ("BLOCK".equals(severity)) {
            return validation(
                    revision, checkType, severity, "FAILED", "UNSUPPORTED_CHECK_TYPE",
                    "Configured BLOCK check is not executable in M39",
                    details("checkType", checkType), now);
        }
        return validation(
                revision, checkType, severity, "SKIPPED", "DEFERRED_CHECK",
                "Configured WARN check is deferred until a later milestone",
                details("checkType", checkType), now);
    }

    private EvidenceValidationView validation(
            EvidenceRevisionView revision,
            String checkType,
            String severity,
            String result,
            String reasonCode,
            String message,
            String detailsJson,
            Instant now
    ) {
        return new EvidenceValidationView(
                UUID.randomUUID(), revision.evidenceRevisionId(), checkType, severity, result,
                reasonCode, message, detailsJson, VALIDATOR_NAME, VALIDATOR_VERSION, now);
    }

    private static boolean mimeMatches(String mediaType, String mimeType) {
        if (mediaType == null || mimeType == null || mimeType.isBlank()) {
            return false;
        }
        String mime = mimeType.toLowerCase(Locale.ROOT);
        return switch (mediaType) {
            case "PHOTO", "SIGNATURE" -> mime.startsWith("image/");
            case "VIDEO" -> mime.startsWith("video/");
            case "DOCUMENT", "GENERATED_REPORT" -> mime.startsWith("application/")
                    || mime.startsWith("text/")
                    || mime.startsWith("image/");
            default -> false;
        };
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Evidence JSON cannot be decoded", exception);
        }
    }

    private String details(Object... pairs) {
        ObjectNode node = objectMapper.createObjectNode();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object key = pairs[i];
            Object value = pairs[i + 1];
            if (value instanceof Boolean booleanValue) {
                node.put(String.valueOf(key), booleanValue);
            } else if (value instanceof Long longValue) {
                node.put(String.valueOf(key), longValue);
            } else if (value instanceof Integer intValue) {
                node.put(String.valueOf(key), intValue);
            } else {
                node.put(String.valueOf(key), String.valueOf(value));
            }
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JacksonException exception) {
            throw new IllegalStateException("validation details cannot be serialized", exception);
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? "" : value.asText();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Evidence check " + field + " must not be blank");
        }
        return value.asText();
    }
}
