package com.serviceos.configuration.api;

/**
 * 履约配置结构化校验问题。禁止只返回“配置校验失败”。
 */
public record ProjectFulfillmentValidationIssue(
        String severity,
        String errorCode,
        String profileId,
        String stageCode,
        String assetType,
        String assetRef,
        String fieldPath,
        String userMessage,
        String technicalMessage,
        String suggestion,
        String phaseId,
        String nodeId,
        String transitionId,
        String configSection
) {
    public ProjectFulfillmentValidationIssue {
        severity = requireText(severity, "severity", 16);
        errorCode = requireText(errorCode, "errorCode", 96);
        userMessage = requireText(userMessage, "userMessage", 500);
        technicalMessage = technicalMessage == null ? "" : technicalMessage.trim();
        suggestion = suggestion == null ? "" : suggestion.trim();
    }

    /** 兼容既有阶段/资产校验调用方。 */
    public ProjectFulfillmentValidationIssue(
            String severity,
            String errorCode,
            String profileId,
            String stageCode,
            String assetType,
            String assetRef,
            String fieldPath,
            String userMessage,
            String technicalMessage,
            String suggestion
    ) {
        this(severity, errorCode, profileId, stageCode, assetType, assetRef, fieldPath,
                userMessage, technicalMessage, suggestion, null, null, null, null);
    }

    private static String requireText(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(name + " exceeds " + max);
        }
        return trimmed;
    }
}
