package com.serviceos.readmodel.api;

import tools.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 单条个人 UI 偏好读模型。value 为受控 JSON；不授予页面或数据能力。
 */
public record UiPreferenceEntry(
        String key,
        JsonNode value,
        int schemaVersion,
        long aggregateVersion,
        Instant updatedAt
) {
}
